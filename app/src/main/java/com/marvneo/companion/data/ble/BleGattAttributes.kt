package com.marvneo.companion.data.ble

import java.util.UUID

/**
 * GATT identifiers used by Crrepa-class firmware (the family the reference beatXP app targets).
 *
 * Evidence: `com.crrepa.p.a` in the reference APK maps an incoming `BluetoothGattService` list to
 * fields by UUID substring. It looks for service `feea` with characteristics `fee1`, `fee2`,
 * `fee3`, `fee5`, `fee6`, `fee7`, `fee8`; service `180f` / `2a19` (battery); service `180d` /
 * `2a37` (heart rate); and service `180a` / `2a24`, `2a28`, `2a29` (device information).
 * `com.crrepa.g.e#onServicesDiscovered` treats a device as usable only when `fee1`, `fee2` and
 * `fee3` all exist, then subscribes to `fee3`, `2a37`, `fee7`, `fee8` and `fee1`.
 *
 * The previously shipped UUIDs (FFF0/FFF1/FFF2 and FEE7/FEB1/FEB2) do not appear in the vendor
 * SDK as the Marv Neo data channel; they were assumptions. They are gone.
 */
object BleGattAttributes {

    private fun uuid16(short: String): UUID =
        UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")

    // ---- Primary Crrepa data service ----
    val CRP_SERVICE_FEEA: UUID = uuid16("feea")
    /** Notifies live step data (`CRPStepInfo`, 3-byte little-endian triplets). */
    val CRP_CHAR_FEE1: UUID = uuid16("fee1")
    /** Default command write channel. */
    val CRP_CHAR_FEE2: UUID = uuid16("fee2")
    /** Main notify channel carrying framed packets (magic FE EA). Requirement for a valid device. */
    val CRP_CHAR_FEE3: UUID = uuid16("fee3")
    /** Write channel used only by command byte 0x01. */
    val CRP_CHAR_FEE5: UUID = uuid16("fee5")
    /** Write channel used only by command byte 0x02. */
    val CRP_CHAR_FEE6: UUID = uuid16("fee6")
    val CRP_CHAR_FEE7: UUID = uuid16("fee7")
    val CRP_CHAR_FEE8: UUID = uuid16("fee8")

    // ---- Standard services ----
    val BATTERY_SERVICE: UUID = uuid16("180f")
    val BATTERY_LEVEL_CHAR: UUID = uuid16("2a19")

    val HEART_RATE_SERVICE: UUID = uuid16("180d")
    val HEART_RATE_MEASUREMENT_CHAR: UUID = uuid16("2a37")

    val DEVICE_INFO_SERVICE: UUID = uuid16("180a")
    val MODEL_NUMBER_CHAR: UUID = uuid16("2a24")
    val FIRMWARE_REVISION_CHAR: UUID = uuid16("2a28")
    val MANUFACTURER_NAME_CHAR: UUID = uuid16("2a29")

    /** Client Characteristic Configuration Descriptor (shared by every notifying characteristic). */
    val CCCD: UUID = uuid16("2902")
}
