package com.marvneo.companion.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marvneo.companion.data.ble.MarvNeoBleManager
import com.marvneo.companion.data.ble.protocol.WatchFaceConverter
import com.marvneo.companion.domain.model.ConnectionState
import com.marvneo.companion.domain.model.WatchTheme
import com.marvneo.companion.ui.components.LiquidGlassCard
import com.marvneo.companion.ui.components.LiquidGlassPill
import com.marvneo.companion.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WatchThemeStoreScreen(
    bleManager: MarvNeoBleManager,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val connectionState by bleManager.connectionState.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Theme Store, 1 = Photo Studio

    // Flashing state
    var isFlashing by remember { mutableStateOf(false) }
    var flashProgress by remember { mutableFloatStateOf(0f) }
    var flashStatusText by remember { mutableStateOf("") }

    val themes = remember {
        listOf(
            WatchTheme(
                id = "cyberpunk_hud",
                name = "Cyberpunk HUD 2077",
                category = "Cyber",
                description = "Neon telemetry matrix with battery ring, heart rate pulse and step arc.",
                primaryColorHex = 0xFF00F0FF,
                secondaryColorHex = 0xFFFF003C,
                style = "Digital"
            ),
            WatchTheme(
                id = "chronos_titanium",
                name = "Chronos Titanium",
                category = "Luxury Analog",
                description = "Classic Swiss chronometer with brushed titanium bezel and sub-dials.",
                primaryColorHex = 0xFFE2E8F0,
                secondaryColorHex = 0xFF38BDF8,
                style = "Analog"
            ),
            WatchTheme(
                id = "astro_orbit",
                name = "Astro Celestial Orbit",
                category = "Cosmic",
                description = "Orbital planetary watch face tracking solar progression and lunar phases.",
                primaryColorHex = 0xFFA855F7,
                secondaryColorHex = 0xFF38BDF8,
                style = "Digital"
            ),
            WatchTheme(
                id = "neon_synthwave",
                name = "Neon Synthwave 80s",
                category = "Retro",
                description = "Vibrant synthwave sunset grid with retro glowing digital numbers.",
                primaryColorHex = 0xFFEC4899,
                secondaryColorHex = 0xFF8B5CF6,
                style = "Digital"
            ),
            WatchTheme(
                id = "minimal_obsidian",
                name = "Minimal Obsidian OLED",
                category = "Minimalist",
                description = "Ultra pure dark OLED theme designed for 2x battery preservation.",
                primaryColorHex = 0xFF10B981,
                secondaryColorHex = 0xFF64748B,
                style = "Minimal"
            ),
            WatchTheme(
                id = "tactical_field",
                name = "Tactical Field Special",
                category = "Sports & Field",
                description = "Rugged military outdoor layout with compass heading and altimeter.",
                primaryColorHex = 0xFFF59E0B,
                secondaryColorHex = 0xFF10B981,
                style = "Field"
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Screen Header
        Text(
            text = "Watch Face & Theme Store",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
        Text(
            text = "Hardware native 240x284 RGB565 dial compiler",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Segmented Tab Switcher
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = PrimaryElectric,
            divider = {}
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Theme Gallery", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Custom Photo Studio", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Flashing Progress Indicator Card
        if (isFlashing) {
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), glowColor = PrimaryElectric, glowAlpha = 0.2f) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(flashStatusText, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("${(flashProgress * 100).toInt()}%", color = PrimaryElectric, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { flashProgress },
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
            // Theme Store Gallery
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(themes) { theme ->
                    ThemeStoreCard(
                        theme = theme,
                        isWatchConnected = connectionState is ConnectionState.Connected,
                        onApplyTheme = {
                            coroutineScope.launch {
                                isFlashing = true
                                flashStatusText = "Compiling ${theme.name}..."
                                flashProgress = 0.05f

                                val binary = withContext(Dispatchers.Default) {
                                    generateThemeBitmap(theme)
                                }

                                flashStatusText = "Uploading dial to Marv Neo..."
                                val chunks = WatchFaceConverter.createChunks(binary, chunkSize = 240)
                                bleManager.flashBinaryChunks(chunks) { progress ->
                                    flashProgress = progress
                                }

                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                flashStatusText = "Theme Applied Successfully!"
                                kotlinx.coroutines.delay(1500)
                                isFlashing = false
                            }
                        }
                    )
                }
            }
        } else {
            // Custom Photo Studio
            PhotoDialStudio(
                bleManager = bleManager,
                isWatchConnected = connectionState is ConnectionState.Connected,
                onStartFlashing = { status ->
                    isFlashing = true
                    flashStatusText = status
                },
                onProgress = { p -> flashProgress = p },
                onFinished = {
                    flashStatusText = "Custom Dial Flashed!"
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(1500)
                        isFlashing = false
                    }
                }
            )
        }
    }
}

@Composable
fun ThemeStoreCard(
    theme: WatchTheme,
    isWatchConnected: Boolean,
    onApplyTheme: () -> Unit
) {
    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = Color(theme.primaryColorHex),
        glowAlpha = 0.15f
    ) {
        // Dial Canvas Simulation Box (240x284 simulated preview)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(theme.primaryColorHex).copy(alpha = 0.35f),
                            Color(theme.secondaryColorHex).copy(alpha = 0.15f),
                            DarkBackground
                        )
                    )
                )
                .border(1.dp, Color(theme.primaryColorHex).copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = SimpleDateFormat("10:42", Locale.getDefault()).format(Date()),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(theme.primaryColorHex)
                )
                Text(
                    text = "OCT 31 • THU",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(theme.secondaryColorHex)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DirectionsWalk, null, tint = StepsEmerald, modifier = Modifier.size(12.dp))
                    Text(" 8,420", fontSize = 10.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Default.Favorite, null, tint = HeartRateCrimson, modifier = Modifier.size(12.dp))
                    Text(" 74", fontSize = 10.sp, color = TextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(theme.name, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp, maxLines = 1)
        Text(theme.category, color = TextSecondary, fontSize = 11.sp)

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onApplyTheme,
            modifier = Modifier.fillMaxWidth(),
            enabled = isWatchConnected,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(theme.primaryColorHex)),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            Text(
                text = if (isWatchConnected) "Apply to Watch" else "Connect Watch",
                color = DarkBackground,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PhotoDialStudio(
    bleManager: MarvNeoBleManager,
    isWatchConnected: Boolean,
    onStartFlashing: (String) -> Unit,
    onProgress: (Float) -> Unit,
    onFinished: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var clockPosition by remember { mutableStateOf("Top") } // Top, Center, Bottom
    var clockColor by remember { mutableStateOf(PrimaryElectric) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("Customize Watch Wallpaper", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(modifier = Modifier.height(10.dp))

            // Pick Photo Action
            OutlinedButton(
                onClick = { photoPickerLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (selectedImageUri != null) "Change Selected Photo" else "Select Photo from Gallery")
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Position & Color Controls
            Text("Digital Clock Position", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Top", "Center", "Bottom").forEach { pos ->
                    FilterChip(
                        selected = clockPosition == pos,
                        onClick = { clockPosition = pos },
                        label = { Text(pos) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Text Color", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(PrimaryElectric, StepsEmerald, HeartRateCrimson, AccentAmber, Color.White).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { clockColor = color }
                            .border(
                                2.dp,
                                if (clockColor == color) Color.White else Color.Transparent,
                                CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = {
                    coroutineScope.launch {
                        onStartFlashing("Compiling custom dial...")
                        val dummyBitmap = Bitmap.createBitmap(240, 284, Bitmap.Config.RGB_565)
                        val c = Canvas(dummyBitmap)
                        c.drawColor(android.graphics.Color.DKGRAY)

                        val p = Paint().apply {
                            this.color = android.graphics.Color.WHITE
                            this.textSize = 36f
                            this.isAntiAlias = true
                        }
                        val timeY = when (clockPosition) {
                            "Top" -> 60f
                            "Center" -> 140f
                            else -> 230f
                        }
                        c.drawText("10:42", 50f, timeY, p)

                        val binary = WatchFaceConverter.convertBitmapToDialBinary(dummyBitmap)
                        val chunks = WatchFaceConverter.createChunks(binary, 240)

                        onStartFlashing("Uploading to watch...")
                        bleManager.flashBinaryChunks(chunks) { progress ->
                            onProgress(progress)
                        }
                        onFinished()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isWatchConnected,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryElectric)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = DarkBackground)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Build & Flash to Watch", color = DarkBackground, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Generates an RGB565 dial bitmap from a theme configuration.
 */
private fun generateThemeBitmap(theme: WatchTheme): ByteArray {
    val bitmap = Bitmap.createBitmap(240, 284, Bitmap.Config.RGB_565)
    val canvas = Canvas(bitmap)

    // Draw background gradient
    val paint = Paint().apply {
        isAntiAlias = true
    }
    paint.color = android.graphics.Color.parseColor(
        String.format("#%06X", 0xFFFFFF and theme.primaryColorHex.toInt())
    )
    canvas.drawRect(0f, 0f, 240f, 100f, paint)

    paint.color = android.graphics.Color.BLACK
    canvas.drawRect(0f, 100f, 240f, 284f, paint)

    paint.color = android.graphics.Color.WHITE
    paint.textSize = 42f
    canvas.drawText("10:42", 60f, 170f, paint)

    return WatchFaceConverter.convertBitmapToDialBinary(bitmap)
}

