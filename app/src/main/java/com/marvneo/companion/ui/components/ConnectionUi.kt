package com.marvneo.companion.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.marvneo.companion.domain.model.ConnectionState
import com.marvneo.companion.ui.theme.StatusCritical
import com.marvneo.companion.ui.theme.StatusPositive
import com.marvneo.companion.ui.theme.StatusWarning

/** Short state word shown next to the connection dot. */
fun connectionLabel(state: ConnectionState): String = when (state) {
    is ConnectionState.Ready -> "Ready"
    is ConnectionState.VerifyingLink -> "Verifying"
    is ConnectionState.Subscribing -> "Subscribing"
    is ConnectionState.DiscoveringServices -> "Discovering"
    is ConnectionState.Connecting -> "Connecting"
    is ConnectionState.Scanning -> "Scanning"
    is ConnectionState.WatchFound -> "Found"
    is ConnectionState.Reconnecting -> "Reconnecting"
    is ConnectionState.Unstable -> "Unstable"
    is ConnectionState.Failed -> "Failed"
    is ConnectionState.BluetoothOff -> "Bluetooth off"
    is ConnectionState.Disconnected -> "Disconnected"
    ConnectionState.Idle -> "Not connected"
}

/** One sentence a user can act on. Never optimistic. */
fun connectionDetail(state: ConnectionState): String = when (state) {
    is ConnectionState.Ready ->
        "Link verified by a real exchange with ${state.deviceName}. MTU ${state.mtu}."
    is ConnectionState.VerifyingLink ->
        "GATT is up. Waiting for the watch to answer a read request."
    is ConnectionState.Subscribing ->
        "Subscribing to watch telemetry (${state.characteristic.take(8)}…)."
    is ConnectionState.DiscoveringServices ->
        "Reading the watch's GATT profile."
    is ConnectionState.Connecting -> "Opening a GATT link to ${state.name}."
    is ConnectionState.Scanning -> "Looking for nearby watches. This stops when the watch answers."
    is ConnectionState.WatchFound -> "${state.name} answered at ${state.rssi} dBm."
    is ConnectionState.Reconnecting ->
        "Reconnect attempt ${state.attempt} of ${state.maxAttempts}."
    is ConnectionState.Unstable -> state.reason
    is ConnectionState.Failed -> state.reason
    is ConnectionState.BluetoothOff -> "Turn Bluetooth on to reach the watch."
    is ConnectionState.Disconnected -> "No active link."
    ConnectionState.Idle -> "Connect to start syncing."
}

@Composable
fun connectionColor(state: ConnectionState): Color = when (state) {
    is ConnectionState.Ready -> StatusPositive
    is ConnectionState.Unstable -> StatusWarning
    is ConnectionState.Failed -> StatusCritical
    is ConnectionState.BluetoothOff -> StatusCritical
    is ConnectionState.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant
    ConnectionState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.primary
}
