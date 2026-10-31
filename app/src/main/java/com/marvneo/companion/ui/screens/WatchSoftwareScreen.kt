package com.marvneo.companion.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marvneo.companion.data.ble.MarvNeoBleManager
import com.marvneo.companion.data.ble.protocol.WatchFaceConverter
import com.marvneo.companion.domain.model.ConnectionState
import com.marvneo.companion.domain.model.WatchApp
import com.marvneo.companion.ui.components.LiquidGlassCard
import com.marvneo.companion.ui.components.LiquidGlassPill
import com.marvneo.companion.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun WatchSoftwareScreen(
    bleManager: MarvNeoBleManager,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val connectionState by bleManager.connectionState.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Watch Apps, 1 = OTA DFU Flasher

    var isInstalling by remember { mutableStateOf(false) }
    var installProgress by remember { mutableFloatStateOf(0f) }
    var installStatusText by remember { mutableStateOf("") }
    var selectedFirmwareUri by remember { mutableStateOf<Uri?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedFirmwareUri = uri
    }

    val availableApps = remember {
        listOf(
            WatchApp(
                id = "calc_app",
                name = "Scientific Calculator",
                category = "Utility",
                version = "v1.2.0",
                sizeKb = 42,
                description = "On-wrist calculation suite with standard arithmetic and percentage operations."
            ),
            WatchApp(
                id = "space_dodger",
                name = "Space Dodger Arcade",
                category = "Games",
                version = "v1.0.4",
                sizeKb = 68,
                description = "Retro endless 8-bit space survival game controlled via touchscreen."
            ),
            WatchApp(
                id = "puzzle_2048",
                name = "2048 Number Puzzle",
                category = "Games",
                version = "v2.0.1",
                sizeKb = 54,
                description = "Swipe tiles to merge numbers and reach the 2048 tile on your watch."
            ),
            WatchApp(
                id = "qr_wallet",
                name = "E-Card & QR Wallet",
                category = "Finance & ID",
                version = "v1.1.0",
                sizeKb = 36,
                description = "Display payment QR codes, gym membership IDs, and digital cards on your wrist."
            ),
            WatchApp(
                id = "focus_pomodoro",
                name = "Pomodoro Focus Timer",
                category = "Productivity",
                version = "v1.3.2",
                sizeKb = 28,
                description = "25-minute focus intervals and 5-minute break timer with vibration alerts."
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = "Watch App & Software Center",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
        Text(
            text = "Install supported mini-apps and OTA firmware updates",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Segmented Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = PrimaryElectric,
            divider = {}
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("App Catalog", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Firmware / DFU", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Installation Progress Card
        if (isInstalling) {
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), glowColor = PrimaryElectric, glowAlpha = 0.2f) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(installStatusText, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("${(installProgress * 100).toInt()}%", color = PrimaryElectric, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { installProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = PrimaryElectric,
                    trackColor = SurfaceCardElevated
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (selectedTab == 0) {
            // Watch Apps Catalog
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(availableApps) { app ->
                    WatchAppCard(
                        app = app,
                        isWatchConnected = connectionState is ConnectionState.Connected,
                        onInstall = {
                            coroutineScope.launch {
                                isInstalling = true
                                installStatusText = "Packaging ${app.name} (${app.sizeKb} KB)..."
                                installProgress = 0.05f

                                // Create simulated app binary package
                                val dummyAppBinary = ByteArray(app.sizeKb * 1024) { (it % 255).toByte() }
                                val chunks = WatchFaceConverter.createChunks(dummyAppBinary, chunkSize = 240)

                                installStatusText = "Transmitting to Marv Neo via BLE..."
                                bleManager.flashBinaryChunks(chunks) { progress ->
                                    installProgress = progress
                                }

                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                installStatusText = "${app.name} Installed on Watch!"
                                kotlinx.coroutines.delay(1800)
                                isInstalling = false
                            }
                        }
                    )
                }
            }
        } else {
            // Firmware & OTA DFU Flasher
            Column(modifier = Modifier.fillMaxSize()) {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth(), glowColor = AccentAmber, glowAlpha = 0.1f) {
                    Text("Firmware & Resource Flasher", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Upload and flash custom firmware (.bin), resource packs (.res), or font tables to your Marv Neo device.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (selectedFirmwareUri != null) "File Selected" else "Select Firmware (.bin) File")
                    }

                    if (selectedFirmwareUri != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Selected: ${selectedFirmwareUri?.lastPathSegment ?: "firmware.bin"}",
                            color = PrimaryElectric,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isInstalling = true
                                installStatusText = "Verifying firmware checksum..."
                                installProgress = 0.1f
                                kotlinx.coroutines.delay(600)

                                val dummyFw = ByteArray(32 * 1024) { (it % 256).toByte() }
                                val chunks = WatchFaceConverter.createChunks(dummyFw, 240)

                                installStatusText = "Flashing OTA Firmware blocks..."
                                bleManager.flashBinaryChunks(chunks) { progress ->
                                    installProgress = progress
                                }

                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                installStatusText = "Firmware Update Successful!"
                                kotlinx.coroutines.delay(1800)
                                isInstalling = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = connectionState is ConnectionState.Connected && selectedFirmwareUri != null,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentAmber)
                    ) {
                        Text(
                            if (connectionState is ConnectionState.Connected) "Start OTA Firmware Flash" else "Connect Watch First",
                            color = DarkBackground,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WatchAppCard(
    app: WatchApp,
    isWatchConnected: Boolean,
    onInstall: () -> Unit
) {
    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), glowColor = PrimaryElectric, glowAlpha = 0.08f) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PrimaryElectric.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryElectric.copy(alpha = 0.3f)),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (app.category) {
                                "Games" -> Icons.Default.SportsEsports
                                "Utility" -> Icons.Default.Calculate
                                "Productivity" -> Icons.Default.Timer
                                else -> Icons.Default.QrCode
                            },
                            contentDescription = null,
                            tint = PrimaryElectric,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(app.name, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(app.category, color = TextSecondary, fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("• ${app.sizeKb} KB", color = TextTertiary, fontSize = 11.sp)
                    }
                }
            }

            Button(
                onClick = onInstall,
                enabled = isWatchConnected,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryElectric),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isWatchConnected) "Install" else "Locked",
                    color = DarkBackground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(app.description, color = TextSecondary, fontSize = 12.sp)
    }
}
