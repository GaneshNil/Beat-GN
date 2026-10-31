package com.marvneo.companion.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marvneo.companion.data.ble.MarvNeoBleManager
import com.marvneo.companion.domain.model.ConnectionState
import com.marvneo.companion.ui.components.LiquidGlassCard
import com.marvneo.companion.ui.components.LiquidGlassPill
import com.marvneo.companion.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun HealthMonitoringScreen(
    bleManager: MarvNeoBleManager,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val connectionState by bleManager.connectionState.collectAsState()
    val heartRate by bleManager.heartRateFlow.collectAsState()
    val spO2 by bleManager.spO2Flow.collectAsState()
    val bloodPressure by bleManager.bloodPressureFlow.collectAsState()

    val isMeasuringHR by bleManager.isMeasuringHeartRate.collectAsState()
    val isMeasuringSpO2 by bleManager.isMeasuringSpO2.collectAsState()
    val isMeasuringBP by bleManager.isMeasuringBloodPressure.collectAsState()

    val isConnected = connectionState is ConnectionState.Connected

    // Pulse animation for heart rate
    val infiniteTransition = rememberInfiniteTransition(label = "HRPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isMeasuringHR) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isMeasuringHR) 600 else 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .verticalScroll(scrollState)
    ) {
        // Header
        Text(
            text = "Health Monitoring",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
        Text(
            text = "Direct sensor controls and live biometric measurements",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Connection Warning if not connected
        if (!isConnected) {
            LiquidGlassCard(glowColor = AccentAmber, glowAlpha = 0.15f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = AccentAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Watch not connected. Please connect your Marv Neo to trigger sensor measurements.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 1. Heart Rate Monitor Card
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            glowColor = HeartRateCrimson,
            glowAlpha = if (isMeasuringHR) 0.25f else 0.10f
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
                            .scale(pulseScale)
                            .background(HeartRateCrimson.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Favorite, null, tint = HeartRateCrimson, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Heart Rate (PPG)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Continuous optical photoplethysmography", fontSize = 11.sp, color = TextSecondary)
                    }
                }

                if (isMeasuringHR) {
                    LiquidGlassPill(color = HeartRateCrimson) {
                        CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 2.dp, color = HeartRateCrimson)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Measuring...", color = HeartRateCrimson, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Reading Display
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    text = if (heartRate > 0) "$heartRate" else "--",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (heartRate > 0) HeartRateCrimson else TextTertiary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "BPM",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        coroutineScope.launch { bleManager.startHeartRateMeasurement() }
                    },
                    enabled = isConnected && !isMeasuringHR,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HeartRateCrimson)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        coroutineScope.launch { bleManager.stopHeartRateMeasurement() }
                    },
                    enabled = isConnected && isMeasuringHR,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                ) {
                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Blood Oxygen (SpO2) Card
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            glowColor = BloodOxygenBlue,
            glowAlpha = if (isMeasuringSpO2) 0.25f else 0.10f
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
                            .background(BloodOxygenBlue.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Air, null, tint = BloodOxygenBlue, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Blood Oxygen (SpO₂)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Red & Infrared optical reflectance", fontSize = 11.sp, color = TextSecondary)
                    }
                }

                if (isMeasuringSpO2) {
                    LiquidGlassPill(color = BloodOxygenBlue) {
                        CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 2.dp, color = BloodOxygenBlue)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scanning...", color = BloodOxygenBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Reading Display
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    text = if (spO2 > 0) "$spO2" else "--",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (spO2 > 0) BloodOxygenBlue else TextTertiary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "%",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        coroutineScope.launch { bleManager.startSpO2Measurement() }
                    },
                    enabled = isConnected && !isMeasuringSpO2,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BloodOxygenBlue)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        coroutineScope.launch { bleManager.stopSpO2Measurement() }
                    },
                    enabled = isConnected && isMeasuringSpO2,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                ) {
                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Blood Pressure Capability & Test Card
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            glowColor = SleepViolet,
            glowAlpha = 0.10f
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
                            .background(SleepViolet.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MonitorHeart, null, tint = SleepViolet, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Blood Pressure (BP)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Hardware capability & protocol test", fontSize = 11.sp, color = TextSecondary)
                    }
                }

                LiquidGlassPill(color = TextSecondary) {
                    Text("Hardware Check", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Honest Hardware Capability Notice
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x331E293B), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = PrimaryElectric, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Sensor Architecture Reality",
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryElectric,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "The beatXP Marv Neo is engineered with an optical PPG sensor (HX3600/HX3605 series) intended for photoplethysmographic Heart Rate and SpO₂ detection. It lacks a micro-pump inflatable cuff or calibrated tonometry transducer. While Crrepa protocol command opcode 0x69 can be transmitted to test firmware response, the app will never fabricate simulated blood pressure values.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Reading Display if returned
            val bp = bloodPressure
            if (bp != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${bp.first}/${bp.second}", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("mmHg", fontSize = 14.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
                }
            } else {
                Text(
                    text = if (isMeasuringBP) "Testing watch firmware response (Opcode 0x69)..." else "Status: Not supported by Marv Neo hardware",
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        coroutineScope.launch { bleManager.startBloodPressureMeasurement() }
                    },
                    enabled = isConnected && !isMeasuringBP,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Science, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test Opcode 0x69")
                }

                if (isMeasuringBP) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch { bleManager.stopBloodPressureMeasurement() }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
