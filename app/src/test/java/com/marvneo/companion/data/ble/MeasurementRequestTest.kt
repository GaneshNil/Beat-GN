package com.marvneo.companion.data.ble

import com.marvneo.companion.domain.model.MeasurementState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementRequestTest {
    private val state = MutableStateFlow<MeasurementState>(MeasurementState.Idle)

    @Test
    fun `notification before write callback preserves the completed result`() = runBlocking {
        val reading = MeasurementState.Completed(96)
        assertFalse(state.beginMeasurement({
            state.value = reading
            true
        }))
        assertEquals(reading, state.value)
    }

    @Test
    fun `second tap during request does not send a second start`() = runBlocking {
        assertTrue(state.beginMeasurement({
            assertFalse(state.beginMeasurement({ error("Duplicate start") }))
            true
        }, now = { 123L }))
        assertEquals(MeasurementState.InProgress(123L), state.value)
    }

    @Test
    fun `link loss remains visible when the write fails afterward`() = runBlocking {
        val failure = MeasurementState.Failed("Disconnected")
        assertFalse(state.beginMeasurement({
            state.value = failure
            false
        }))
        assertEquals(failure, state.value)
    }

    @Test
    fun `late start callback cannot complete a newer pending request`() = runBlocking {
        val firstCallback = CompletableDeferred<Boolean>()
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            state.beginMeasurement({ firstCallback.await() })
        }
        state.value = MeasurementState.Completed(96)

        val secondCallback = CompletableDeferred<Boolean>()
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            state.beginMeasurement({ secondCallback.await() }, now = { 123L })
        }
        val secondRequest = state.value
        firstCallback.complete(true)
        assertFalse(first.await())
        assertEquals(secondRequest, state.value)

        secondCallback.complete(true)
        assertTrue(second.await())
        assertEquals(MeasurementState.InProgress(123L), state.value)
    }
}
