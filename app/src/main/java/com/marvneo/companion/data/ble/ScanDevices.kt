package com.marvneo.companion.data.ble

import com.marvneo.companion.domain.model.DiscoveredDevice

/** A scan response can omit fields that were present in an earlier advertisement. */
internal fun List<DiscoveredDevice>.withScanResult(result: DiscoveredDevice): List<DiscoveredDevice> {
    val previous = firstOrNull { it.address == result.address }
    val merged = result.copy(
        name = result.name.ifBlank { previous?.name.orEmpty() },
        advertisesCompanionService = result.advertisesCompanionService || previous?.advertisesCompanionService == true,
        likelyWatch = result.likelyWatch || previous?.likelyWatch == true,
        connectable = result.connectable || previous?.connectable == true
    )
    return (filterNot { it.address == result.address } + merged)
        .sortedWith(compareByDescending<DiscoveredDevice> { it.connectable }
            .thenByDescending { it.advertisesCompanionService }
            .thenByDescending { it.likelyWatch }
            .thenByDescending { it.isBonded }
            .thenByDescending { it.rssi })
}

private val bluetoothAddress = Regex("(?i)([0-9a-f]{2}[:-]){5}[0-9a-f]{2}")

internal fun String.maskBluetoothAddresses(): String = bluetoothAddress.replace(this) {
    "…" + it.value.takeLast(5)
}
