package com.marvneo.companion.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marvneo.companion.data.ble.MarvNeoBleManager
import com.marvneo.companion.domain.model.ConnectionState
import com.marvneo.companion.ui.components.LiquidGlassCard
import com.marvneo.companion.ui.components.LiquidGlassPill
import com.marvneo.companion.ui.theme.*

@Composable
fun DeviceInfoScreen(
    bleManager: MarvNeoBleManager,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val connectionState by bleManager.connectionState.collectAsState()
    val connectedName by bleManager.connectedDeviceName.collectAsState()
    val connectedAddress by bleManager.connectedDeviceAddress.collectAsState()
    val battery by bleManager.batteryFlow.collectAsState()
    val isConnected = connectionState is ConnectionState.Connected

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "Device Information",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
        Text(
            text = "Hardware architecture, GATT channels, and firmware specifications",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Hardware Profile
        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), glowColor = PrimaryElectric, glowAlpha = 0.12f) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(PrimaryElectric.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Watch, null, tint = PrimaryElectric, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(connectedName ?: "beatXP Marv Neo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Target Hardware: HunterSun HS6621 / Realtek 8762D", fontSize = 11.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            InfoRow("Bluetooth MAC", connectedAddress ?: "Not Connected")
            InfoRow("Connection Status", if (isConnected) "Connected • Link Active" else "Disconnected")
            InfoRow("Battery Level", "$battery% (Telemetry flow active)")
            InfoRow("Display Matrix", "1.85\" IPS LCD • 240 x 284 px")
            InfoRow("Optical Sensor", "PPG Sensor (HX3600/HX3605 series)")
            InfoRow("BLE Framing", "Crrepa v1/v2 Framing (0xFE, 0xEA, 0x10)")
            InfoRow("GATT Buffer", "Negotiated MTU 512")
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bluetooth Audio & Voice Profile
        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), glowColor = SleepViolet, glowAlpha = 0.10f) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Headphones, null, tint = SleepViolet, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Dual Bluetooth Audio Profile", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "The Marv Neo uses two Bluetooth channels:\n" +
                        "1. BLE (Low Energy): Low-power telemetry, step counts, heart rate, dial flashing, and notifications.\n" +
                        "2. Bluetooth Classic (HFP/A2DP): Calling, microphone audio, and voice assistant trigger (AT+BVRA).\n" +
                        "Pair both identities in phone Bluetooth settings for calling and native voice features.",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Open Source & Privacy Statement
        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), glowColor = StepsEmerald, glowAlpha = 0.08f) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, null, tint = StepsEmerald, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Zero Telemetry & Offline Privacy", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Beat-GN operates 100% locally on your smartphone. No advertising trackers, no Mixpanel, and no cloud server sign-ins. Your health measurements and voice logs remain encrypted in local Room SQLite storage.",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Text(value, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 12.sp)
    }
}

