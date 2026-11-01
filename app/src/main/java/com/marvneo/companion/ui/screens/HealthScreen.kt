package com.marvneo.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.marvneo.companion.data.ble.MarvNeoBleManager
import com.marvneo.companion.data.repository.HealthRepository
import com.marvneo.companion.domain.model.ConnectionState
import com.marvneo.companion.domain.model.MeasurementState
import com.marvneo.companion.domain.report.HealthReport
import com.marvneo.companion.domain.report.MetricStats
import com.marvneo.companion.domain.report.ReportPeriod
import com.marvneo.companion.ui.components.AppCard
import com.marvneo.companion.ui.components.EmptyState
import com.marvneo.companion.ui.components.InlineNotice
import com.marvneo.companion.ui.components.KeyValueRow
import com.marvneo.companion.ui.components.MetricTile
import com.marvneo.companion.ui.components.NoticeTone
import com.marvneo.companion.ui.components.SectionHeader
import com.marvneo.companion.ui.components.SparkBars
import com.marvneo.companion.ui.theme.HeartCoral
import com.marvneo.companion.ui.theme.OxygenTeal
import com.marvneo.companion.ui.theme.PressureSteel
import com.marvneo.companion.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HealthScreen(
    bleManager: MarvNeoBleManager,
    healthRepository: HealthRepository,
    onOpenWatch: () -> Unit
) {
    var period by remember { mutableStateOf(ReportPeriod.TODAY) }
    val report by remember(period) { healthRepository.report(period) }.collectAsState(initial = null)
    val connection by bleManager.connectionState.collectAsState()
    val hrState by bleManager.heartRateMeasurement.collectAsState()
    val spo2State by bleManager.spo2Measurement.collectAsState()
    val bpState by bleManager.bloodPressureMeasurement.collectAsState()
    val bpEverReported by bleManager.bloodPressureEverReported.collectAsState()

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
                title = "Health",
                subtitle = "Only what the watch actually measured"
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PeriodChip("Today", period == ReportPeriod.TODAY) { period = ReportPeriod.TODAY }
                PeriodChip("7 days", period == ReportPeriod.LAST_7_DAYS) { period = ReportPeriod.LAST_7_DAYS }
                PeriodChip("Month", period == ReportPeriod.THIS_MONTH) { period = ReportPeriod.THIS_MONTH }
            }
        }

        item {
            Text(
                text = report?.window?.description ?: "Loading window…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            AppCard {
                Text(
                    text = "Measure now",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "Requests are sent to the watch. A value appears only when the watch " +
                        "answers; a timeout is reported as a timeout.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(
                        enabled = ready,
                        onClick = { scope.launch { bleManager.startHeartRateMeasurement() } }
                    ) {
                        Icon(Icons.Outlined.FavoriteBorder, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Pulse")
                    }
                    OutlinedButton(
                        enabled = ready,
                        onClick = { scope.launch { bleManager.startSpO2Measurement() } }
                    ) {
                        Icon(Icons.Outlined.Bloodtype, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Oxygen")
                    }
                    OutlinedButton(
                        enabled = ready,
                        onClick = { scope.launch { bleManager.probeBloodPressure() } }
                    ) {
                        Icon(Icons.Outlined.MonitorHeart, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("BP probe")
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                MeasurementLine("Heart rate", hrState)
                MeasurementLine("Blood oxygen", spo2State)
                MeasurementLine("Blood pressure", bpState)

                if (!ready) {
                    Spacer(Modifier.height(Spacing.sm))
                    InlineNotice(
                        title = "Watch not ready",
                        message = "Measurements need a verified link. Open the Watch tab to connect.",
                        tone = NoticeTone.WARNING
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                MetricTile(
                    title = "Heart rate",
                    value = report?.heartRate?.formatAverage() ?: "—",
                    unit = if (report?.heartRate?.hasData == true) "bpm" else null,
                    caption = report?.heartRate?.summary() ?: "No readings in this period",
                    accent = HeartCoral,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "Blood oxygen",
                    value = report?.spo2?.formatAverage() ?: "—",
                    unit = if (report?.spo2?.hasData == true) "%" else null,
                    caption = report?.spo2?.summary() ?: "No readings in this period",
                    accent = OxygenTeal,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            BloodPressureCard(report = report, everReported = bpEverReported)
        }

        val daily = report?.daily.orEmpty()
        if (daily.isNotEmpty()) {
            item {
                AppCard {
                    Text(
                        text = "Daily average",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = "Empty columns are days with no reading — they are not zeros.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.lg))
                    SparkBars(
                        values = daily.map { it.heartRateAverage },
                        labels = daily.map { it.label },
                        accent = HeartCoral
                    )
                }
            }
        }

        val records = report?.heartRateRecords.orEmpty()
        item {
            SectionHeader(
                title = "Readings",
                subtitle = if (records.isEmpty()) "Nothing recorded in this period" else
                    "${records.size} heart-rate readings"
            )
        }

        if (records.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Speed,
                    title = "No data yet",
                    message = "Connect the watch and take a measurement, or wait for the watch to " +
                        "sync its history. Empty really means empty.",
                    action = {
                        OutlinedButton(onClick = onOpenWatch) { Text("Open Watch") }
                    }
                )
            }
        } else {
            items(records.take(60)) { record ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(record.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(96.dp)
                    )
                    Text(
                        text = "${record.bpm} bpm",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(Spacing.sm))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "This app is not a medical device and does not diagnose anything. Values come " +
                    "from an optical wrist sensor and can be inaccurate during movement.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PeriodChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun MeasurementLine(label: String, state: MeasurementState) {
    val text = when (state) {
        MeasurementState.Idle -> "Idle"
        MeasurementState.Requesting -> "Requesting…"
        is MeasurementState.InProgress -> "Measuring…"
        is MeasurementState.Completed ->
            if (state.secondary != null) "${state.value}/${state.secondary}"
            else "${state.value}"
        is MeasurementState.TimedOut -> state.reason
        is MeasurementState.Failed -> state.reason
        MeasurementState.NotSupported -> "This watch did not report a value"
    }
    KeyValueRow(label = label, value = text)
}

@Composable
private fun BloodPressureCard(report: HealthReport?, everReported: Boolean) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.MonitorHeart,
                contentDescription = null,
                tint = PressureSteel
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = "Blood pressure",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        if (report?.bloodPressureSupported == true) {
            val stats = report.bloodPressure
            Text(
                text = "${stats.count} readings in this period",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.sm))
            KeyValueRow("Average systolic", stats.average?.let { "${it.toInt()} mmHg" } ?: "—")
            KeyValueRow("Range", if (stats.minimum != null) "${stats.minimum}–${stats.maximum} mmHg" else "—")
        } else {
            Text(
                text = if (everReported) {
                    "No blood-pressure reading in this period. The watch has reported BP before, " +
                        "but not inside this window."
                } else {
                    "No blood-pressure data has ever been received from this watch. The protocol " +
                        "supports a BP request, but this unit has not answered it. It is shown as " +
                        "unsupported rather than invented."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun MetricStats.formatAverage(): String =
    average?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "—"

private fun MetricStats.summary(): String =
    if (hasData) "$count readings · min $minimum · max $maximum" else "No readings in this period"

private fun formatTime(millis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(millis))
