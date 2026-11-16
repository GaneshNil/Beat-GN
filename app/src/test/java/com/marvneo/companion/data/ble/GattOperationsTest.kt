package com.marvneo.companion.data.ble

import com.marvneo.companion.data.ble.GattOperations.Kind
import com.marvneo.companion.data.ble.GattOperations.Request
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GattOperationsTest {
    private val operations = GattOperations()
    private val link = Any()
    private val read = Request(Kind.READ, "battery")
    private var current: Any? = link
    private var starts = 0
    private var abandonments = 0

    private suspend fun perform(
        owner: Any = link,
        request: Request = read,
        timeoutMs: Long = 5_000,
        accepted: Boolean = true
    ): Boolean = operations.run(
        owner, request, timeoutMs,
        isCurrent = { current === owner },
        onAbandoned = {
            current = null
            abandonments++
        },
        start = {
            starts++
            accepted
        }
    )

    @Test
    fun `callbacks must match the link operation type and attribute`() = runBlocking {
        val pending = async(start = CoroutineStart.UNDISPATCHED) { perform() }
        operations.complete(Any(), read, true) { fail("Stale link modified data") }
        operations.complete(link, Request(Kind.MTU), true) { fail("MTU modified data") }
        operations.complete(link, Request(Kind.READ, "model"), true) { fail("Wrong read modified data") }
        assertFalse(pending.isCompleted)
        operations.complete(link, read, true)
        assertTrue(pending.await())
    }

    @Test
    fun `queued operation on a closed link never starts`() = runBlocking {
        val pending = async(start = CoroutineStart.UNDISPATCHED) { perform() }
        val queued = async(start = CoroutineStart.UNDISPATCHED) { perform() }
        current = Any()
        operations.invalidate()
        assertFalse(pending.await())
        assertFalse(queued.await())
        assertEquals(1, starts)
        assertEquals(0, abandonments)
    }

    @Test
    fun `timed out operation quarantines link before releasing the queue`() = runBlocking {
        val pending = async(start = CoroutineStart.UNDISPATCHED) { perform(timeoutMs = 20) }
        val queued = async(start = CoroutineStart.UNDISPATCHED) { perform() }
        assertFalse(pending.await())
        assertFalse(queued.await())
        operations.complete(link, read, true) { fail("Timed-out callback modified data") }
        assertEquals(1, starts)
        assertEquals(1, abandonments)
    }

    @Test
    fun `cancellation after Android accepts a request abandons the link`() = runBlocking {
        val pending = async(start = CoroutineStart.UNDISPATCHED) { perform() }
        pending.cancelAndJoin()
        assertEquals(1, abandonments)
        assertFalse(perform())
    }

    @Test
    fun `rejected start leaves link available for the next operation`() = runBlocking {
        assertFalse(perform(accepted = false))
        val pending = async(start = CoroutineStart.UNDISPATCHED) { perform() }
        operations.complete(link, read, false) { fail("Failed read modified data") }
        assertFalse(pending.await())
        assertEquals(2, starts)
        assertEquals(0, abandonments)
    }

    @Test
    fun `retry budget stops after five backoffs`() {
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L),
            (0 until MAX_RECONNECT_ATTEMPTS).map(::reconnectDelayMillis))
        assertNull(reconnectDelayMillis(MAX_RECONNECT_ATTEMPTS))
    }
}
