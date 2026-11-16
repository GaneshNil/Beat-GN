package com.marvneo.companion.data.ble

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Access from the BLE owner's looper. The callback identifies both the operation and its link. */
internal class GattOperations {
    enum class Kind { MTU, DISCOVERY, DESCRIPTOR_WRITE, READ, WRITE }
    data class Request(val kind: Kind, val attribute: Any? = null)

    private class Pending(val link: Any, val request: Request) {
        val result = CompletableDeferred<Boolean>()
    }

    private val mutex = Mutex()
    private var pending: Pending? = null

    suspend fun run(
        link: Any,
        request: Request,
        timeoutMs: Long,
        isCurrent: () -> Boolean,
        onAbandoned: () -> Unit,
        start: () -> Boolean
    ): Boolean = mutex.withLock {
        if (!isCurrent()) return false
        val operation = Pending(link, request)
        pending = operation
        var started = false
        try {
            started = start()
            started && withTimeoutOrNull(timeoutMs) { operation.result.await() } == true
        } finally {
            if (pending === operation) pending = null
            if (started && !operation.result.isCompleted && isCurrent()) onAbandoned()
        }
    }

    fun complete(link: Any, request: Request, success: Boolean, onSuccess: () -> Unit = {}) {
        val operation = pending ?: return
        if (operation.link !== link || operation.request != request || operation.result.isCompleted) return
        if (success) onSuccess()
        operation.result.complete(success)
    }

    fun invalidate() {
        pending?.result?.complete(false)
        pending = null
    }
}
