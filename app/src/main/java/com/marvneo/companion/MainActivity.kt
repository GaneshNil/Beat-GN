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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.content.ContextCompat
import com.marvneo.companion.data.service.WatchCompanionService
import com.marvneo.companion.ui.screens.DashboardScreen
import com.marvneo.companion.ui.screens.WatchFaceStudioScreen
import com.marvneo.companion.ui.screens.WatchSettingsScreen
import com.marvneo.companion.ui.theme.MarvNeoTheme

class MainActivity : ComponentActivity() {

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
        // Enforce Android 15/16 modern Edge-to-Edge display
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        requestRequiredPermissions()

        val bleManager = (application as MarvNeoApp).bleManager

        setContent {
            MarvNeoTheme {
                var selectedTab by remember { mutableIntStateOf(0) }
                val haptic = LocalHapticFeedback.current

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        NavigationBar(
                            tonalElevation = NavigationBarDefaults.Elevation,
                            windowInsets = NavigationBarDefaults.windowInsets
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = {
                                    if (selectedTab != 0) {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        selectedTab = 0
                                    }
                                },
                                icon = { Icon(Icons.Default.Dashboard, "Dashboard") },
                                label = { Text("Dashboard") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = {
                                    if (selectedTab != 1) {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        selectedTab = 1
                                    }
                                },
                                icon = { Icon(Icons.Default.Palette, "Studio") },
                                label = { Text("Studio") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = {
                                    if (selectedTab != 2) {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        selectedTab = 2
                                    }
                                },
                                icon = { Icon(Icons.Default.SettingsIcon, "Settings") },
                                label = { Text("Settings") }
                            )
                        }
                    }
                ) { innerPadding ->
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "TabTransition"
                    ) { tab ->
                        when (tab) {
                            0 -> DashboardScreen(bleManager = bleManager, modifier = Modifier.padding(innerPadding))
                            1 -> WatchFaceStudioScreen(bleManager = bleManager, modifier = Modifier.padding(innerPadding))
                            2 -> WatchSettingsScreen(
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
