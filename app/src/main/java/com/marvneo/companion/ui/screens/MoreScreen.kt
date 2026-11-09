package com.marvneo.companion.ui.screens

import android.os.Build
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.marvneo.companion.data.ble.MarvNeoBleManager
import com.marvneo.companion.ui.components.AppCard
import com.marvneo.companion.ui.components.EmptyState
import com.marvneo.companion.ui.components.KeyValueRow
import com.marvneo.companion.ui.components.SectionHeader
import com.marvneo.companion.ui.components.connectionLabel
import com.marvneo.companion.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun MoreScreen(
    bleManager: MarvNeoBleManager,
    route: String?,
    onNavigate: (String?) -> Unit,
    onBack: () -> Unit
) {
    when (route) {
        null -> MoreMenu(onNavigate)
        "device" -> DeviceInfoPage(bleManager, onBack)
        "diagnostics" -> DiagnosticsPage(bleManager, onBack)
        "software" -> SoftwarePage(onBack)
        "about" -> AboutPage(onBack)
        else -> MoreMenu(onNavigate)
    }
}

@Composable
private fun MoreMenu(onNavigate: (String?) -> Unit) {
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
            SectionHeader(title = "More", subtitle = "Device details, logs and disclosures")
        }
        item {
            AppCard {
                MenuRow(
                    icon = Icons.Outlined.Watch,
                    title = "Device information",
                    subtitle = "Address, firmware, MTU, supported services"
                ) { onNavigate("device") }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Spacing.sm),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
                MenuRow(
                    icon = Icons.Outlined.BugReport,
                    title = "Diagnostics",
                    subtitle = "Every Bluetooth exchange this session"
                ) { onNavigate("diagnostics") }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Spacing.sm),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
                MenuRow(
                    icon = Icons.Outlined.Security,
                    title = "Privacy and software",
                    subtitle = "What is stored, what leaves the phone"
                ) { onNavigate("software") }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Spacing.sm),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
                MenuRow(
                    icon = Icons.Outlined.Info,
                    title = "About",
                    subtitle = "Version and limitations"
                ) { onNavigate("about") }
            }
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
        }
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun DeviceInfoPage(bleManager: MarvNeoBleManager, onBack: () -> Unit) {
    val connection by bleManager.connectionState.collectAsState()
    val address by bleManager.connectedDeviceAddress.collectAsState()
    val name by bleManager.connectedDeviceName.collectAsState()
    val firmware by bleManager.firmwareVersion.collectAsState()
    val model by bleManager.modelNumber.collectAsState()
    val manufacturer by bleManager.manufacturerName.collectAsState()
    val mtu by bleManager.negotiatedMtu.collectAsState()
    val lastSeen by bleManager.lastCommunicationTimestamp.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Spacing.lg, Spacing.lg, Spacing.lg, Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item { PageHeader("Device information", onBack) }
        item {
            AppCard {
                KeyValueRow("State", connectionLabel(connection))
                KeyValueRow("Name", name ?: "—")
                KeyValueRow("Address", address ?: "—")
                KeyValueRow("Manufacturer", manufacturer ?: "not reported")
                KeyValueRow("Model", model ?: "not reported")
                KeyValueRow("Firmware", firmware ?: "not reported")
                KeyValueRow("MTU", if (mtu > 0) "$mtu bytes" else "not negotiated")
                KeyValueRow(
                    "Last traffic",
                    if (lastSeen > 0) {
                        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                            .format(java.util.Date(lastSeen))
                    } else {
                        "none this session"
                    }
                )
            }
        }
        item {
            AppCard {
                Text("Expected GATT profile", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "Service FEEA with characteristics FEE1 (steps), FEE2 (commands), " +
                        "FEE3 (packets). Plus 180F/2A19 battery and 180D/2A37 heart rate. " +
                        "A watch without FEE1/FEE2/FEE3 is refused rather than half-supported.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsPage(bleManager: MarvNeoBleManager, onBack: () -> Unit) {
    val entries by bleManager.diagnostics.collectAsState()
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Spacing.lg, Spacing.lg, Spacing.lg, Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        item { PageHeader("Diagnostics", onBack) }
        item {
            Text(
                "Raw Bluetooth activity for this session. Useful when reporting a bug: these are " +
                    "the exact bytes that were sent and received.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            OutlinedButton(onClick = { bleManager.clearDiagnostics() }) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Clear")
            }
        }
        if (entries.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.BugReport,
                    title = "No activity yet",
                    message = "Entries appear when the app talks to the watch."
                )
            }
        } else {
            items(entries) { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SoftwarePage(onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Spacing.lg, Spacing.lg, Spacing.lg, Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item { PageHeader("Privacy and software", onBack) }
        item {
            AppCard {
                Text("Stored on this phone", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "Heart rate, blood oxygen, blood pressure (only if the watch reports it), " +
                        "step snapshots, alarm configurations and delivered transcripts. All of it " +
                        "lives in a local SQLite database.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            AppCard {
                Text("Leaves the phone", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "Nothing. There is no analytics, no crash reporting, no login and no cloud " +
                        "service. The app requests no internet permission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            AppCard {
                Text("Deliberately removed", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "A Gemini API-key integration that never had a transcript source, a " +
                        "notification-listener service that scraped other apps, a microphone " +
                        "trigger activity, and watch-face/app \"installers\" that reported success " +
                        "without writing anything to the watch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AboutPage(onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Spacing.lg, Spacing.lg, Spacing.lg, Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item { PageHeader("About", onBack) }
        item {
            AppCard {
                KeyValueRow("App", "Marv Neo Companion")
                KeyValueRow("Package", "com.marvneo.companion")
                KeyValueRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                KeyValueRow("Device", "${Build.MANUFACTURER} ${Build.MODEL}")
            }
        }
        item {
            AppCard {
                Text("Known limitations", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "• Alarms are written to the watch but cannot be read back or acknowledged by " +
                        "this firmware, so they stay marked pending.\n" +
                        "• Find-watch has no protocol-level confirmation.\n" +
                        "• Blood pressure is shown only when the watch sends a reading.\n" +
                        "• Watch faces, app installation and firmware updates are not implemented.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
