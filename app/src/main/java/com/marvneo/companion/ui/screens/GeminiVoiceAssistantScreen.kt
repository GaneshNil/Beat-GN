package com.marvneo.companion.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marvneo.companion.data.ai.GeminiVoiceAssistantManager
import com.marvneo.companion.data.db.AppDatabase
import com.marvneo.companion.data.db.entity.VoiceMessageRecord
import com.marvneo.companion.ui.components.LiquidGlassCard
import com.marvneo.companion.ui.components.LiquidGlassPill
import com.marvneo.companion.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GeminiVoiceAssistantScreen(
    voiceManager: GeminiVoiceAssistantManager,
    database: AppDatabase,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val isListening by voiceManager.isListening.collectAsState()
    val isSpeaking by voiceManager.isSpeaking.collectAsState()
    val isProcessing by voiceManager.isProcessing.collectAsState()
    val liveText by voiceManager.liveSpokenText.collectAsState()
    val scoActive by voiceManager.bluetoothScoActive.collectAsState()

    val messages by database.voiceDao().getAllMessages().collectAsState(initial = emptyList())
    var textInput by remember { mutableStateOf("") }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Screen Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Gemini Watch Agent",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "Autonomous smartwatch AI with Bluetooth SCO audio",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            if (messages.isNotEmpty()) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            database.voiceDao().clearHistory()
                        }
                    }
                ) {
                    Icon(Icons.Default.DeleteSweep, "Clear History", tint = TextSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bluetooth Audio SCO Status Pill
        LiquidGlassPill(
            color = if (scoActive) PrimaryElectric else SleepViolet,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                if (scoActive) Icons.Default.HeadsetMic else Icons.Default.BluetoothAudio,
                contentDescription = null,
                tint = if (scoActive) PrimaryElectric else SleepViolet,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (scoActive) "Watch Mic & Speaker Active (Bluetooth HFP)" else "Bluetooth Audio Link Ready",
                fontSize = 12.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Conversation History List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SmartToy, null, tint = SleepViolet.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No voice commands yet.", color = TextSecondary, fontSize = 14.sp)
                            Text(
                                "Tap the mic and say 'Find my watch' or 'What is my heart rate?'",
                                color = TextTertiary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                items(messages) { msg ->
                    VoiceMessageBubble(msg)
                }
            }

            if (isProcessing) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SurfaceCardElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SleepViolet.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = SleepViolet)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Gemini is reasoning...", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Live Voice & Input Control Bar
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            glowColor = if (isListening) PrimaryElectric else SleepViolet,
            glowAlpha = if (isListening) 0.25f else 0.08f
        ) {
            if (isListening || liveText.isNotBlank()) {
                Text(
                    text = liveText.ifBlank { "Listening to speech..." },
                    color = if (isListening) PrimaryElectric else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Voice Recognition Trigger Button
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (isListening) HeartRateCrimson else PrimaryElectric)
                        .clickable {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            if (isListening) {
                                voiceManager.stopListening()
                            } else {
                                voiceManager.startListening()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = DarkBackground,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Text Input fallback
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Ask Gemini or give watch command...", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            val prompt = textInput.trim()
                            textInput = ""
                            voiceManager.processUserPrompt(prompt)
                        }
                    },
                    enabled = textInput.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, "Send", tint = if (textInput.isNotBlank()) PrimaryElectric else TextTertiary)
                }
            }
        }
    }
}

@Composable
fun VoiceMessageBubble(message: VoiceMessageRecord) {
    val isUser = message.sender == "user"
    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) PrimaryElectric.copy(alpha = 0.2f) else SurfaceCardElevated,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isUser) PrimaryElectric.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f)
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "You" else "Gemini Agent",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (isUser) PrimaryElectric else SleepViolet
                    )
                    Text(timeStr, fontSize = 9.sp, color = TextTertiary)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(message.text, fontSize = 13.sp, color = TextPrimary)

                // Tool Execution Receipt Tag
                if (!message.toolExecuted.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = StepsEmerald.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StepsEmerald.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Bolt, null, tint = StepsEmerald, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Action Executed: ${message.toolExecuted}",
                                color = StepsEmerald,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

