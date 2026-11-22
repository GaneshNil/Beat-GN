package com.marvneo.companion.data.ble

import com.marvneo.companion.domain.model.DiscoveredDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanDevicesTest {
    @Test fun aLaterScanResponseCanOmitTheNameServiceAndConnectableFlag() {
        val watch = DiscoveredDevice("Marv", "a", rssi = -80, likelyWatch = true, advertisesCompanionService = true)
        val response = DiscoveredDevice("", "a", rssi = -40, connectable = false)
        val result = listOf(watch).withScanResult(response).single()
        assertEquals(watch.copy(rssi = -40), result)
    }

    @Test fun connectableCompanionRanksAheadOfNamesAndBroadcastOnlyDevices() {
        val calling = DiscoveredDevice("Watch", "a", rssi = -20, likelyWatch = true)
        val companion = DiscoveredDevice("Watch", "b", rssi = -85, likelyWatch = true, advertisesCompanionService = true)
        val beacon = companion.copy(address = "c", rssi = -10, connectable = false)
        val found = listOf(beacon, calling).withScanResult(companion)
        assertEquals(listOf("b", "a", "c"), found.map { it.address })
        assertFalse(found.last().connectable)
    }

    @Test fun discoveryKeepsUnnamedDevicesAndUpdatesConnectability() {
        val beacon = DiscoveredDevice("", "a", connectable = false)
        val found = listOf(beacon).withScanResult(beacon.copy(connectable = true))
        assertTrue(found.single().connectable)
        assertEquals("", found.single().name)
    }

    @Test fun addressesInPeripheralTextAreMaskedWhileServiceUuidsRemainReadable() {
        val service = BleGattAttributes.CRP_SERVICE_FEEA.toString()
        val input = "device 0A:1B:2C:3D:4E:5F or aa-bb-cc-dd-ee-ff service $service"
        assertEquals("device …4E:5F or …ee-ff service $service", input.maskBluetoothAddresses())
    }
}
