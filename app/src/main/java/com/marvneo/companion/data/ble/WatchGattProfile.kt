package com.marvneo.companion.data.ble

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService

internal sealed interface WatchGattProfile {
    data class Channels(
        val activity: BluetoothGattCharacteristic,
        val commands: BluetoothGattCharacteristic,
        val packets: BluetoothGattCharacteristic
    ) : WatchGattProfile

    enum class Missing(val reason: String) : WatchGattProfile {
        SERVICE("The selected device did not report the companion service (FEEA)."),
        CHARACTERISTICS("The companion service is missing a required channel (FEE1/FEE2/FEE3)."),
        WRITE("The companion command channel (FEE2) does not support writes."),
        SUBSCRIPTION("The packet channel (FEE3) lacks notification or indication support with a CCCD.")
    }
}

/** Each candidate must supply all channels in the same discovered service instance. */
internal fun resolveWatchProfile(services: List<BluetoothGattService>): WatchGattProfile {
    val matching = services.filter { it.uuid == BleGattAttributes.CRP_SERVICE_FEEA }
    if (matching.isEmpty()) return WatchGattProfile.Missing.SERVICE
    val candidates = matching.mapNotNull { service ->
        WatchGattProfile.Channels(
            service.getCharacteristic(BleGattAttributes.CRP_CHAR_FEE1) ?: return@mapNotNull null,
            service.getCharacteristic(BleGattAttributes.CRP_CHAR_FEE2) ?: return@mapNotNull null,
            service.getCharacteristic(BleGattAttributes.CRP_CHAR_FEE3) ?: return@mapNotNull null
        )
    }
    if (candidates.isEmpty()) return WatchGattProfile.Missing.CHARACTERISTICS

    val write = BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
    val writable = candidates.filter { it.commands.properties and write != 0 }
    if (writable.isEmpty()) return WatchGattProfile.Missing.WRITE
    val notify = BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE
    return writable.firstOrNull { channel ->
        channel.packets.properties and notify != 0 && channel.packets.getDescriptor(BleGattAttributes.CCCD) != null
    } ?: WatchGattProfile.Missing.SUBSCRIPTION
}
