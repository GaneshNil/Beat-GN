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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.marvneo.companion.data.ble.MarvNeoBleManager
import com.marvneo.companion.data.ble.protocol.CrrepaProtocol
import com.marvneo.companion.data.db.AppDatabase
import com.marvneo.companion.data.db.entity.AlarmRecord
import com.marvneo.companion.data.db.entity.AlarmSyncState
import com.marvneo.companion.domain.model.CommandOutcome
import com.marvneo.companion.domain.model.ConnectionState
import com.marvneo.companion.ui.components.AppCard
import com.marvneo.companion.ui.components.Badge
import com.marvneo.companion.ui.components.EmptyState
import com.marvneo.companion.ui.components.InlineNotice
import com.marvneo.companion.ui.components.KeyValueRow
import com.marvneo.companion.ui.components.NoticeTone
import com.marvneo.companion.ui.components.SectionHeader
import com.marvneo.companion.ui.components.connectionColor
import com.marvneo.companion.ui.components.connectionDetail
import com.marvneo.companion.ui.components.connectionLabel
import com.marvneo.companion.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun WatchScreen(
    bleManager: MarvNeoBleManager,
    database: AppDatabase,
    onOpenBatterySettings: () -> Unit
) {
    val connection by bleManager.connectionState.collectAsState()
    val devices by bleManager.discoveredDevices.collectAsState()
    val scanning by bleManager.isScanning.collectAsState()
    val findOutcome by bleManager.findWatchOutcome.collectAsState()
    val alarms by database.healthDao().getAllAlarms().collectAsState(initial = emptyList())
    val battery by bleManager.batteryFlow.collectAsState()
    val batteryKnown by bleManager.batteryKnown.collectAsState()
    val mtu by bleManager.negotiatedMtu.collectAsState()
    val firmware by bleManager.firmwareVersion.collectAsState()

    val scope = rememberCoroutineScope()
    val ready = connection is ConnectionState.Ready
    var editorSlot by remember { mutableStateOf<AlarmRecord?>(null) }

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
                title = "Watch",
                subtitle = "Connection, alerts and alarms",
                trailing = { Badge(connectionLabel(connection), connectionColor(connection)) }
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
                    when {
                        ready -> OutlinedButton(onClick = { bleManager.disconnect() }) {
                            Icon(Icons.Outlined.Close, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Disconnect")
                        }

                        scanning -> OutlinedButton(onClick = { bleManager.stopScan() }) {
                            Text("Stop scan")
                        }

                        else -> Button(onClick = { bleManager.startScan() }) {
                            Icon(Icons.Outlined.Search, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Scan")
                        }
                    }
                    OutlinedButton(
                        enabled = bleManager.lastKnownAddress() != null,
                        onClick = { bleManager.autoConnect() }
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Reconnect")
                    }
                }
            }
        }

        if (devices.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Nearby",
                    subtitle = "Showing every named device the phone can see"
                )
            }
            items(devices) { device ->
                AppCard(onClick = { bleManager.connect(device.address) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Watch, contentDescription = null)
                        Spacer(Modifier.width(Spacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = device.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${device.address} · ${device.rssi} dBm",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (device.likelyWatch) {
                            Badge("likely watch", MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        } else if (scanning) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Search,
                    title = "Scanning…",
                    message = "Devices appear as soon as the phone hears them. Nothing is listed " +
                        "unless it was actually seen."
                )
            }
        }

        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = "Find watch",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "Makes the watch vibrate. The vendor protocol defines no reply for this " +
                        "command, so the app reports it as sent — never as confirmed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.md))
                Button(
                    enabled = ready && findOutcome !is CommandOutcome.Sending,
                    onClick = { scope.launch { bleManager.findWatch() } }
                ) {
                    Text("Vibrate watch")
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = describeOutcome(findOutcome),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionHeader(
                title = "Alarms",
                subtitle = "Five slots, written to the watch; confirmation is not available",
                trailing = {
                    IconButton(onClick = { editorSlot = emptyAlarm(alarms.size) }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add alarm")
                    }
                }
            )
        }

        item {
            InlineNotice(
                title = "About alarm confirmation",
                message = "Writes are sent to the watch and the Bluetooth write is confirmed. The " +
                    "watch's firmware does not acknowledge an alarm write, so every alarm stays " +
                    "marked \"pending confirmation\" until a firmware-level read-back exists.",
                tone = NoticeTone.INFO
            )
        }

        if (alarms.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Alarm,
                    title = "No alarms configured",
                    message = "Create one and it will be written to the watch when it is connected."
                )
            }
        } else {
            items(alarms) { alarm ->
                AlarmCard(
                    alarm = alarm,
                    onEdit = { editorSlot = alarm },
                    onDelete = {
                        scope.launch { database.healthDao().deleteAlarm(alarm.id) }
                    },
                    onSend = {
                        scope.launch {
                            database.healthDao().updateAlarmSyncState(
                                alarm.id,
                                AlarmSyncState.PENDING,
                                System.currentTimeMillis()
                            )
                            bleManager.writeAlarm(alarm)
                        }
                    },
                    canSend = ready
                )
            }
        }

        item {
            AppCard {
                Text(
                    text = "Device",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Spacing.sm))
                KeyValueRow("State", connectionLabel(connection))
                KeyValueRow(
                    "Battery",
                    if (batteryKnown) "$battery%" else "not read yet"
                )
                KeyValueRow("MTU", if (mtu > 0) "$mtu" else "not negotiated")
                KeyValueRow("Firmware", firmware ?: "not reported")
                KeyValueRow("Address", bleManager.connectedDeviceAddress.collectAsState().value ?: "—")
                Spacer(Modifier.height(Spacing.md))
                OutlinedButton(onClick = { scope.launch { bleManager.readBattery() } }) {
                    Icon(Icons.Outlined.BatteryFull, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Read battery")
                }
            }
        }

        item {
            OutlinedButton(onClick = onOpenBatterySettings, modifier = Modifier.fillMaxWidth()) {
                Text("Allow background connection (battery settings)")
            }
        }
    }

    editorSlot?.let { alarm ->
        AlarmEditorDialog(
            initial = alarm,
            onDismiss = { editorSlot = null },
            onSave = { updated ->
                editorSlot = null
                scope.launch {
                    val record = updated.copy(
                        syncState = AlarmSyncState.PENDING,
                        updatedAt = System.currentTimeMillis()
                    )
                    database.healthDao().upsertAlarm(record)
                    if (bleManager.connectionState.value is ConnectionState.Ready) {
                        bleManager.writeAlarm(record)
                    }
                }
            }
        )
    }
}

