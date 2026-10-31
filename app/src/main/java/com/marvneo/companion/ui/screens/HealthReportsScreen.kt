package com.marvneo.companion.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.marvneo.companion.data.db.AppDatabase
import com.marvneo.companion.data.db.entity.BloodOxygenRecord
import com.marvneo.companion.data.db.entity.HeartRateRecord
import com.marvneo.companion.ui.components.LiquidGlassCard
import com.marvneo.companion.ui.components.LiquidGlassPill
import com.marvneo.companion.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HealthReportsScreen(
    database: AppDatabase,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var selectedReportPeriod by remember { mutableIntStateOf(0) } // 0: Daily, 1: 7-Day Rolling, 2: Monthly

    val now = remember { System.currentTimeMillis() }
    val oneDayAgo = remember { now - (24L * 60 * 60 * 1000) }
    val sevenDaysAgo = remember { now - (7L * 24 * 60 * 60 * 1000) }
    val thirtyDaysAgo = remember { now - (30L * 24 * 60 * 60 * 1000) }

    val filterSince = when (selectedReportPeriod) {
        0 -> oneDayAgo
        1 -> sevenDaysAgo
        else -> thirtyDaysAgo
    }

    val periodLabel = when (selectedReportPeriod) {
        0 -> "Today (Last 24 Hours)"
        1 -> "Rolling 7-Day Window"
        else -> "Monthly Window (Last 30 Days)"
    }

    // Averages and stats from database Flow
    val avgHeartRate by database.healthDao().getAvgHeartRateSince(filterSince).collectAsState(initial = null)
    val minHeartRate by database.healthDao().getMinHeartRateSince(filterSince).collectAsState(initial = null)
    val maxHeartRate by database.healthDao().getMaxHeartRateSince(filterSince).collectAsState(initial = null)
    val hrCount by database.healthDao().getHeartRateCountSince(filterSince).collectAsState(initial = 0)

    val avgSpO2 by database.healthDao().getAvgBloodOxygenSince(filterSince).collectAsState(initial = null)
    val minSpO2 by database.healthDao().getMinBloodOxygenSince(filterSince).collectAsState(initial = null)
    val maxSpO2 by database.healthDao().getMaxBloodOxygenSince(filterSince).collectAsState(initial = null)
    val spO2Count by database.healthDao().getBloodOxygenCountSince(filterSince).collectAsState(initial = 0)

    val recentHeartRates by database.healthDao().getHeartRatesBetween(filterSince, now).collectAsState(initial = emptyList())
    val recentBloodOxygen by database.healthDao().getBloodOxygenBetween(filterSince, now).collectAsState(initial = emptyList())

    val timeFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = "Biometric Reports",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
        Text(
            text = "Mathematical analysis from verified smartwatch sensor packets",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Period Selection Tabs
        TabRow(
            selectedTabIndex = selectedReportPeriod,
            containerColor = Color(0x331E293B),
            contentColor = PrimaryElectric,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedReportPeriod == 0,
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    selectedReportPeriod = 0
                },
                text = { Text("Daily", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            )
            Tab(
                selected = selectedReportPeriod == 1,
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    selectedReportPeriod = 1
                },
                text = { Text("7-Day Rolling", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            )
            Tab(
                selected = selectedReportPeriod == 2,
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    selectedReportPeriod = 2
                },
                text = { Text("Monthly", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Summary Banner
            item {
                LiquidGlassCard(glowColor = PrimaryElectric, glowAlpha = 0.10f) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(periodLabel, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        LiquidGlassPill(color = PrimaryElectric) {
                            Icon(Icons.Default.Analytics, null, tint = PrimaryElectric, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${hrCount + spO2Count} Samples", color = PrimaryElectric, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Heart Rate Report Card
            item {
                LiquidGlassCard(glowColor = HeartRateCrimson, glowAlpha = 0.12f) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Favorite, null, tint = HeartRateCrimson, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Heart Rate Analytics", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        }
                        Text("$hrCount records", color = TextSecondary, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (avgHeartRate != null && hrCount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Average", color = TextSecondary, fontSize = 11.sp)
                                Text(
                                    text = String.format(Locale.getDefault(), "%.1f", avgHeartRate),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HeartRateCrimson
                                )
                                Text("BPM", fontSize = 10.sp, color = TextTertiary)
                            }

                            Column {
                                Text("Minimum", color = TextSecondary, fontSize = 11.sp)
                                Text(
                                    text = "${minHeartRate ?: "--"}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text("BPM", fontSize = 10.sp, color = TextTertiary)
                            }

                            Column {
                                Text("Maximum", color = TextSecondary, fontSize = 11.sp)
                                Text(
                                    text = "${maxHeartRate ?: "--"}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text("BPM", fontSize = 10.sp, color = TextTertiary)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No heart rate readings in this time window", color = TextTertiary, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Blood Oxygen Report Card
            item {
                LiquidGlassCard(glowColor = BloodOxygenBlue, glowAlpha = 0.12f) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Air, null, tint = BloodOxygenBlue, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Blood Oxygen (SpO₂) Analytics", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        }
                        Text("$spO2Count records", color = TextSecondary, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (avgSpO2 != null && spO2Count > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Average", color = TextSecondary, fontSize = 11.sp)
                                Text(
                                    text = String.format(Locale.getDefault(), "%.1f%%", avgSpO2),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BloodOxygenBlue
                                )
                            }

                            Column {
                                Text("Minimum", color = TextSecondary, fontSize = 11.sp)
                                Text(
                                    text = "${minSpO2 ?: "--"}%",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }

                            Column {
                                Text("Maximum", color = TextSecondary, fontSize = 11.sp)
                                Text(
                                    text = "${maxSpO2 ?: "--"}%",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No SpO₂ readings in this time window", color = TextTertiary, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Blood Pressure Status Card
            item {
                LiquidGlassCard(glowColor = SleepViolet, glowAlpha = 0.08f) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MonitorHeart, null, tint = SleepViolet, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Blood Pressure Analytics", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Hardware Notice: The Marv Neo smartwatch lacks an inflatable cuff or calibrated tonometry transducer. In accordance with zero-fabrication standards, no simulated blood pressure data is calculated.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            // Timestamped History Log
            item {
                Text(
                    "Timestamped Sensor Log",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (recentHeartRates.isEmpty() && recentBloodOxygen.isEmpty()) {
                item {
                    Text(
                        "No readings recorded yet. Tap 'Start' on the Health Monitoring screen to record data.",
                        color = TextTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(recentHeartRates.take(20)) { hr ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x221E293B), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Favorite, null, tint = HeartRateCrimson, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Heart Rate: ${hr.bpm} BPM", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            Text(timeFormat.format(Date(hr.timestamp)), color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }

                items(recentBloodOxygen.take(20)) { ox ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x221E293B), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Air, null, tint = BloodOxygenBlue, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Blood Oxygen: ${ox.percentage}%", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            Text(timeFormat.format(Date(ox.timestamp)), color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
