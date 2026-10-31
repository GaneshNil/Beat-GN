package com.marvneo.companion

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.marvneo.companion.data.service.WatchCompanionService
import com.marvneo.companion.ui.screens.*
import com.marvneo.companion.ui.theme.DarkBackground
import com.marvneo.companion.ui.theme.MarvNeoTheme
import com.marvneo.companion.ui.theme.PrimaryElectric

class MainActivity : ComponentActivity() {

    private val selectedTabState = mutableIntStateOf(0)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val bluetoothGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            true
        }
        if (bluetoothGranted) {
            startCompanionService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handleIntentExtras(intent)
        requestRequiredPermissions()

        val app = application as MarvNeoApp
        val bleManager = app.bleManager
        val voiceManager = app.voiceManager
        val database = app.database

        setContent {
            MarvNeoTheme {
                val selectedTab by selectedTabState
                val haptic = LocalHapticFeedback.current
                val scrollState = rememberScrollState()

                val navigationSections = listOf(
                    "Dashboard" to Icons.Default.Dashboard,
                    "Connection" to Icons.Default.Bluetooth,
                    "Health Monitor" to Icons.Default.Favorite,
                    "Health Reports" to Icons.Default.Analytics,
                    "Voice History" to Icons.Default.Mic,
                    "Theme Store" to Icons.Default.Palette,
                    "Watch Apps" to Icons.Default.Apps,
                    "Watch Settings" to Icons.Default.Settings,
                    "Device Info" to Icons.Default.Info
                )

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBackground),
                    contentWindowInsets = WindowInsets.safeDrawing,
                    topBar = {
                        // Quick Scrollable Section Chips Bar
                        Surface(
                            color = Color(0xCC090D16),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(scrollState)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                navigationSections.forEachIndexed { index, (label, icon) ->
                                    val isSelected = selectedTab == index
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (selectedTab != index) {
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                selectedTabState.intValue = index
                                            }
                                        },
                                        label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                        leadingIcon = {
                                            Icon(
                                                icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PrimaryElectric.copy(alpha = 0.2f),
                                            selectedLabelColor = PrimaryElectric,
                                            selectedLeadingIconColor = PrimaryElectric,
                                            containerColor = Color(0x221E293B),
                                            labelColor = Color(0xFF94A3B8)
                                        )
                                    )
                                }
                            }
                        }
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = Color(0xCC090D16),
                            tonalElevation = 0.dp,
                            windowInsets = NavigationBarDefaults.windowInsets
                        ) {
                            // 5 Primary bottom items with direct routing
                            val bottomItems = listOf(
                                Triple(0, "Home", Icons.Default.Dashboard),
                                Triple(1, "Connect", Icons.Default.Bluetooth),
                                Triple(2, "Health", Icons.Default.Favorite),
                                Triple(4, "Voice", Icons.Default.Mic),
                                Triple(7, "Settings", Icons.Default.Settings)
                            )

                            bottomItems.forEach { (tabIdx, label, icon) ->
                                val isSelected = when (tabIdx) {
                                    2 -> selectedTab == 2 || selectedTab == 3 // Health Monitor or Reports
                                    7 -> selectedTab in 5..8 // Themes, Software, Settings, or Device Info
                                    else -> selectedTab == tabIdx
                                }

                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = {
                                        if (selectedTab != tabIdx) {
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            selectedTabState.intValue = tabIdx
                                        }
                                    },
                                    icon = { Icon(icon, label) },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = PrimaryElectric,
                                        selectedTextColor = PrimaryElectric,
                                        indicatorColor = PrimaryElectric.copy(alpha = 0.15f)
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "MainScreenNavigation"
                    ) { tab ->
                        when (tab) {
                            0 -> DashboardScreen(
                                bleManager = bleManager,
                                onNavigateToTab = { target -> selectedTabState.intValue = target },
                                modifier = Modifier.padding(innerPadding)
                            )
                            1 -> WatchConnectionScreen(
                                bleManager = bleManager,
                                modifier = Modifier.padding(innerPadding)
                            )
                            2 -> HealthMonitoringScreen(
                                bleManager = bleManager,
                                modifier = Modifier.padding(innerPadding)
                            )
                            3 -> HealthReportsScreen(
                                database = database,
                                modifier = Modifier.padding(innerPadding)
                            )
                            4 -> VoiceAssistantHistoryScreen(
                                voiceManager = voiceManager,
                                database = database,
                                modifier = Modifier.padding(innerPadding)
                            )
                            5 -> WatchThemeStoreScreen(
                                bleManager = bleManager,
                                modifier = Modifier.padding(innerPadding)
                            )
                            6 -> WatchSoftwareScreen(
                                bleManager = bleManager,
                                modifier = Modifier.padding(innerPadding)
                            )
                            7 -> WatchSettingsScreen(
                                bleManager = bleManager,
                                database = database,
                                onOpenBatteryOptimization = { requestIgnoreBatteryOptimization() },
                                modifier = Modifier.padding(innerPadding)
                            )
                            8 -> DeviceInfoScreen(
                                bleManager = bleManager,
                                modifier = Modifier.padding(innerPadding)
                            )
                            else -> DashboardScreen(
                                bleManager = bleManager,
                                onNavigateToTab = { target -> selectedTabState.intValue = target },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentExtras(intent)
    }

    private fun handleIntentExtras(intent: Intent?) {
        val targetTab = intent?.getIntExtra("OPEN_TAB", -1) ?: -1
        if (targetTab in 0..8) {
            selectedTabState.intValue = targetTab
        }
        val triggerVoice = intent?.getBooleanExtra("TRIGGER_VOICE_LISTENING", false) ?: false
        if (triggerVoice) {
            val app = application as MarvNeoApp
            app.voiceManager.handleWatchVoiceTrigger()
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // Android 11 (API 30)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissions.add(Manifest.permission.RECORD_AUDIO)

        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startCompanionService() {
        val intent = Intent(this, WatchCompanionService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }
}
