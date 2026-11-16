package com.marvneo.companion.data.ble

internal const val MAX_RECONNECT_ATTEMPTS = 5

internal fun reconnectDelayMillis(previousAttempts: Int): Long? {
    if (previousAttempts !in 0 until MAX_RECONNECT_ATTEMPTS) return null
    return minOf(30_000L, 2_000L shl previousAttempts)
}
