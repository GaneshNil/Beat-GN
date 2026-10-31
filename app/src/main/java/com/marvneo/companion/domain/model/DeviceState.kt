package com.marvneo.companion.domain.model

sealed class ConnectionState {
    object BluetoothDisabled : ConnectionState()
    object SearchingForWatch : ConnectionState()
    data class WatchFound(val name: String, val address: String, val rssi: Int) : ConnectionState()
    data class Connecting(val message: String = "Establishing GATT link...") : ConnectionState()
    data class Connected(
        val deviceName: String,
        val deviceAddress: String,
        val lastSeenMs: Long = System.currentTimeMillis()
    ) : ConnectionState()
    data class Synchronizing(val step: String = "Synchronizing watch telemetry...") : ConnectionState()
    data class ConnectionUnstable(val reason: String) : ConnectionState()
    data class Disconnected(val reason: String = "Watch is disconnected") : ConnectionState()
    data class Reconnecting(val attempt: Int, val maxAttempts: Int = 5) : ConnectionState()
    data class ConnectionFailed(val error: String) : ConnectionState()

    // Backward-compatible error constructor
    companion object {
        fun Error(message: String): ConnectionState = ConnectionFailed(message)
    }
}

data class DiscoveredDevice(
    val name: String,
    val address: String,
    val rssi: Int = -100,
    val isBonded: Boolean = false
)

data class AlarmModel(
    val id: Int, // 0..2
    val hour: Int,
    val minute: Int,
    val repeatDaysMask: Int = 0x7F, // Bitmask: bit0=Mon .. bit6=Sun
    val isEnabled: Boolean = true,
    val label: String = "Alarm"
)

data class HealthSummary(
    val periodTitle: String,
    val avgHeartRate: Double?,
    val minHeartRate: Int?,
    val maxHeartRate: Int?,
    val hrReadingCount: Int,
    val avgSpO2: Double?,
    val minSpO2: Int?,
    val maxSpO2: Int?,
    val spO2ReadingCount: Int,
    val avgSystolic: Double? = null,
    val avgDiastolic: Double? = null,
    val bpReadingCount: Int = 0,
    val isBpSupported: Boolean = false
)

data class WatchTheme(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val resolution: String = "240x284",
    val primaryColorHex: Long,
    val secondaryColorHex: Long,
    val style: String = "Digital",
    val hasDigitalClock: Boolean = true,
    val hasStepGauge: Boolean = true,
    val hasHeartRate: Boolean = true,
    val hasBatteryGauge: Boolean = true
)

data class WatchApp(
    val id: String,
    val name: String,
    val category: String,
    val version: String,
    val sizeKb: Int,
    val description: String,
    val isInstalled: Boolean = false
)

data class VoiceMessage(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val sender: String, // "user" or "gemini"
    val text: String,
    val toolExecuted: String? = null,
    val status: String = "completed"
)
