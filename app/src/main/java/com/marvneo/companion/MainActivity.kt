package com.marvneo.companion

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.marvneo.companion.data.service.WatchCompanionService
import com.marvneo.companion.ui.screens.AssistantScreen
import com.marvneo.companion.ui.screens.HealthScreen
import com.marvneo.companion.ui.screens.HomeScreen
import com.marvneo.companion.ui.screens.MoreScreen
import com.marvneo.companion.ui.screens.WatchScreen
import com.marvneo.companion.ui.theme.MarvNeoTheme

class MainActivity : ComponentActivity() {

    private var permissionRequested = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val bluetoothDenied = grants
            .filterKeys { it != Manifest.permission.POST_NOTIFICATIONS }
            .any { !it.value }
        if (!bluetoothDenied) {
            startCompanionService()
        } else {
            permissionRequested = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as MarvNeoApp

        setContent {
            MarvNeoTheme {
                val bleManager = app.bleManager
                val healthRepository = app.healthRepository

                var selectedTab by rememberSaveable { mutableStateOf(0) }
                var moreRoute by remember { mutableStateOf<String?>(null) }

                BackHandler(enabled = moreRoute != null) { moreRoute = null }

                val destinations = remember {
                    listOf(
                        Destination("Home", Icons.Outlined.Home),
                        Destination("Health", Icons.Outlined.FavoriteBorder),
                        Destination("Watch", Icons.Outlined.Watch),
                        Destination("Assistant", Icons.Outlined.RecordVoiceOver),
                        Destination("More", Icons.Outlined.MoreHoriz)
                    )
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            destinations.forEachIndexed { index, destination ->
                                NavigationBarItem(
                                    selected = selectedTab == index,
                                    onClick = {
                                        selectedTab = index
                                        moreRoute = null
                                    },
                                    icon = {
                                        Icon(destination.icon, contentDescription = destination.label)
                                    },
                                    label = { Text(destination.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AnimatedContent(
                            targetState = selectedTab to moreRoute,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "navigation"
                        ) { (tab, route) ->
                            when (tab) {
                                0 -> HomeScreen(
                                    bleManager = bleManager,
                                    healthRepository = healthRepository,
                                    onOpenWatch = { selectedTab = 2 },
                                    onOpenHealth = { selectedTab = 1 }
                                )

                                1 -> HealthScreen(
                                    bleManager = bleManager,
                                    healthRepository = healthRepository,
                                    onOpenWatch = { selectedTab = 2 }
                                )

                                2 -> WatchScreen(
                                    bleManager = bleManager,
                                    database = app.database,
                                    onOpenBatterySettings = { requestIgnoreBatteryOptimization() }
                                )

                                3 -> AssistantScreen(
                                    transcripts = app.transcripts
                                )

                                else -> MoreScreen(
                                    bleManager = bleManager,
                                    route = route,
                                    onNavigate = { moreRoute = it },
                                    onBack = { moreRoute = null }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        requestNeededPermissions()
        val app = application as MarvNeoApp
        app.bleManager.refreshAdapterState()
        if (app.bleManager.hasPermissions()) startCompanionService()
    }

    private fun requestNeededPermissions() {
        val app = application as MarvNeoApp
        val missing = app.bleManager.requiredPermissions().toMutableList()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val actuallyMissing = missing.filter {
            ContextCompat.checkSelfPermission(this, it) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (actuallyMissing.isEmpty()) {
            if (!permissionRequested) startCompanionService()
            return
        }
        if (permissionRequested) return
        permissionRequested = true
        permissionLauncher.launch(actuallyMissing.toTypedArray())
    }

    private fun startCompanionService() {
        runCatching {
            val intent = Intent(this, WatchCompanionService::class.java)
            ContextCompat.startForegroundService(this, intent)
        }
    }

    private fun requestIgnoreBatteryOptimization() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            openAppDetails()
            return
        }
        runCatching {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        }.onFailure { openAppDetails() }
    }

    private fun openAppDetails() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        }
    }

    private data class Destination(val label: String, val icon: ImageVector)
}
