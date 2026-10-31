package com.marvneo.companion.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.marvneo.companion.ui.components.LiquidGlassCard
import com.marvneo.companion.ui.components.LiquidGlassPill
import com.marvneo.companion.ui.components.RadarPulseIndicator
import com.marvneo.companion.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    bleManager: MarvNeoBleManager,
    onNavigateToTab: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val connectionState by bleManager.connectionState.collectAsState()
    val isScanning by bleManager.isScanning.collectAsState()
    val connectedName by bleManager.connectedDeviceName.collectAsState()
    val lastSeenTimestamp by bleManager.lastCommunicationTimestamp.collectAsState()

    val steps by bleManager.stepsFlow.collectAsState()
    val calories by bleManager.caloriesFlow.collectAsState()
    val distance by bleManager.distanceFlow.collectAsState()
    val heartRate by bleManager.heartRateFlow.collectAsState()
    val spO2 by bleManager.spO2Flow.collectAsState()
    val battery by bleManager.batteryFlow.collectAsState()

    var isFindingWatch by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    // Auto-attempt connection on initial screen display if disconnected
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
                    text = "Marv Neo • OriginOS Fluid Companion",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            if (isScanning) {
                RadarPulseIndicator(color = PrimaryElectric)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Genuine Connection Status Card
        LiquidGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToTab(1) }, // Navigate to Watch Connection tab
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

                    when (val state = connectionState) {
                        is ConnectionState.Connected -> {
                            LiquidGlassPill(color = StepsEmerald) {
                                Box(modifier = Modifier.size(8.dp).background(StepsEmerald, CircleShape))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Connected • BLE Active", color = StepsEmerald, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                                Text("Searching...", color = PrimaryElectric, fontSize = 12.sp)
                            }
                        }
                        is ConnectionState.Reconnecting -> {
                            LiquidGlassPill(color = AccentAmber) {
                                CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 2.dp, color = AccentAmber)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reconnecting (${state.attempt}/${state.maxAttempts})...", color = AccentAmber, fontSize = 12.sp)
                            }
                        }
                        is ConnectionState.ConnectionUnstable -> {
                            LiquidGlassPill(color = HeartRateCrimson) {
                                Icon(Icons.Default.Warning, null, tint = HeartRateCrimson, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Unstable: ${state.reason}", color = HeartRateCrimson, fontSize = 11.sp)
                            }
                        }
                        is ConnectionState.BluetoothDisabled -> {
                            LiquidGlassPill(color = HeartRateCrimson) {
                                Icon(Icons.Default.BluetoothDisabled, null, tint = HeartRateCrimson, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Bluetooth Disabled", color = HeartRateCrimson, fontSize = 12.sp)
                            }
                        }
                        is ConnectionState.Disconnected -> {
                            LiquidGlassPill(color = TextSecondary) {
                                Box(modifier = Modifier.size(8.dp).background(TextTertiary, CircleShape))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Disconnected", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                        is ConnectionState.ConnectionFailed -> {
                            LiquidGlassPill(color = HeartRateCrimson) {
                                Icon(Icons.Default.Error, null, tint = HeartRateCrimson, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Failed: ${state.error}", color = HeartRateCrimson, fontSize = 11.sp)
                            }
                        }
                        else -> {
                            LiquidGlassPill(color = PrimaryElectric) {
                                Text("Tap to Manage Connection", color = PrimaryElectric, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Battery Badge
                LiquidGlassPill(color = if (battery > 20) StepsEmerald else HeartRateCrimson) {
                    Icon(
                        if (battery > 20) Icons.Default.BatteryFull else Icons.Default.BatteryAlert,
                        contentDescription = "Battery",
                        tint = if (battery > 20) StepsEmerald else HeartRateCrimson,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("$battery%", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                }
            }

            if (lastSeenTimestamp > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Verified active: ${timeFormat.format(Date(lastSeenTimestamp))}",
                    color = TextTertiary,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Daily Activity Tracker Card
        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), glowColor = StepsEmerald, glowAlpha = 0.15f) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Today's Activity", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                LiquidGlassPill(color = StepsEmerald) {
                    Icon(Icons.Default.DirectionsRun, null, tint = StepsEmerald, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Goal 10,000", color = StepsEmerald, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "%,d".format(steps),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("steps", fontSize = 14.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { animatedStepProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = StepsEmerald,
                trackColor = SurfaceGlassHighlight,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocalFireDepartment, null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("$calories kcal", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, null, tint = PrimaryElectric, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        String.format(Locale.getDefault(), "%.2f km", distance.toFloat() / 1000f),
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Biometrics Cards Row (Heart Rate & SpO2)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Heart Rate Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToTab(2) } // Navigate to Health Monitoring tab
            ) {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth(), glowColor = HeartRateCrimson, glowAlpha = 0.12f) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Favorite, null, tint = HeartRateCrimson, modifier = Modifier.size(20.dp))
                        Text("PPG", fontSize = 10.sp, color = TextTertiary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Heart Rate", fontSize = 12.sp, color = TextSecondary)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (heartRate > 0) "$heartRate" else "--",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = HeartRateCrimson
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("BPM", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 2.dp))
                    }
                }
            }

            // SpO2 Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToTab(2) }
            ) {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth(), glowColor = BloodOxygenBlue, glowAlpha = 0.12f) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Air, null, tint = BloodOxygenBlue, modifier = Modifier.size(20.dp))
                        Text("Optical", fontSize = 10.sp, color = TextTertiary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Blood Oxygen", fontSize = 12.sp, color = TextSecondary)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (spO2 > 0) "$spO2%" else "--",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = BloodOxygenBlue
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Actions Card
        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), glowColor = PrimaryElectric, glowAlpha = 0.08f) {
            Text("Quick Watch Operations", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Find Watch Quick Action
                Button(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        val nextState = !isFindingWatch
                        isFindingWatch = nextState
                        coroutineScope.launch {
                            bleManager.findWatch(nextState)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFindingWatch) AccentAmber else PrimaryElectric
                    )
                ) {
                    Icon(Icons.Default.Vibration, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (isFindingWatch) "Stop Ringing" else "Find Watch",
                        color = DarkBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                // Health Measurement Shortcut
                OutlinedButton(
                    onClick = { onNavigateToTab(2) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.FavoriteBorder, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Live Measure", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Theme Store Shortcut
                OutlinedButton(
                    onClick = { onNavigateToTab(5) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Palette, null, modifier = Modifier.size(16.dp), tint = SleepViolet)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Theme Store", fontSize = 12.sp, color = TextPrimary)
                }

                // App Catalog Shortcut
                OutlinedButton(
                    onClick = { onNavigateToTab(6) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Apps, null, modifier = Modifier.size(16.dp), tint = PrimaryElectric)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("App Catalog", fontSize = 12.sp, color = TextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
