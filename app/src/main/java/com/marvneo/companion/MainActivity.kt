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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
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

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBackground),
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        NavigationBar(
                            containerColor = Color(0xCC090D16),
                            tonalElevation = 0.dp,
                            windowInsets = NavigationBarDefaults.windowInsets
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = {
                                    if (selectedTab != 0) {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        selectedTabState.intValue = 0
                                    }
                                },
                                icon = { Icon(Icons.Default.Dashboard, "Dashboard") },
                                label = { Text("Dashboard") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PrimaryElectric,
                                    selectedTextColor = PrimaryElectric,
                                    indicatorColor = PrimaryElectric.copy(alpha = 0.15f)
                                )
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = {
                                    if (selectedTab != 1) {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        selectedTabState.intValue = 1
                                    }
                                },
                                icon = { Icon(Icons.Default.Palette, "Themes") },
                                label = { Text("Dials") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PrimaryElectric,
                                    selectedTextColor = PrimaryElectric,
                                    indicatorColor = PrimaryElectric.copy(alpha = 0.15f)
                                )
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = {
                                    if (selectedTab != 2) {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        selectedTabState.intValue = 2
                                    }
                                },
                                icon = { Icon(Icons.Default.Mic, "AI Agent") },
                                label = { Text("Gemini") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PrimaryElectric,
                                    selectedTextColor = PrimaryElectric,
                                    indicatorColor = PrimaryElectric.copy(alpha = 0.15f)
                                )
                            )
                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = {
                                    if (selectedTab != 3) {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        selectedTabState.intValue = 3
                                    }
                                },
                                icon = { Icon(Icons.Default.Apps, "Apps") },
                                label = { Text("Apps") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PrimaryElectric,
                                    selectedTextColor = PrimaryElectric,
                                    indicatorColor = PrimaryElectric.copy(alpha = 0.15f)
                                )
                            )
                            NavigationBarItem(
                                selected = selectedTab == 4,
                                onClick = {
                                    if (selectedTab != 4) {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        selectedTabState.intValue = 4
                                    }
                                },
                                icon = { Icon(Icons.Default.Settings, "Settings") },
                                label = { Text("Settings") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PrimaryElectric,
                                    selectedTextColor = PrimaryElectric,
                                    indicatorColor = PrimaryElectric.copy(alpha = 0.15f)
                                )
                            )
                        }
                    }
                ) { innerPadding ->
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "MainScreenNavigation"
                    ) { tab ->
                        when (tab) {
                            0 -> DashboardScreen(bleManager = bleManager, modifier = Modifier.padding(innerPadding))
                            1 -> WatchThemeStoreScreen(bleManager = bleManager, modifier = Modifier.padding(innerPadding))
                            2 -> GeminiVoiceAssistantScreen(
                                voiceManager = voiceManager,
                                database = database,
                                modifier = Modifier.padding(innerPadding)
                            )
                            3 -> WatchSoftwareScreen(bleManager = bleManager, modifier = Modifier.padding(innerPadding))
                            4 -> WatchSettingsScreen(
                                bleManager = bleManager,
                                onOpenBatteryOptimization = { requestIgnoreBatteryOptimization() },
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
        if (targetTab in 0..4) {
            selectedTabState.intValue = targetTab
        }
        val triggerVoice = intent?.getBooleanExtra("TRIGGER_VOICE_LISTENING", false) ?: false
        if (triggerVoice) {
            val app = application as MarvNeoApp
            app.voiceManager.startListening()
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
