package com.marvneo.companion.domain.model

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class DiscoveredDevice(
    val name: String,
    val address: String,
    val rssi: Int = -100,
    val isBonded: Boolean = false
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
