package com.marvneo.companion.data.ble

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.UUID

/** Synthetic service inventories exercise Android's attribute objects without a radio. */
class WatchGattProfileTest {
    private fun channel(id: UUID, properties: Int) = BluetoothGattCharacteristic(id, properties, 0)

    private fun service(vararg channels: BluetoothGattCharacteristic, id: UUID = BleGattAttributes.CRP_SERVICE_FEEA) =
        BluetoothGattService(id, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
            channels.forEach { addCharacteristic(it) }
        }

    private val steps get() = channel(BleGattAttributes.CRP_CHAR_FEE1, PROPERTY_NOTIFY)
    private val commands get() = channel(BleGattAttributes.CRP_CHAR_FEE2, PROPERTY_WRITE)
    private fun packets(properties: Int = PROPERTY_NOTIFY, withCccd: Boolean = true) =
        channel(BleGattAttributes.CRP_CHAR_FEE3, properties).apply {
            if (withCccd) addDescriptor(BluetoothGattDescriptor(BleGattAttributes.CCCD, BluetoothGattDescriptor.PERMISSION_WRITE))
        }

    @Test fun rejectsUnknownServiceEvenIfTheCharacteristicIdsMatch() {
        val other = service(steps, commands, packets(), id = BleGattAttributes.DEVICE_INFO_SERVICE)
        assertEquals(WatchGattProfile.Missing.SERVICE, resolveWatchProfile(listOf(other)))
    }

    @Test fun doesNotCombineChannelsFromSeparateServices() {
        assertEquals(WatchGattProfile.Missing.CHARACTERISTICS,
            resolveWatchProfile(listOf(service(steps, commands), service(packets()))))
    }

    @Test fun rejectsUnwritableCommandOrUnsubscribablePacketChannel() {
        val readOnly = channel(BleGattAttributes.CRP_CHAR_FEE2, PROPERTY_READ)
        assertEquals(WatchGattProfile.Missing.WRITE, resolveWatchProfile(listOf(service(steps, readOnly, packets()))))
        assertEquals(WatchGattProfile.Missing.SUBSCRIPTION,
            resolveWatchProfile(listOf(service(steps, commands, packets(withCccd = false)))))
        assertEquals(WatchGattProfile.Missing.SUBSCRIPTION,
            resolveWatchProfile(listOf(service(steps, commands, packets(PROPERTY_READ)))))
    }

    @Test fun selectsUsableInstanceAfterIncompleteAndUnsubscribableInstances() {
        val write = channel(BleGattAttributes.CRP_CHAR_FEE2, PROPERTY_WRITE_NO_RESPONSE)
        val notify = packets(PROPERTY_INDICATE)
        val result = resolveWatchProfile(listOf(
            service(steps), service(steps, commands, packets(withCccd = false)), service(steps, write, notify)
        )) as WatchGattProfile.Channels
        assertSame(write, result.commands)
        assertSame(notify, result.packets)
    }
}
