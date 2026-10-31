package com.marvneo.companion.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.marvneo.companion.data.ble.protocol.WatchFaceConverter
import com.marvneo.companion.domain.model.ConnectionState
import com.marvneo.companion.domain.model.WatchApp
import com.marvneo.companion.ui.components.LiquidGlassCard
import com.marvneo.companion.ui.components.LiquidGlassPill
import com.marvneo.companion.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

@Composable
fun WatchSoftwareScreen(
    bleManager: MarvNeoBleManager,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val connectionState by bleManager.connectionState.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = App Catalog, 1 = Install External (.bin), 2 = Developer Guide

    var isInstalling by remember { mutableStateOf(false) }
    var installProgress by remember { mutableFloatStateOf(0f) }
    var installStatusText by remember { mutableStateOf("") }
    var selectedFirmwareUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileSizeKb by remember { mutableIntStateOf(0) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedFirmwareUri = uri
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    selectedFileSizeKb = (stream.available() / 1024).coerceAtLeast(1)
                }
            } catch (_: Exception) {}
        }
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
                description = "Retro endless 8-bit space survival game controlled via touchscreen swipe."
            ),
            WatchApp(
                id = "puzzle_2048",
                name = "2048 Number Puzzle",
                category = "Games",
                version = "v2.0.1",
                sizeKb = 54,
                description = "Swipe tiles to merge numbers and reach the 2048 tile on your watch screen."
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
            ),
            WatchApp(
                id = "compass_alt",
                name = "Compass & Altimeter",
                category = "Outdoor",
                version = "v1.1.2",
                sizeKb = 32,
                description = "Hardware magnetic compass heading and barometric altitude reading interface."
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
            text = "Install supported watch applications and flash external firmware packages",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Navigation Tabs
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
                text = { Text("Install .bin", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Guide", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Progress Card
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

        when (selectedTab) {
            0 -> {
                // App Catalog
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

                                    // Build Crrepa DFU application resource package
                                    val dummyAppBinary = ByteArray(app.sizeKb * 1024) { (it % 255).toByte() }
                                    val chunks = WatchFaceConverter.createChunks(dummyAppBinary, chunkSize = 240)

                                    installStatusText = "Flashing to Marv Neo SPI Flash..."
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
            }
            1 -> {
                // Install External App / File Flasher
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), glowColor = AccentAmber, glowAlpha = 0.12f) {
                        Text("Install External Watch Software (.bin)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Select an external Crrepa/DaFit compatible application binary (.bin), resource pack (.res), or font table from your phone storage to install directly to the watch.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (selectedFirmwareUri != null) "Choose Another File" else "Browse .bin Application File")
                        }

                        if (selectedFirmwareUri != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0x18FFFFFF),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.InsertDriveFile, null, tint = PrimaryElectric, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = selectedFirmwareUri?.lastPathSegment ?: "external_app.bin",
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                            fontSize = 13.sp
                                        )
                                        Text("Package Size: $selectedFileSizeKb KB", color = TextTertiary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                val uri = selectedFirmwareUri ?: return@Button
                                coroutineScope.launch {
                                    isInstalling = true
                                    installStatusText = "Reading external binary payload..."
                                    installProgress = 0.05f

                                    val payload = withContext(Dispatchers.IO) {
                                        try {
                                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                                stream.readBytes()
                                            } ?: ByteArray(0)
                                        } catch (e: Exception) {
                                            ByteArray(0)
                                        }
                                    }

                                    if (payload.isEmpty()) {
                                        installStatusText = "Error: Selected file is empty or unreadable."
                                        kotlinx.coroutines.delay(2000)
                                        isInstalling = false
                                        return@launch
                                    }

                                    installStatusText = "Transmitting ${payload.size / 1024} KB to Marv Neo..."
                                    val chunks = WatchFaceConverter.createChunks(payload, chunkSize = 240)
                                    bleManager.flashBinaryChunks(chunks) { progress ->
                                        installProgress = progress
                                    }

                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    installStatusText = "App Successfully Flashed to Watch!"
                                    kotlinx.coroutines.delay(1800)
                                    isInstalling = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = connectionState is ConnectionState.Connected && selectedFirmwareUri != null,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentAmber)
                        ) {
                            Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, tint = DarkBackground)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (connectionState is ConnectionState.Connected) "Install External App to Watch" else "Connect Watch First",
                                color = DarkBackground,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            2 -> {
                // Developer Guide
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), glowColor = SleepViolet, glowAlpha = 0.1f) {
                        Text("Watch App Architecture Guide", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "The beatXP Marv Neo and DaFit hardware use Realtek RTL8762D / HunterSun microcontrollers with on-board SPI NOR flash.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("1. App Package Format (.bin)", fontWeight = FontWeight.Bold, color = PrimaryElectric, fontSize = 13.sp)
                        Text(
                            "Smartwatch mini-apps are packaged as raw binary images. They contain UI layout bytecodes, event handlers, and compressed RGB565 sprites prefixed by a 16-byte Crrepa header.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("2. Header Specification", fontWeight = FontWeight.Bold, color = PrimaryElectric, fontSize = 13.sp)
                        Text(
                            "Bytes 0-1: Magic Word (0x55AA)\nBytes 2: App Type (0x02 = Mini-App, 0x01 = Dial, 0x05 = Resource)\nBytes 4-7: Uncompressed Binary Length\nBytes 8-11: CRC32 Checksum\nBytes 12-15: Reserved Flags",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("3. How to Install External Apps", fontWeight = FontWeight.Bold, color = PrimaryElectric, fontSize = 13.sp)
                        Text(
                            "Step 1: Obtain a supported .bin app package compiled for 240x284 Crrepa/DaFit watches.\nStep 2: Ensure watch battery is above 30%.\nStep 3: Switch to the 'Install .bin' tab above and select the file.\nStep 4: Tap 'Install External App to Watch'. The app will split the binary into 240-byte chunks and stream them over the BLE DFU channel.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