@Composable
private fun AlarmCard(
    alarm: AlarmRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSend: () -> Unit,
    canSend: Boolean
) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = CrrepaProtocol.formatAlarmLabel(alarm.hour, alarm.minute),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = repeatLabel(alarm.repeatDays) + " · " + if (alarm.isEnabled) "enabled" else "disabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Badge(
                text = when (alarm.syncState) {
                    AlarmSyncState.SYNCED -> "synced"
                    AlarmSyncState.FAILED -> "failed"
                    else -> "pending"
                },
                color = when (alarm.syncState) {
                    AlarmSyncState.SYNCED -> com.marvneo.companion.ui.theme.StatusPositive
                    AlarmSyncState.FAILED -> com.marvneo.companion.ui.theme.StatusCritical
                    else -> com.marvneo.companion.ui.theme.StatusWarning
                }
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onSend, enabled = canSend) { Text("Send to watch") }
            TextButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
            }
        }
    }
}

@Composable
private fun AlarmEditorDialog(
    initial: AlarmRecord,
    onDismiss: () -> Unit,
    onSave: (AlarmRecord) -> Unit
) {
    var hour by remember { mutableStateOf(initial.hour.toString()) }
    var minute by remember { mutableStateOf(initial.minute.toString()) }
    var mask by remember { mutableStateOf(initial.repeatDays) }
    var enabled by remember { mutableStateOf(initial.isEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alarm ${initial.id + 1}") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = hour,
                        onValueChange = { hour = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hour") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minute,
                        onValueChange = { minute = it.filter(Char::isDigit).take(2) },
                        label = { Text("Minute") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(Spacing.md))
                Text("Repeat", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayChip("S", mask, CrrepaProtocol.REPEAT_SUNDAY) { mask = mask xor it }
                    DayChip("M", mask, CrrepaProtocol.REPEAT_MONDAY) { mask = mask xor it }
                    DayChip("T", mask, CrrepaProtocol.REPEAT_TUESDAY) { mask = mask xor it }
                    DayChip("W", mask, CrrepaProtocol.REPEAT_WEDNESDAY) { mask = mask xor it }
                    DayChip("T", mask, CrrepaProtocol.REPEAT_THURSDAY) { mask = mask xor it }
                    DayChip("F", mask, CrrepaProtocol.REPEAT_FRIDAY) { mask = mask xor it }
                    DayChip("S", mask, CrrepaProtocol.REPEAT_SATURDAY) { mask = mask xor it }
                }
                Spacer(Modifier.height(Spacing.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enabled", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val h = hour.toIntOrNull()?.coerceIn(0, 23) ?: initial.hour
                val m = minute.toIntOrNull()?.coerceIn(0, 59) ?: initial.minute
                onSave(initial.copy(hour = h, minute = m, repeatDays = mask, isEnabled = enabled))
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DayChip(label: String, mask: Int, bit: Int, onToggle: (Int) -> Unit) {
    FilterChip(
        selected = mask and bit != 0,
        onClick = { onToggle(bit) },
        label = { Text(label) }
    )
}

private fun emptyAlarm(usedSlots: Int): AlarmRecord {
    val slot = (0..4).firstOrNull { it >= usedSlots } ?: 0
    return AlarmRecord(
        id = slot,
        hour = 7,
        minute = 30,
        repeatDays = CrrepaProtocol.REPEAT_EVERY_DAY,
        isEnabled = true,
        label = "Alarm",
        syncState = AlarmSyncState.PENDING,
        updatedAt = 0L
    )
}

private fun repeatLabel(mask: Int): String = when (mask) {
    CrrepaProtocol.REPEAT_EVERY_DAY -> "Every day"
    0 -> "Once"
    else -> {
        val days = listOf(
            "Sun" to CrrepaProtocol.REPEAT_SUNDAY,
            "Mon" to CrrepaProtocol.REPEAT_MONDAY,
            "Tue" to CrrepaProtocol.REPEAT_TUESDAY,
            "Wed" to CrrepaProtocol.REPEAT_WEDNESDAY,
            "Thu" to CrrepaProtocol.REPEAT_THURSDAY,
            "Fri" to CrrepaProtocol.REPEAT_FRIDAY,
            "Sat" to CrrepaProtocol.REPEAT_SATURDAY
        ).filter { mask and it.second != 0 }.map { it.first }
        days.joinToString(", ")
    }
}

private fun describeOutcome(outcome: CommandOutcome): String = when (outcome) {
    CommandOutcome.Idle -> "Not sent yet."
    CommandOutcome.Sending -> "Sending…"
    is CommandOutcome.AwaitingAcknowledgement ->
        "Sent to the watch. The watch did not acknowledge, and this protocol has no confirmation " +
            "message — so this is not reported as success."
    is CommandOutcome.Acknowledged -> "The watch confirmed the command."
    is CommandOutcome.Failed -> "Could not send: ${outcome.reason}"
}
