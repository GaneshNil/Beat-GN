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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Transcript view.
 *
 * This screen deliberately has no API key, no text generation, no microphone capture and no
 * notification scraping. It only lists messages that arrived over the watch link. If nothing has
 * arrived, it says so and explains why.
 */
@Composable
fun AssistantScreen(transcripts: AssistantTranscriptRepository) {
    val messages by transcripts.messages.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

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
                subtitle = "Only transcripts the watch actually delivered",
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
                title = "What this screen can and cannot show",
                message = "The reference application's Bluetooth SDK exposes phone-to-watch " +
                    "recording controls (start recording, stop recording, ask a question) but no " +
                    "channel that delivers a recognised transcript back to the phone. Until that " +
                    "changes, this list stays empty. It is not filled with examples.",
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

        if (messages.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.RecordVoiceOver,
                    title = "No transcripts",
                    message = "The watch has not delivered any transcript to this phone. When it " +
                        "does, it will appear here with the time it arrived."
                )
            }
        } else {
            items(messages) { message ->
                AppCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Badge(
                            text = if (message.sender == "user") "you" else "watch",
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
                OutlinedButton(onClick = { scope.launch { transcripts.clear() } }) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Clear history")
                }
            }
        }
    }
}
