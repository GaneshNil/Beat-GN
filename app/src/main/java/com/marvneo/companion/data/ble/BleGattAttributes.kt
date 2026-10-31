package com.marvneo.companion.data.ble

import java.util.UUID

object BleGattAttributes {
    // 1. Nordic UART Service
    val NUS_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    val NUS_TX_CHAR_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    val NUS_RX_CHAR_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

    // 2. Crrepa FFF0 Service (Very common in Marv Neo / MOY / DaFit hardware)
    val CRP_FFF0_SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val CRP_FFF1_CHAR_UUID: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
    val CRP_FFF2_CHAR_UUID: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")

    // 3. DaFit FEE7 Service
    val CRP_FEE7_SERVICE_UUID: UUID = UUID.fromString("0000fee7-0000-1000-8000-00805f9b34fb")
    val CRP_FEB1_CHAR_UUID: UUID = UUID.fromString("0000feb1-0000-1000-8000-00805f9b34fb")
    val CRP_FEB2_CHAR_UUID: UUID = UUID.fromString("0000feb2-0000-1000-8000-00805f9b34fb")

    // 4. Crrepa DFU Service
    val DFU_SERVICE_UUID: UUID = UUID.fromString("0000ffd0-0000-1000-8000-00805f9b34fb")
    val DFU_TX_CHAR_UUID: UUID = UUID.fromString("0000ffd1-0000-1000-8000-00805f9b34fb")
    val DFU_RX_CHAR_UUID: UUID = UUID.fromString("0000ffd2-0000-1000-8000-00805f9b34fb")

    // 5. HunterSun AE00 Service
    val HS_AE00_SERVICE_UUID: UUID = UUID.fromString("0000ae00-0000-1000-8000-00805f9b34fb")
    val HS_AE01_CHAR_UUID: UUID = UUID.fromString("0000ae01-0000-1000-8000-00805f9b34fb")
    val HS_AE02_CHAR_UUID: UUID = UUID.fromString("0000ae02-0000-1000-8000-00805f9b34fb")

    // Standard Client Characteristic Configuration Descriptor (CCCD)
    val CCCD_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
