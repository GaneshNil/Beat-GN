package com.marvneo.companion.data.ble

import java.util.UUID

object BleGattAttributes {
    // Nordic UART Service (Primary BLE communication channel for Crrepa / DaFit / Marv Neo)
    val NUS_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    val NUS_TX_CHAR_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e") // Host -> Watch (Write)
    val NUS_RX_CHAR_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e") // Watch -> Host (Notify)

    // Standard Client Characteristic Configuration Descriptor (CCCD)
    val CCCD_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // DFU / OTA Upgrade Service
    val DFU_SERVICE_UUID: UUID = UUID.fromString("0000ffd0-0000-1000-8000-00805f9b34fb")
    val DFU_CONTROL_CHAR_UUID: UUID = UUID.fromString("0000ffd1-0000-1000-8000-00805f9b34fb")
    val DFU_PACKET_CHAR_UUID: UUID = UUID.fromString("0000ffd2-0000-1000-8000-00805f9b34fb")

    // Alternative Crrepa Service UUID (used by some firmware versions)
    val CRP_SERVICE_UUID: UUID = UUID.fromString("0000fee7-0000-1000-8000-00805f9b34fb")
}

