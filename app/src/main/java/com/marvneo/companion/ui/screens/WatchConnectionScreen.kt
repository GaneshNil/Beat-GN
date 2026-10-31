package com.marvneo.companion.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marvneo.companion.data.ble.MarvNeoBleManager
import com.marvneo.companion.domain.model.ConnectionState
import com.marvneo.companion.domain.model.DiscoveredDevice
import com.marvneo.companion.ui.components.LiquidGlassCard
import com.marvneo.companion.ui.components.LiquidGlassPill
import com.marvneo.companion.ui.components.RadarPulseIndicator
import com.marvneo.companion.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WatchConnectionScreen(
    bleManager: MarvNeoBleManager,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val connectionState by bleManager.connectionState.collectAsState()
    val discoveredDevices by bleManager.discoveredDevices.collectAsState()
    val isScanning by bleManager.isScanning.collectAsState()
    val connectedName by bleManager.connectedDeviceName.collectAsState()
    val connectedAddress by bleManager.connectedDeviceAddress.collectAsState()
    val lastSeenTimestamp by bleManager.lastCommunicationTimestamp.collectAsState()

    var showManualInput by remember { mutableStateOf(false) }
    var manualMac by remember { mutableStateOf("") }

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .verticalScroll(scrollState)
    ) {
        // Screen Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Watch Connection",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "Genuine GATT connection lifecycle & device discovery",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            if (isScanning) {
                RadarPulseIndicator(color = PrimaryElectric)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Connection Lifecycle Status Card
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            glowColor = when (connectionState) {
                is ConnectionState.Connected -> StepsEmerald
                is ConnectionState.Connecting, is ConnectionState.Synchronizing, is ConnectionState.Reconnecting -> AccentAmber
                is ConnectionState.ConnectionUnstable -> HeartRateCrimson
                else -> PrimaryElectric
            },
            glowAlpha = 0.15f
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = connectedName ?: "beatXP Marv Neo",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )

                    if (!connectedAddress.isNullOrBlank()) {
                        Text(
                            text = "MAC: $connectedAddress",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Dynamic State Pills
                    when (val state = connectionState) {
                        is ConnectionState.Connected -> {
                            LiquidGlassPill(color = StepsEmerald) {
                                Box(modifier = Modifier.size(8.dp).background(StepsEmerald, CircleShape))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Connected • Genuine Link Active", color = StepsEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        is ConnectionState.Connecting -> {
                            LiquidGlassPill(color = AccentAmber) {
                                CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 2.dp, color = AccentAmber)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(state.message, color = AccentAmber, fontSize = 12.sp)
                            }
                        }
                        is ConnectionState.Synchronizing -> {
                            LiquidGlassPill(color = AccentAmber) {
                                CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 2.dp, color = AccentAmber)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(state.step, color = AccentAmber, fontSize = 12.sp)
                            }
                        }
                        is ConnectionState.SearchingForWatch -> {
                            LiquidGlassPill(color = PrimaryElectric) {
                                CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 2.dp, color = PrimaryElectric)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Searching for Marv Neo...", color = PrimaryElectric, fontSize = 12.sp)
                            }
                        }
                        is ConnectionState.WatchFound -> {
                            LiquidGlassPill(color = StepsEmerald) {
                                Icon(Icons.Default.CheckCircle, null, tint = StepsEmerald, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Watch Found: ${state.name} (${state.rssi} dBm)", color = StepsEmerald, fontSize = 12.sp)
                            }
                        }
                        is ConnectionState.ConnectionUnstable -> {
                            LiquidGlassPill(color = HeartRateCrimson) {
                                Icon(Icons.Default.Warning, null, tint = HeartRateCrimson, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Connection Unstable: ${state.reason}", color = HeartRateCrimson, fontSize = 11.sp)
                            }
                        }
                        is ConnectionState.Reconnecting -> {
                            LiquidGlassPill(color = AccentAmber) {
                                CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 2.dp, color = AccentAmber)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reconnecting (Attempt ${state.attempt}/${state.maxAttempts})...", color = AccentAmber, fontSize = 12.sp)
                            }
                        }
                        is ConnectionState.BluetoothDisabled -> {
                            LiquidGlassPill(color = HeartRateCrimson) {
                                Icon(Icons.Default.BluetoothDisabled, null, tint = HeartRateCrimson, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Bluetooth is Disabled on Phone", color = HeartRateCrimson, fontSize = 12.sp)
                            }
                        }
                        is ConnectionState.Disconnected -> {
                            LiquidGlassPill(color = TextSecondary) {
                                Box(modifier = Modifier.size(8.dp).background(TextTertiary, CircleShape))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Disconnected (${state.reason})", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                        is ConnectionState.ConnectionFailed -> {
                            LiquidGlassPill(color = HeartRateCrimson) {
                                Icon(Icons.Default.Error, null, tint = HeartRateCrimson, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Failed: ${state.error}", color = HeartRateCrimson, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Connection Action Button
                if (connectionState is ConnectionState.Connected) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            bleManager.disconnect()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HeartRateCrimson)
                    ) {
                        Text("Disconnect", fontWeight = FontWeight.Bold)
                    }
                } else if (connectionState is ConnectionState.Connecting || connectionState is ConnectionState.Synchronizing) {
                    OutlinedButton(
                        onClick = { bleManager.disconnect() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                } else {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            val autoConnected = bleManager.autoConnect()
                            if (!autoConnected) {
                                bleManager.startScan()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryElectric)
                    ) {
                        Text("Connect", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Last verified communication timestamp
            if (lastSeenTimestamp > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = TextTertiary, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Last communication exchange: ${timeFormat.format(Date(lastSeenTimestamp))}",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Scanner Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Nearby Smartwatches",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = {
                        if (isScanning) bleManager.stopScan() else bleManager.startScan()
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        if (isScanning) Icons.Default.Stop else Icons.Default.Refresh,
                        null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isScanning) "Stop" else "Scan", fontSize = 12.sp)
                }

                IconButton(onClick = { showManualInput = !showManualInput }) {
                    Icon(Icons.Default.Edit, "Manual MAC", tint = TextSecondary)
                }
            }
        }

        // Manual MAC entry fallback
        AnimatedVisibility(visible = showManualInput) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(Color(0x331E293B), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text("Manual Bluetooth MAC Input", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = manualMac,
                        onValueChange = { manualMac = it },
                        placeholder = { Text("E.g. 78:02:B7:XX:XX:XX") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (manualMac.isNotBlank()) {
                                bleManager.connect(manualMac.trim())
                                showManualInput = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryElectric)
                    ) {
                        Text("Connect to Address", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Discovered Smartwatches List
        if (discoveredDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isScanning) "Searching for beatXP Marv Neo..." else "No smartwatch detected. Tap 'Scan' to discover.",
                    color = TextTertiary,
                    fontSize = 13.sp
                )
            }
        } else {
            discoveredDevices.forEach { dev ->
                val isCurrentTarget = dev.address.equals(connectedAddress, ignoreCase = true)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(
                            if (isCurrentTarget) Color(0x3300E5FF) else Color(0x221E293B),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            bleManager.connect(dev.address)
                        }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(PrimaryElectric.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Watch, null, tint = PrimaryElectric, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(dev.name, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                                Text(dev.address, color = TextSecondary, fontSize = 11.sp)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${dev.rssi} dBm", color = TextTertiary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            if (dev.isBonded) {
                                LiquidGlassPill(color = StepsEmerald) {
                                    Text("Bonded", color = StepsEmerald, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

