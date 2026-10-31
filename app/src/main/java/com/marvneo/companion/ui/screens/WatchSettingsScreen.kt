package com.marvneo.companion.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marvneo.companion.data.ble.MarvNeoBleManager
import com.marvneo.companion.data.ble.protocol.CrrepaProtocol
import com.marvneo.companion.ui.components.LiquidGlassCard
import com.marvneo.companion.ui.components.LiquidGlassPill
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

    val prefs = remember { context.getSharedPreferences("marv_neo_prefs", Context.MODE_PRIVATE) }
    var geminiApiKey by remember { mutableStateOf(prefs.getString("gemini_api_key", "") ?: "") }
    var isKeyVisible by remember { mutableStateOf(false) }
    var apiKeySavedFeedback by remember { mutableStateOf(false) }

    var is24HourFormat by remember { mutableStateOf(prefs.getBoolean("format_24h", true)) }
    var isRaiseToWake by remember { mutableStateOf(true) }
    var isFindingWatch by remember { mutableStateOf(false) }

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
            text = "Device & Agent Settings",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
        Text(
            text = "Customization, OriginOS keep-alive, and Gemini AI configuration",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Gemini AI Agent API Configuration Card
        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), glowColor = SleepViolet, glowAlpha = 0.15f) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SmartToy, contentDescription = null, tint = SleepViolet, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Gemini AI API Configuration", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(6.dp))

            if (geminiApiKey.isNotBlank()) {
                LiquidGlassPill(color = StepsEmerald) {
                    Icon(Icons.Default.CheckCircle, null, tint = StepsEmerald, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("API Key Configured • Model: gemini-1.5-flash", color = StepsEmerald, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
            Text(
                "Enter your Google Gemini API key to unlock natural language understanding and autonomous agent commands from your smartwatch.",
                color = TextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = geminiApiKey,
                onValueChange = {
                    geminiApiKey = it
                    apiKeySavedFeedback = false
                },
                placeholder = { Text("AIzaSy...") },
                label = { Text("Gemini API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                        Icon(if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle Visibility")
                    }
                },
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    prefs.edit().putString("gemini_api_key", geminiApiKey.trim()).apply()
                    apiKeySavedFeedback = true
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SleepViolet)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (apiKeySavedFeedback) "Key Saved Successfully!" else "Save API Key", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // OriginOS 6+ / Vivo Aggressive Battery Optimization Card
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
                    "OriginOS 6+ suspends BLE background tasks. Enable 'High Background Power Consumption' and 'Auto-start' so the watch never disconnects."
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

        // Hardware Watch Preferences
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

        // Disconnect & Unpair Card
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
