package com.marvneo.companion.ui.screens

import androidx.compose.foundation.background
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
import java.util.*

@Composable
fun VoiceAssistantHistoryScreen(
    voiceManager: GeminiVoiceAssistantManager,
    database: AppDatabase,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val messages by database.voiceDao().getAllMessages().collectAsState(initial = emptyList())
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE, MMM dd", Locale.getDefault()) }

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
                    text = "Voice Assistant History",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "Conversation transcript of watch & Gemini interactions",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            if (messages.isNotEmpty()) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        coroutineScope.launch {
                            voiceManager.clearHistory()
                        }
                    }
                ) {
                    Icon(Icons.Default.DeleteSweep, "Clear History", tint = TextSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Hardware & Protocol Reality Notice Pill
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            glowColor = SleepViolet,
            glowAlpha = 0.10f
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Info, null, tint = SleepViolet, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        "Watch Hardware Link: Bluetooth HFP Audio",
                        fontWeight = FontWeight.SemiBold,
                        color = SleepViolet,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "When you tap the Voice Assistant button on the Marv Neo, the watch sends an AT+BVRA command over Bluetooth Classic hands-free audio, invoking the native Google Gemini assistant directly on your phone. Textual transcript streaming over BLE GATT is governed by vendor firmware.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
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
                            .padding(top = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(SleepViolet.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Mic, null, tint = SleepViolet, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No Voice History Recorded", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Tap the voice assistant button on your Marv Neo watch to trigger Gemini.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    val isFromWatch = msg.sender.equals("watch", ignoreCase = true) || msg.sender.equals("user", ignoreCase = true)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (isFromWatch) Alignment.End else Alignment.Start
                    ) {
                        // Sender & Timestamp Tag
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 2.dp, start = 4.dp, end = 4.dp)
                        ) {
                            Icon(
                                if (isFromWatch) Icons.Default.Watch else Icons.Default.SmartToy,
                                null,
                                tint = if (isFromWatch) PrimaryElectric else SleepViolet,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isFromWatch) "Watch → Assistant" else "Assistant → Watch",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = timeFormat.format(Date(msg.timestamp)),
                                fontSize = 10.sp,
                                color = TextTertiary
                            )
                        }

                        // Message Bubble
                        Box(
                            modifier = Modifier
                                .widthIn(max = 300.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 14.dp,
                                        topEnd = 14.dp,
                                        bottomStart = if (isFromWatch) 14.dp else 2.dp,
                                        bottomEnd = if (isFromWatch) 2.dp else 14.dp
                                    )
                                )
                                .background(
                                    if (isFromWatch) Color(0x3300E5FF) else Color(0x337C4DFF)
                                )
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = msg.text,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )

                                if (!msg.toolExecuted.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LiquidGlassPill(color = StepsEmerald) {
                                        Text(
                                            text = msg.toolExecuted,
                                            color = StepsEmerald,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

