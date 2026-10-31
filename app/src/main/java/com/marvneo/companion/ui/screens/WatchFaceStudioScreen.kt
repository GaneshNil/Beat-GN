package com.marvneo.companion.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.marvneo.companion.data.ble.MarvNeoBleManager
import com.marvneo.companion.data.ble.protocol.WatchFaceConverter
import com.marvneo.companion.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WatchFaceStudioScreen(
    bleManager: MarvNeoBleManager,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val watchWidth = 240
    val watchHeight = 280

    var timeX by remember { mutableFloatStateOf(40f) }
    var timeY by remember { mutableFloatStateOf(60f) }
    var transferProgress by remember { mutableFloatStateOf(0f) }
    var isTransferring by remember { mutableStateOf(false) }

    val animatedTransferProgress by animateFloatAsState(
        targetValue = transferProgress,
        label = "TransferProgress"
    )

    // Generate preview bitmap
    val previewBitmap = remember(timeX, timeY) {
        val bmp = Bitmap.createBitmap(watchWidth, watchHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(android.graphics.Color.parseColor("#1E293B"))

        val paint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 38f
            isAntiAlias = true
            isFakeBoldText = true
        }
        canvas.drawText("10:09", timeX, timeY, paint)
        bmp
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Custom Watch Face Studio", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(modifier = Modifier.height(16.dp))

        // Watch Screen Mockup
        Box(
            modifier = Modifier
                .size(200.dp, 233.dp)
                .background(SurfaceCard, RoundedCornerShape(24.dp))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = previewBitmap.asImageBitmap(),
                contentDescription = "Watchface Preview",
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Clock Position Sliders
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Clock X Position: ${timeX.toInt()} px", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = timeX,
                    onValueChange = {
                        timeX = it
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    },
                    valueRange = 10f..180f
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("Clock Y Position: ${timeY.toInt()} px", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = timeY,
                    onValueChange = {
                        timeY = it
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    },
                    valueRange = 30f..250f
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isTransferring) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                LinearProgressIndicator(
                    progress = { animatedTransferProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = PrimaryElectric,
                    trackColor = SurfaceCardElevated
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Flashing Watch Face: ${(transferProgress * 100).toInt()}%", color = PrimaryElectric)
            }
        } else {
            Button(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    isTransferring = true
                    coroutineScope.launch {
                        val binaryPayload = WatchFaceConverter.convertBitmapToDialBinary(
                            bitmap = previewBitmap,
                            timeX = timeX.toInt().toShort(),
                            timeY = timeY.toInt().toShort()
                        )
                        val chunks = WatchFaceConverter.createChunks(binaryPayload)
                        for (i in chunks.indices) {
                            bleManager.sendCommand(chunks[i])
                            transferProgress = (i + 1).toFloat() / chunks.size
                            delay(35)
                        }
                        isTransferring = false
                        transferProgress = 0f
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryElectric)
            ) {
                Text("Flash Custom Watch Face", color = DarkBackground)
            }
        }
    }
}
