package com.marvneo.companion.data.ble

import com.marvneo.companion.domain.model.MeasurementState
import kotlinx.coroutines.flow.MutableStateFlow

internal suspend fun MutableStateFlow<MeasurementState>.beginMeasurement(
    sendStart: suspend () -> Boolean,
    now: () -> Long = System::currentTimeMillis
): Boolean {
    if (value.active) return false
    value = MeasurementState.Requesting
    val sent = sendStart()
    val next = if (sent) {
        MeasurementState.InProgress(now())
    } else {
        MeasurementState.Failed("Could not send measurement request")
    }
    // A notification or a link loss can arrive before the GATT write callback.
    return compareAndSet(MeasurementState.Requesting, next) && sent
}
