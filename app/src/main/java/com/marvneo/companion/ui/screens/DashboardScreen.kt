package com.marvneo.companion.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import com.marvneo.companion.data.ble.protocol.CrrepaProtocol
import com.marvneo.companion.domain.model.ConnectionState
import com.marvneo.companion.domain.model.DiscoveredDevice
import com.marvneo.companion.ui.components.LiquidGlassCard
import com.marvneo.companion.ui.components.LiquidGlassPill
import com.marvneo.companion.ui.components.RadarPulseIndicator
import com.marvneo.companion.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
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

    val steps by bleManager.stepsFlow.collectAsState()
    val calories by bleManager.caloriesFlow.collectAsState()
    val distance by bleManager.distanceFlow.collectAsState()
    val heartRate by bleManager.heartRateFlow.collectAsState()
    val spO2 by bleManager.spO2Flow.collectAsState()
    val battery by bleManager.batteryFlow.collectAsState()

    var showManualInput by remember { mutableStateOf(false) }
    var manualMac by remember { mutableStateOf("") }
    var isMeasuringHR by remember { mutableStateOf(false) }
    var isMeasuringSpO2 by remember { mutableStateOf(false) }

    // Auto-attempt connection on initial screen display if not connected
    LaunchedEffect(Unit) {
        if (connectionState is ConnectionState.Disconnected) {
            val autoConnected = bleManager.autoConnect()
            if (!autoConnected) {
                bleManager.startScan()
            }
        }
    }

    val animatedStepProgress by animateFloatAsState(
        targetValue = (steps.toFloat() / 10000f).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 200f),
        label = "StepGaugeAnimation"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .verticalScroll(scrollState)
    ) {
        // App Title Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "BeatXP Companion",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "OriginOS Fluid Architecture • Marv Neo Engine",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            if (isScanning) {
                RadarPulseIndicator(color = PrimaryElectric)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Liquid Glass Connection Status Card
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            glowColor = if (connectionState is ConnectionState.Connected) StepsEmerald else PrimaryElectric,
            glowAlpha = 0.12f
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = connectedName ?: "beatXP Marv Neo",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    when (connectionState) {
                        is ConnectionState.Connected -> {
                            LiquidGlassPill(color = StepsEmerald) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(StepsEmerald, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Connected • BLE Active", color = StepsEmerald, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        is ConnectionState.Connecting -> {
                            LiquidGlassPill(color = AccentAmber) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = AccentAmber
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Connecting Watch...", color = AccentAmber, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        is ConnectionState.Disconnected -> {
                            LiquidGlassPill(color = TextSecondary) {
                                Text("Not Connected", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                        is ConnectionState.Error -> {
                            LiquidGlassPill(color = HeartRateCrimson) {
                                Text((connectionState as ConnectionState.Error).message, color = HeartRateCrimson, fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (connectionState is ConnectionState.Connected) {
                    LiquidGlassPill(color = PrimaryElectric) {
                        Icon(Icons.Default.BatteryChargingFull, "Battery", tint = PrimaryElectric, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("$battery%", color = PrimaryElectric, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            if (connectionState is ConnectionState.Connected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            coroutineScope.launch {
                                bleManager.sendCommand(CrrepaProtocol.createTimeSyncPacket())
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryElectric)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sync Time")
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            bleManager.disconnect()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HeartRateCrimson.copy(alpha = 0.85f))
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Disconnect")
                    }
                }
            } else if (connectionState is ConnectionState.Connecting) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        bleManager.disconnect()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HeartRateCrimson.copy(alpha = 0.85f))
                ) {
                    Text("Cancel Connection")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            if (!bleManager.autoConnect()) {
                                bleManager.startScan()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryElectric)
                    ) {
                        Icon(Icons.Default.BluetoothSearching, contentDescription = null, modifier = Modifier.size(16.dp), tint = DarkBackground)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Auto-Connect", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            if (isScanning) bleManager.stopScan() else bleManager.startScan()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                    ) {
                        Text(if (isScanning) "Stop Scan" else "Scan Radar")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Smart Device Auto-Detection Radar (When not connected)
        if (connectionState !is ConnectionState.Connected) {
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = SleepViolet,
                glowAlpha = 0.08f
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Discovered Watches & Devices",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary
                    )
                    Text(
                        text = if (isScanning) "Searching..." else "${discoveredDevices.size} Found",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isScanning) PrimaryElectric else TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (discoveredDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Watch, "Watch", tint = TextTertiary, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Make sure your Marv Neo is turned on nearby.", color = TextSecondary, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { bleManager.startScan() }) {
                                Text("Tap to Scan Again", color = PrimaryElectric)
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        discoveredDevices.take(4).forEach { device ->
                            DeviceDiscoveryRow(device = device) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                bleManager.connect(device.address)
                            }
                        }
                    }
                }

                // Advanced manual fallback expander
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showManualInput = !showManualInput }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Advanced: Connect via MAC manually", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                    Icon(
                        if (showManualInput) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        null,
                        tint = TextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                AnimatedVisibility(visible = showManualInput) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = manualMac,
                            onValueChange = { manualMac = it },
                            placeholder = { Text("e.g. C6:A2:29:XX:XX:XX") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (manualMac.isNotBlank()) bleManager.connect(manualMac.trim())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Connect Manual Address")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // Quick Device Actions (When Connected)
        if (connectionState is ConnectionState.Connected) {
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), glowColor = AccentAmber, glowAlpha = 0.08f) {
                Text(
                    text = "Quick Watch Actions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionButton(
                        icon = Icons.Default.Vibration,
                        label = "Find Watch",
                        modifier = Modifier.weight(1f)
                    ) {
                        coroutineScope.launch {
                            bleManager.sendCommand(CrrepaProtocol.createFindWatchPacket(true))
                        }
                    }

                    QuickActionButton(
                        icon = Icons.Default.CameraAlt,
                        label = "Camera",
                        modifier = Modifier.weight(1f)
                    ) {
                        coroutineScope.launch {
                            bleManager.sendCommand(CrrepaProtocol.createCameraModePacket(true))
                        }
                    }

                    QuickActionButton(
                        icon = Icons.Default.WbSunny,
                        label = "Raise Wake",
                        modifier = Modifier.weight(1f)
                    ) {
                        coroutineScope.launch {
                            bleManager.sendCommand(CrrepaProtocol.createRaiseToWakePacket(true))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // Steps & Calories Liquid Glass Card
        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), glowColor = StepsEmerald, glowAlpha = 0.1f) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Daily Activity", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$steps",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = StepsEmerald
                    )
                    Text("Goal: 10,000 steps", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = StepsEmerald.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StepsEmerald.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LocalFireDepartment, "Calories", tint = AccentAmber, modifier = Modifier.size(20.dp))
                        Text("$calories kcal", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Animated Activity Progress Bar
            LinearProgressIndicator(
                progress = { animatedStepProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = StepsEmerald,
                trackColor = SurfaceCardElevated
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${(distance / 1000f)} km walked",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    text = "${(animatedStepProgress * 100).toInt()}% completed",
                    style = MaterialTheme.typography.bodySmall,
                    color = StepsEmerald,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Biometric Vital Sensors: Heart Rate & SpO2 Grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Heart Rate Card
            LiquidGlassCard(
                modifier = Modifier.weight(1f),
                glowColor = HeartRateCrimson,
                glowAlpha = 0.12f
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Favorite, "Heart Rate", tint = HeartRateCrimson, modifier = Modifier.size(24.dp))
                    IconButton(
                        onClick = {
                            isMeasuringHR = !isMeasuringHR
                            coroutineScope.launch {
                                bleManager.sendCommand(CrrepaProtocol.createHeartRateControlPacket(isMeasuringHR))
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            if (isMeasuringHR) Icons.Default.Stop else Icons.Default.PlayArrow,
                            "Toggle Measure",
                            tint = HeartRateCrimson
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Heart Rate", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text(
                    text = if (heartRate > 0) "$heartRate BPM" else "-- BPM",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = HeartRateCrimson
                )
                Text(
                    text = if (isMeasuringHR) "Live measuring..." else "Tap play to start",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }

            // Blood Oxygen (SpO2) Card
            LiquidGlassCard(
                modifier = Modifier.weight(1f),
                glowColor = OxygenCyan,
                glowAlpha = 0.12f
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Air, "SpO2", tint = OxygenCyan, modifier = Modifier.size(24.dp))
                    IconButton(
                        onClick = {
                            isMeasuringSpO2 = !isMeasuringSpO2
                            coroutineScope.launch {
                                bleManager.sendCommand(CrrepaProtocol.createSpO2ControlPacket(isMeasuringSpO2))
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            if (isMeasuringSpO2) Icons.Default.Stop else Icons.Default.PlayArrow,
                            "Toggle SpO2",
                            tint = OxygenCyan
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Blood Oxygen", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text(
                    text = if (spO2 > 0) "$spO2%" else "--%",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = OxygenCyan
                )
                Text(
                    text = if (isMeasuringSpO2) "Measuring SpO2..." else "Tap play to start",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun DeviceDiscoveryRow(
    device: DiscoveredDevice,
    onConnect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConnect() },
        shape = RoundedCornerShape(14.dp),
        color = Color(0x18FFFFFF),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Watch,
                    contentDescription = null,
                    tint = if (device.isBonded) StepsEmerald else PrimaryElectric,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(device.name, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(device.address, color = TextTertiary, fontSize = 11.sp)
                        if (device.isBonded) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("• Paired", color = StepsEmerald, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Button(
                onClick = onConnect,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryElectric),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("Connect", color = DarkBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = Color(0x18FFFFFF),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, tint = PrimaryElectric, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}
