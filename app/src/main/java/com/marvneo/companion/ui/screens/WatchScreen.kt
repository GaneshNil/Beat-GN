package com.marvneo.companion.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.marvneo.companion.data.ble.MarvNeoBleManager
import com.marvneo.companion.data.ble.protocol.CrrepaProtocol
import com.marvneo.companion.data.repository.AlarmRepository
import com.marvneo.companion.data.db.entity.AlarmRecord
import com.marvneo.companion.data.db.entity.AlarmSyncState
import com.marvneo.companion.domain.model.AlarmSlots
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

@Composable
fun WatchScreen(
    bleManager: MarvNeoBleManager,
    alarmRepository: AlarmRepository,
    bluetoothGranted: Boolean,
    onRequestBluetoothPermissions: () -> Unit,
    onConnect: (String) -> Unit,
    onReconnect: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    val connection by bleManager.connectionState.collectAsStateWithLifecycle()
    val devices by bleManager.discoveredDevices.collectAsStateWithLifecycle()
    val scanning by bleManager.isScanning.collectAsStateWithLifecycle()
    val findOutcome by bleManager.findWatchOutcome.collectAsStateWithLifecycle()
    val alarms by alarmRepository.alarms.collectAsStateWithLifecycle(initialValue = null)
    val alarmBusy by alarmRepository.busy.collectAsStateWithLifecycle()
    val alarmError by alarmRepository.error.collectAsStateWithLifecycle()
    val batteryMessage by bleManager.batteryReadMessage.collectAsStateWithLifecycle()
    val bluetoothOn by bleManager.bluetoothEnabled.collectAsStateWithLifecycle()
    val address by bleManager.connectedDeviceAddress.collectAsStateWithLifecycle()
    val battery by bleManager.batteryFlow.collectAsStateWithLifecycle()
    val batteryKnown by bleManager.batteryKnown.collectAsStateWithLifecycle()
    val mtu by bleManager.negotiatedMtu.collectAsStateWithLifecycle()
    val firmware by bleManager.firmwareVersion.collectAsStateWithLifecycle()

    val ready = connection is ConnectionState.Ready
    var editSlot by rememberSaveable { mutableStateOf<Int?>(null) }
    var addingAlarm by rememberSaveable { mutableStateOf(false) }
    var deleteSlot by rememberSaveable { mutableStateOf<Int?>(null) }
    val freeAlarmSlot = alarms?.let { rows -> AlarmSlots.firstFree(rows.map { it.id }) }
    val editorSlot = if (addingAlarm) freeAlarmSlot?.let(::emptyAlarm)
        else alarms?.firstOrNull { it.id == editSlot }
    val linkActive = address != null || connection is ConnectionState.Reconnecting

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
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    when {
                        !bluetoothGranted -> Button(onClick = onRequestBluetoothPermissions) {
                            Text("Grant Bluetooth access")
                        }

                        linkActive -> OutlinedButton(onClick = { bleManager.disconnect() }) {
                            Icon(Icons.Outlined.Close, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (ready || connection is ConnectionState.Unstable) "Disconnect" else "Cancel connection")
                        }

                        scanning -> OutlinedButton(onClick = { bleManager.stopScan() }) {
                            Text("Stop scan")
                        }

                        else -> Button(enabled = bluetoothOn, onClick = { bleManager.startScan() }) {
                            Icon(Icons.Outlined.Search, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Scan")
                        }
                    }
                    OutlinedButton(
                        enabled = bluetoothGranted && bluetoothOn && !linkActive && !scanning && bleManager.canReconnect(),
                        onClick = onReconnect
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
                    subtitle = "Select your watch from nearby Bluetooth devices"
                )
            }
            items(devices, key = { it.address }) { device ->
                AppCard(onClick = { onConnect(device.address) }) {
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
                                text = "…${device.address.takeLast(5)} · ${device.rssi} dBm",
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
                    text = "Requests a vibration. Check the watch for the result.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.md))
                Button(
                    enabled = ready && findOutcome !is CommandOutcome.Sending,
                    onClick = { bleManager.findWatch() }
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
                subtitle = "Five slots with local storage and Bluetooth writes",
                trailing = {
                    IconButton(
                        enabled = freeAlarmSlot != null && !alarmBusy,
                        onClick = { addingAlarm = true }
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add alarm")
                    }
                }
            )
        }

        item {
            InlineNotice(
                title = "Check alarms on the watch",
                message = "Sent means the Bluetooth write completed. Check the slot and schedule on the watch.",
                tone = NoticeTone.INFO
            )
        }
        alarmError?.let { message ->
            item { InlineNotice(title = "Alarm update failed", message = message, tone = NoticeTone.WARNING) }
        }

        val currentAlarms = alarms
        when {
            currentAlarms == null -> item { Text("Loading alarms…") }
            currentAlarms.isEmpty() -> item {
                EmptyState(
                    icon = Icons.Outlined.Alarm,
                    title = "No alarms configured",
                    message = "Save an alarm on this phone. Send it from this screen when the watch is connected."
                )
            }
            else -> {
                if (freeAlarmSlot == null) {
                    item {
                        InlineNotice(
                            title = "All five phone slots are in use",
                            message = "Delete a phone alarm to free a slot.",
                            tone = NoticeTone.WARNING
                        )
                    }
                }
                items(currentAlarms, key = { it.id }) { alarm ->
                    AlarmCard(
                        alarm = alarm,
                        onEdit = { editSlot = alarm.id },
                        onDelete = { deleteSlot = alarm.id },
                        onSend = { alarmRepository.send(alarm.id) },
                        enabled = !alarmBusy,
                        canSend = ready &&
                            AlarmSlots.isValid(alarm.id, alarm.hour, alarm.minute, alarm.repeatDays, alarm.isEnabled)
                    )
                }
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
                KeyValueRow("Address suffix", address?.takeLast(5) ?: "—")
                Spacer(Modifier.height(Spacing.md))
                OutlinedButton(
                    enabled = (ready || connection is ConnectionState.Unstable) && batteryMessage != "Reading battery…",
                    onClick = { bleManager.readBattery() }
                ) {
                    Icon(Icons.Outlined.BatteryFull, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Read battery")
                }
                batteryMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
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
            onDismiss = {
                editSlot = null
                addingAlarm = false
            },
            onSave = { updated ->
                alarmRepository.save(updated, addingAlarm, sendWhenReady = ready)
                editSlot = null
                addingAlarm = false
            }
        )
    }
    deleteSlot?.let { slot ->
        AlertDialog(
            onDismissRequest = { deleteSlot = null },
            title = { Text("Delete phone alarm?") },
            text = { Text("A matching watch alarm can still ring. Disable it first and check the watch if you want to stop it.") },
            confirmButton = {
                TextButton(enabled = !alarmBusy, onClick = {
                    alarmRepository.delete(slot)
                    deleteSlot = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteSlot = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AlarmCard(
    alarm: AlarmRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
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
                    AlarmSyncState.SENT, AlarmSyncState.SYNCED -> "sent · check watch"
                    AlarmSyncState.FAILED -> "failed"
                    else -> "pending"
                },
                color = when (alarm.syncState) {
                    AlarmSyncState.FAILED -> com.marvneo.companion.ui.theme.StatusCritical
                    else -> com.marvneo.companion.ui.theme.StatusWarning
                }
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            TextButton(onClick = onEdit, enabled = enabled) { Text("Edit") }
            TextButton(onClick = onSend, enabled = enabled && canSend) { Text("Send to watch") }
            TextButton(onClick = onDelete, enabled = enabled) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete phone alarm")
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
    var hour by rememberSaveable(initial.id) { mutableStateOf(initial.hour.toString()) }
    var minute by rememberSaveable(initial.id) { mutableStateOf(initial.minute.toString()) }
    var mask by rememberSaveable(initial.id) { mutableStateOf(initial.repeatDays) }
    var enabled by rememberSaveable(initial.id) { mutableStateOf(initial.isEnabled) }

    val validHour = hour.toIntOrNull()?.takeIf { it in 0..23 }
    val validMinute = minute.toIntOrNull()?.takeIf { it in 0..59 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alarm ${initial.id + 1}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = hour,
                        onValueChange = { hour = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hour (0–23)") },
                        isError = validHour == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minute,
                        onValueChange = { minute = it.filter(Char::isDigit).take(2) },
                        label = { Text("Minute (0–59)") },
                        isError = validMinute == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(Spacing.md))
                Text("Repeat", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(Spacing.xs))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DayChip("Sun", mask, CrrepaProtocol.REPEAT_SUNDAY) { mask = mask xor it }
                    DayChip("Mon", mask, CrrepaProtocol.REPEAT_MONDAY) { mask = mask xor it }
                    DayChip("Tue", mask, CrrepaProtocol.REPEAT_TUESDAY) { mask = mask xor it }
                    DayChip("Wed", mask, CrrepaProtocol.REPEAT_WEDNESDAY) { mask = mask xor it }
                    DayChip("Thu", mask, CrrepaProtocol.REPEAT_THURSDAY) { mask = mask xor it }
                    DayChip("Fri", mask, CrrepaProtocol.REPEAT_FRIDAY) { mask = mask xor it }
                    DayChip("Sat", mask, CrrepaProtocol.REPEAT_SATURDAY) { mask = mask xor it }
                }
                if (enabled && mask == 0) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "Select at least one repeat day or disable the alarm.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                val h = validHour ?: return@TextButton
                val m = validMinute ?: return@TextButton
                onSave(initial.copy(hour = h, minute = m, repeatDays = mask, isEnabled = enabled))
            }, enabled = validHour != null && validMinute != null && (!enabled || mask != 0)) { Text("Save") }
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

private fun emptyAlarm(slot: Int): AlarmRecord {
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
    0 -> "No repeat days"
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
        "Bluetooth write completed. Check the watch for vibration."
    is CommandOutcome.Acknowledged -> "The watch confirmed the command."
    is CommandOutcome.Failed -> "Could not send: ${outcome.reason}"
}
