package com.marvneo.companion.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marvneo.companion.data.ble.MarvNeoBleManager
import com.marvneo.companion.data.ble.protocol.CrrepaProtocol
import com.marvneo.companion.data.db.AppDatabase
import com.marvneo.companion.data.db.entity.AlarmRecord
import com.marvneo.companion.domain.model.AlarmModel
import com.marvneo.companion.domain.model.ConnectionState
import com.marvneo.companion.ui.components.LiquidGlassCard
import com.marvneo.companion.ui.components.LiquidGlassPill
import com.marvneo.companion.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun WatchSettingsScreen(
    bleManager: MarvNeoBleManager,
    database: AppDatabase,
    onOpenBatteryOptimization: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val prefs = remember { context.getSharedPreferences("marv_neo_prefs", Context.MODE_PRIVATE) }
    val connectionState by bleManager.connectionState.collectAsState()
    val isConnected = connectionState is ConnectionState.Connected

    var is24HourFormat by remember { mutableStateOf(prefs.getBoolean("format_24h", true)) }
    var isRaiseToWake by remember { mutableStateOf(true) }
    var isFindingWatch by remember { mutableStateOf(false) }

    val savedAlarms by database.healthDao().getAllAlarms().collectAsState(initial = emptyList())

    val isVivoOrOriginOS = remember {
        Build.MANUFACTURER.contains("vivo", ignoreCase = true) ||
                Build.BRAND.contains("iqoo", ignoreCase = true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "Watch Settings & Alarms",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
        Text(
            text = "Hardware features, vibration alert, and persistent onboard alarms",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Find Watch Physical Alert Card (Opcode 0x61)
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            glowColor = if (isFindingWatch) AccentAmber else PrimaryElectric,
            glowAlpha = 0.15f
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
                            .background(PrimaryElectric.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Vibration, null, tint = PrimaryElectric, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Find Smartwatch", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Authentic Crrepa Opcode 0x61 physical alert", fontSize = 11.sp, color = TextSecondary)
                    }
                }

                if (isFindingWatch) {
                    LiquidGlassPill(color = AccentAmber) {
                        Text("Ringing Watch...", color = AccentAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Sends the verified Crrepa [0xFE, 0xEA, 0x10, 0x06, 0x61, 0x01] command packet to trigger physical vibration and screen wake on your Marv Neo.",
                color = TextSecondary,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        isFindingWatch = true
                        coroutineScope.launch {
                            bleManager.findWatch(true)
                        }
                    },
                    enabled = isConnected && !isFindingWatch,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryElectric)
                ) {
                    Icon(Icons.Default.NotificationsActive, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Trigger Alert", color = DarkBackground, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        isFindingWatch = false
                        coroutineScope.launch {
                            bleManager.findWatch(false)
                        }
                    },
                    enabled = isConnected && isFindingWatch,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Stop Alert")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Persistent Onboard Alarm Synchronization (Opcode 0x11)
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            glowColor = SleepViolet,
            glowAlpha = 0.12f
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
                            .background(SleepViolet.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Alarm, null, tint = SleepViolet, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Onboard Alarm Manager", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Crrepa Opcode 0x11 onboard persistent alarms", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "These alarms are saved directly into the smartwatch's persistent flash memory. They will vibrate on the watch at the configured time even if the phone is turned off or disconnected.",
                color = TextSecondary,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3 Pre-configured / Saved Alarm Slots
            val defaultAlarms = listOf(
                AlarmRecord(id = 0, hour = 7, minute = 0, repeatDays = 0x7F, isEnabled = true, label = "Morning Wake Up"),
                AlarmRecord(id = 1, hour = 13, minute = 30, repeatDays = 0x1F, isEnabled = false, label = "Midday Routine"),
                AlarmRecord(id = 2, hour = 22, minute = 0, repeatDays = 0x7F, isEnabled = true, label = "Bedtime Reminder")
            )

            val displayAlarms = if (savedAlarms.isNotEmpty()) savedAlarms else defaultAlarms

            displayAlarms.forEach { alarm ->
                var enabled by remember(alarm.id) { mutableStateOf(alarm.isEnabled) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(Color(0x221E293B), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                String.format(java.util.Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (enabled) TextPrimary else TextTertiary
                            )
                            Text(alarm.label, color = TextSecondary, fontSize = 11.sp)
                        }

                        Switch(
                            checked = enabled,
                            onCheckedChange = { isChecked ->
                                enabled = isChecked
                                coroutineScope.launch {
                                    val updated = alarm.copy(isEnabled = isChecked)
                                    database.healthDao().insertAlarm(updated)
                                    if (isConnected) {
                                        bleManager.sendAlarm(
                                            AlarmModel(
                                                id = updated.id,
                                                hour = updated.hour,
                                                minute = updated.minute,
                                                repeatDaysMask = updated.repeatDays,
                                                isEnabled = updated.isEnabled,
                                                label = updated.label
                                            )
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    coroutineScope.launch {
                        displayAlarms.forEach { alarm ->
                            database.healthDao().insertAlarm(alarm)
                            if (isConnected) {
                                bleManager.sendAlarm(
                                    AlarmModel(
                                        id = alarm.id,
                                        hour = alarm.hour,
                                        minute = alarm.minute,
                                        repeatDaysMask = alarm.repeatDays,
                                        isEnabled = alarm.isEnabled,
                                        label = alarm.label
                                    )
                                )
                            }
                        }
                    }
                },
                enabled = isConnected,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SleepViolet)
            ) {
                Icon(Icons.Default.Sync, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sync All Alarms to Watch Memory", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Hardware Watch Preferences
        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), glowColor = PrimaryElectric, glowAlpha = 0.08f) {
            Text("Smartwatch Preferences", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(modifier = Modifier.height(10.dp))

            // 12/24 Hour Format Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("24-Hour Time Format", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                    Text("Switch between 12-hour AM/PM and 24-hour clock", color = TextSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = is24HourFormat,
                    onCheckedChange = { checked ->
                        is24HourFormat = checked
                        prefs.edit().putBoolean("format_24h", checked).apply()
                        coroutineScope.launch {
                            bleManager.sendCommand(CrrepaProtocol.createTimeFormatPacket(checked))
                        }
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = BorderSubtle)

            // Raise to wake toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Raise to Wake Display", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                    Text("Turn screen on when wrist is raised", color = TextSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = isRaiseToWake,
                    onCheckedChange = { checked ->
                        isRaiseToWake = checked
                        coroutineScope.launch {
                            bleManager.sendCommand(CrrepaProtocol.createRaiseToWakePacket(checked))
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. OriginOS 6+ / Vivo Aggressive Battery Optimization Card
        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), glowColor = AccentAmber, glowAlpha = 0.12f) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BatteryAlert, contentDescription = "Battery", tint = AccentAmber)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isVivoOrOriginOS) "OriginOS 6+ Keep-Alive Lock" else "Background Battery Saver Exemption",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                if (isVivoOrOriginOS) {
                    "OriginOS 6+ freezes BLE background connections. Enable 'High Background Power Consumption' and 'Auto-start' so the watch remains connected."
                } else {
                    "Disable battery optimizations to prevent Android from closing the watch connection in the background."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenBatteryOptimization,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryElectric)
                ) {
                    Text("Exempt App", color = DarkBackground, fontWeight = FontWeight.Bold)
                }

                if (isVivoOrOriginOS) {
                    OutlinedButton(
                        onClick = { openVivoBackgroundSettings(context) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("OriginOS Settings")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Disconnect & Unpair Card
        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), glowColor = HeartRateCrimson, glowAlpha = 0.08f) {
            Text("Device Pairing Management", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Unlink the smartwatch and forget saved Bluetooth MAC address. The app will return to scan mode.",
                color = TextSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    bleManager.forgetDevice()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HeartRateCrimson)
            ) {
                Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Unpair & Forget Device")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun openVivoBackgroundSettings(context: Context) {
    try {
        val intent = Intent().apply {
            component = ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
            )
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.PurviewTabActivity"
                )
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
            context.startActivity(intent)
        }
    }
}
