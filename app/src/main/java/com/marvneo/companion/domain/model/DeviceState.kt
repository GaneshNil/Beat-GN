package com.marvneo.companion.domain.model

/**
 * The real BLE lifecycle. Every state below is entered from an actual BluetoothGatt callback,
 * never from a locally-set boolean.
 *
 * A device is only [Ready] after services are discovered, telemetry notifications are
 * subscribed, and the watch has answered at least one application-level command.
 */
sealed interface ConnectionState {

    /** Phone Bluetooth is off or unavailable. */
    data object BluetoothOff : ConnectionState

    /** Nothing attempted yet. */
    data object Idle : ConnectionState

    data class Scanning(val startedAt: Long = System.currentTimeMillis()) : ConnectionState

    data class WatchFound(val name: String, val address: String, val rssi: Int) : ConnectionState

    data class Connecting(val name: String, val address: String) : ConnectionState

    data class DiscoveringServices(val address: String) : ConnectionState

    data class Subscribing(val address: String, val characteristic: String) : ConnectionState

    /** GATT is up; waiting for the first acknowledged application-level exchange. */
    data class VerifyingLink(val address: String, val attempt: Int = 1) : ConnectionState

    /**
     * End-to-end ready: GATT connected, services resolved, notifications subscribed, and the
     * watch answered a command during this session.
     */
    data class Ready(
        val deviceName: String,
        val deviceAddress: String,
        val protocolName: String,
        val mtu: Int,
        val lastVerifiedAt: Long = System.currentTimeMillis()
    ) : ConnectionState

    /** Connected at the GATT level but no recent successful exchange. */
    data class Unstable(
        val reason: String,
        val lastVerifiedAt: Long?,
        val connected: Boolean
    ) : ConnectionState

    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ConnectionState

    data class Failed(val reason: String, val retryable: Boolean = true) : ConnectionState

    data object Disconnected : ConnectionState
}

data class DiscoveredDevice(
    val name: String,
    val address: String,
    val rssi: Int = -100,
    val isBonded: Boolean = false,
    val likelyWatch: Boolean = false
)

data class AlarmModel(
    val id: Int, // watch slot 0..4
    val hour: Int,
    val minute: Int,
    val repeatDaysMask: Int = 0x7F, // bit0=Sunday .. bit6=Saturday (see CrrepaProtocol)
    val isEnabled: Boolean = true,
    val label: String = "Alarm"
)

/** How far a command has actually got. Used by Find Watch and alarm sync UI. */
sealed interface CommandOutcome {
    data object Idle : CommandOutcome
    data object Sending : CommandOutcome
    /** Written to the watch, waiting for its reply. */
    data class AwaitingAcknowledgement(val sentAt: Long = System.currentTimeMillis()) : CommandOutcome
    data class Acknowledged(val at: Long = System.currentTimeMillis()) : CommandOutcome
    data class Failed(val reason: String) : CommandOutcome
}

/** State of one health measurement request. */
sealed interface MeasurementState {
    data object Idle : MeasurementState
    data object Requesting : MeasurementState
    data class InProgress(val startedAt: Long) : MeasurementState
    data class Completed(val value: Int, val secondary: Int? = null, val at: Long = System.currentTimeMillis()) : MeasurementState
    data class TimedOut(val reason: String) : MeasurementState
    data class Failed(val reason: String) : MeasurementState
    data object NotSupported : MeasurementState
}

data class VoiceMessage(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val sender: String, // "user" | "assistant"
    val text: String,
    val toolExecuted: String? = null,
    val status: String = "completed",
    val sessionId: String? = null
)
