package com.marvneo.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.marvneo.companion.data.assistant.AssistantTranscriptRepository
import com.marvneo.companion.ui.components.AppCard
import com.marvneo.companion.ui.components.Badge
import com.marvneo.companion.ui.components.EmptyState
import com.marvneo.companion.ui.components.InlineNotice
import com.marvneo.companion.ui.components.NoticeTone
import com.marvneo.companion.ui.components.SectionHeader
import com.marvneo.companion.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Transcript view.
 *
 * This screen deliberately has no API key, no text generation, no microphone capture and no
 * notification scraping. Rows carried forward from earlier builds are labelled as legacy entries.
 */
@Composable
fun AssistantScreen(transcripts: AssistantTranscriptRepository) {
    val messages by transcripts.messages.collectAsStateWithLifecycle(initialValue = emptyList())
    val clearing by transcripts.clearing.collectAsStateWithLifecycle()
    val clearError by transcripts.clearError.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.lg,
            bottom = Spacing.xxl
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            SectionHeader(
                title = "Assistant",
                subtitle = "Local transcript history",
                trailing = {
                    Badge(
                        text = if (messages.isEmpty()) "empty" else "${messages.size}",
                        color = if (messages.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            )
        }

        item {
            InlineNotice(
                title = "Watch transcript delivery unavailable",
                message = "The known Bluetooth interface provides recording controls. Entries from earlier builds have a legacy label.",
                tone = NoticeTone.INFO
            )
        }

        item {
            AppCard {
                Text(
                    text = "No cloud processing",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "This app has no API key and makes no network requests for voice. " +
                        "Nothing you say is sent anywhere by this application.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        clearError?.let { reason ->
            item { InlineNotice(title = "Could not clear history", message = reason, tone = NoticeTone.WARNING) }
        }
        if (messages.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.RecordVoiceOver,
                    title = "No transcripts",
                    message = "No watch transcripts are stored on this phone."
                )
            }
        } else {
            items(messages, key = { it.id }) { message ->
                AppCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Badge(
                            text = if (message.status != "captured") "legacy entry"
                            else if (message.sender == "user") "you" else "watch",
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
                                .format(Date(message.timestamp)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            item {
                OutlinedButton(enabled = !clearing, onClick = { confirmClear = true }) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Clear history")
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear local transcript history?") },
            confirmButton = {
                TextButton(enabled = !clearing, onClick = {
                    confirmClear = false
                    transcripts.clear()
                }) { Text("Clear history") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}
