package com.marvneo.companion.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.marvneo.companion.data.ble.MarvNeoBleManager
import com.marvneo.companion.data.ble.protocol.CrrepaProtocol
import com.marvneo.companion.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun WatchSettingsScreen(
    bleManager: MarvNeoBleManager,
    onOpenBatteryOptimization: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isFindingWatch by remember { mutableStateOf(false) }
    var isRaiseToWake by remember { mutableStateOf(true) }
    var isCameraMode by remember { mutableStateOf(false) }

    val isVivoOrOriginOS = remember {
        Build.MANUFACTURER.contains("vivo", ignoreCase = true) ||
                Build.BRAND.contains("iqoo", ignoreCase = true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text("Device Controls", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(modifier = Modifier.height(16.dp))

        // OriginOS 6+ / Vivo Background Keep-Alive Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BatteryAlert, contentDescription = "Battery", tint = AccentAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isVivoOrOriginOS) "OriginOS 6 Background Lock" else "Battery Optimization",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (isVivoOrOriginOS) {
                        "OriginOS suspends BLE services aggressively. Allow 'High Background Power Consumption' and 'Auto-start' to maintain 24/7 watch connection."
                    } else {
                        "Exclude app from battery optimizations to prevent Android from closing the watch connection in the background."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onOpenBatteryOptimization,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryElectric)
                    ) {
                        Text("Disable Battery Saver", color = DarkBackground)
                    }

                    if (isVivoOrOriginOS) {
                        OutlinedButton(onClick = { openVivoBackgroundSettings(context) }) {
                            Text("Vivo Auto-Start")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Find Watch
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Find Watch", style = MaterialTheme.typography.titleMedium)
                    Text("Vibrates and wakes watch display", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Button(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        isFindingWatch = !isFindingWatch
                        coroutineScope.launch {
                            bleManager.sendCommand(CrrepaProtocol.createFindWatchPacket(isFindingWatch))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFindingWatch) HeartRateCrimson else AccentAmber
                    )
                ) {
                    Text(if (isFindingWatch) "Stop" else "Ring Watch")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Raise To Wake
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Raise to Wake", style = MaterialTheme.typography.titleMedium)
                    Text("Turn screen on when wrist rotates", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Switch(
                    checked = isRaiseToWake,
                    onCheckedChange = { checked ->
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        isRaiseToWake = checked
                        coroutineScope.launch {
                            bleManager.sendCommand(CrrepaProtocol.createRaiseToWakePacket(checked))
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Remote Camera Shutter
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Remote Camera Shutter", style = MaterialTheme.typography.titleMedium)
                    Text("Trigger watch photo button", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Switch(
                    checked = isCameraMode,
                    onCheckedChange = { open ->
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        isCameraMode = open
                        coroutineScope.launch {
                            bleManager.sendCommand(CrrepaProtocol.createCameraModePacket(open))
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Notification Whitelist", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))

        listOf("WhatsApp", "Phone Calls", "SMS Messages", "Instagram", "Telegram", "Gmail").forEach { app ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                var isEnabled by remember { mutableStateOf(true) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(app, style = MaterialTheme.typography.bodyLarge)
                    Checkbox(
                        checked = isEnabled,
                        onCheckedChange = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            isEnabled = it
                        }
                    )
                }
            }
        }
    }
}

private fun openVivoBackgroundSettings(context: Context) {
    try {
        val intent = Intent().apply {
            component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val intent = Intent().apply {
                component = ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
            }
            context.startActivity(intent)
        } catch (e2: Exception) {
            // Fallback to app details
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    }
}
