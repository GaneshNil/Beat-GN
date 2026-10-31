package com.marvneo.companion.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marvneo.companion.data.ble.MarvNeoBleManager
import com.marvneo.companion.data.ble.protocol.CrrepaProtocol
import com.marvneo.companion.domain.model.ConnectionState
import com.marvneo.companion.ui.components.MetricCard
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
    val steps by bleManager.stepsFlow.collectAsState()
    val calories by bleManager.caloriesFlow.collectAsState()
    val heartRate by bleManager.heartRateFlow.collectAsState()
    val spO2 by bleManager.spO2Flow.collectAsState()
    val battery by bleManager.batteryFlow.collectAsState()

    var targetMac by remember { mutableStateOf("") }
    var isMeasuringHR by remember { mutableStateOf(false) }
    var isMeasuringSpO2 by remember { mutableStateOf(false) }

    // Smooth LTPO fluid step progress animation (target: 10,000 steps daily)
    val animatedStepProgress by animateFloatAsState(
        targetValue = (steps.toFloat() / 10000f).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 200f),
        label = "StepGaugeAnimation"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Status & Connection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("beatXP Marv Neo", style = MaterialTheme.typography.titleLarge)
                        val statusText = when (connectionState) {
                            is ConnectionState.Connected -> "Connected • BLE Active"
                            is ConnectionState.Connecting -> "Connecting..."
                            is ConnectionState.Disconnected -> "Disconnected"
                            is ConnectionState.Error -> "Connection Error"
                        }
                        Text(
                            statusText,
                            color = if (connectionState is ConnectionState.Connected) StepsEmerald else AccentAmber
                        )
                    }

                    if (connectionState is ConnectionState.Connected) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = PrimaryElectric.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.BatteryChargingFull, "Battery", tint = PrimaryElectric, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("$battery%", color = PrimaryElectric, fontSize = 14.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (connectionState is ConnectionState.Disconnected) {
                    OutlinedTextField(
                        value = targetMac,
                        onValueChange = { targetMac = it },
                        label = { Text("Watch MAC Address (e.g. C6:A2:29:XX:XX:XX)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            if (targetMac.isNotBlank()) bleManager.connect(targetMac.trim())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryElectric)
                    ) {
                        Text("Connect Watch", color = DarkBackground)
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                coroutineScope.launch {
                                    bleManager.sendCommand(CrrepaProtocol.createTimeSyncPacket())
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Sync Time")
                        }
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                bleManager.disconnect()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = HeartRateCrimson)
                        ) {
                            Text("Disconnect")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Steps & Calories Metric Card with animated bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DirectionsRun, "Steps", tint = StepsEmerald, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Daily Steps", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                    }
                    TextButton(onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        coroutineScope.launch { bleManager.sendCommand(CrrepaProtocol.createStepsQueryPacket()) }
                    }) {
                        Text("Refresh", color = StepsEmerald)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = "$steps", fontSize = 36.sp, style = MaterialTheme.typography.headlineLarge)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("/ 10,000 steps ($calories kcal)", color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { animatedStepProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = StepsEmerald,
                    trackColor = SurfaceCardElevated
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Heart Rate Metric Card
        MetricCard(
            title = "Heart Rate",
            value = if (heartRate > 0) "$heartRate" else "--",
            unit = "BPM",
            icon = Icons.Default.Favorite,
            accentColor = HeartRateCrimson,
            onActionClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                isMeasuringHR = !isMeasuringHR
                coroutineScope.launch {
                    bleManager.sendCommand(CrrepaProtocol.createHeartRateControlPacket(isMeasuringHR))
                }
            },
            actionText = if (isMeasuringHR) "Stop Measuring" else "Measure Now"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Blood Oxygen (SpO2) Card
        MetricCard(
            title = "Blood Oxygen (SpO2)",
            value = if (spO2 > 0) "$spO2" else "--",
            unit = "%",
            icon = Icons.Default.WaterDrop,
            accentColor = OxygenCyan,
            onActionClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                isMeasuringSpO2 = !isMeasuringSpO2
                coroutineScope.launch {
                    bleManager.sendCommand(CrrepaProtocol.createSpO2ControlPacket(isMeasuringSpO2))
                }
            },
            actionText = if (isMeasuringSpO2) "Stop Measuring" else "Check SpO2"
        )
    }
}
