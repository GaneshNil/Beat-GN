package com.marvneo.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.marvneo.companion.data.ble.MarvNeoBleManager
import com.marvneo.companion.data.repository.HealthRepository
import com.marvneo.companion.domain.model.ConnectionState
import com.marvneo.companion.domain.model.MeasurementState
import com.marvneo.companion.domain.report.ReportPeriod
import com.marvneo.companion.ui.components.AppCard
import com.marvneo.companion.ui.components.Badge
import com.marvneo.companion.ui.components.InlineNotice
import com.marvneo.companion.ui.components.MetricTile
import com.marvneo.companion.ui.components.NoticeTone
import com.marvneo.companion.ui.components.SectionHeader
import com.marvneo.companion.ui.components.connectionColor
import com.marvneo.companion.ui.components.connectionDetail
import com.marvneo.companion.ui.components.connectionLabel
import com.marvneo.companion.ui.theme.HeartCoral
import com.marvneo.companion.ui.theme.MotionGreen
import com.marvneo.companion.ui.theme.OxygenTeal
import com.marvneo.companion.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun HomeScreen(
    bleManager: MarvNeoBleManager,
    healthRepository: HealthRepository,
    onOpenWatch: () -> Unit,
    onOpenHealth: () -> Unit
) {
    val connection by bleManager.connectionState.collectAsState()
    val today by remember { healthRepository.report(ReportPeriod.TODAY) }.collectAsState(
        initial = null
    )
    val steps by bleManager.stepsFlow.collectAsState()
    val distance by bleManager.distanceFlow.collectAsState()
    val battery by bleManager.batteryFlow.collectAsState()
    val batteryKnown by bleManager.batteryKnown.collectAsState()
    val heartRate by bleManager.heartRateFlow.collectAsState()
    val hrMeasurement by bleManager.heartRateMeasurement.collectAsState()
    val spo2Measurement by bleManager.spo2Measurement.collectAsState()

    val scope = rememberCoroutineScope()
    val ready = connection is ConnectionState.Ready

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.lg,
            bottom = Spacing.xxl
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            SectionHeader(
                title = "Marv Neo",
                subtitle = "Your watch, reported honestly",
                trailing = {
                    Badge(
                        text = connectionLabel(connection),
                        color = connectionColor(connection)
                    )
                }
            )
        }

        item {
            AppCard {
                Text(
                    text = connectionDetail(connection),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(onClick = onOpenWatch) {
                        Icon(Icons.Outlined.Watch, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Watch")
                    }
                    if (ready) {
                        OutlinedButton(onClick = {
                            scope.launch { bleManager.startHeartRateMeasurement() }
                        }) {
                            Icon(Icons.Outlined.FavoriteBorder, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Measure pulse")
                        }
                    }
                }
            }
        }

        if (hrMeasurement is MeasurementState.InProgress || spo2Measurement is MeasurementState.InProgress) {
            item {
                InlineNotice(
                    title = "Measurement running",
                    message = "Keep the watch on your wrist and still. The reading appears as soon " +
                        "as the watch sends it — nothing is filled in for you.",
                    tone = NoticeTone.INFO
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                MetricTile(
                    title = "Pulse today",
                    value = today?.heartRate?.average?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "—",
                    unit = if (today?.heartRate?.hasData == true) "bpm avg" else null,
                    caption = today?.heartRate?.let {
                        if (it.hasData) "${it.count} readings · ${it.minimum}–${it.maximum}" else "No reading yet"
                    },
                    accent = HeartCoral,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "Oxygen today",
                    value = today?.spo2?.average?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "—",
                    unit = if (today?.spo2?.hasData == true) "% avg" else null,
                    caption = today?.spo2?.let {
                        if (it.hasData) "${it.count} readings" else "No reading yet"
                    },
                    accent = OxygenTeal,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                MetricTile(
                    title = "Steps",
                    value = if (steps > 0) steps.toString() else "—",
                    unit = if (steps > 0) "steps" else null,
                    caption = if (steps > 0) {
                        String.format(Locale.getDefault(), "%.2f km", distance / 1000.0)
                    } else {
                        "Watch has not reported"
                    },
                    accent = MotionGreen,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "Battery",
                    value = if (batteryKnown) battery.toString() else "—",
                    unit = if (batteryKnown) "%" else null,
                    caption = if (batteryKnown) "Read from watch" else "Not read yet",
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            AppCard(onClick = onOpenHealth) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reports",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Today, last 7 days and this month — with empty days shown as empty.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (!ready) {
            item {
                InlineNotice(
                    title = "No live link",
                    message = "Metrics stay empty until the watch has been connected and has actually " +
                        "sent data. Nothing here is simulated.",
                    tone = NoticeTone.WARNING
                )
            }
        }

        if (heartRate > 0) {
            item {
                InlineNotice(
                    title = "Live pulse",
                    message = "$heartRate bpm just now, streamed from the watch's heart-rate " +
                        "characteristic.",
                    tone = NoticeTone.POSITIVE
                )
            }
        }
    }
}
