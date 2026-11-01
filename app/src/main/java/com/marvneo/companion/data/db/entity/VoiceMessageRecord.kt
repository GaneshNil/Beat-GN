package com.marvneo.companion.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A transcript event that the watch explicitly delivered to the phone.
 *
 * Nothing is written here unless the watch sent it. The app never synthesises
 * conversations, and it never treats "the watch has a microphone" as evidence that a
 * transcript is available.
 *
 * [sessionId] groups a recognised utterance with the assistant's reply when the watch
 * supplies a conversation identifier; otherwise it is null and messages are ungrouped.
 * [deliveryState] records the capture state reported by the transport (COMPLETE /
 * PARTIAL / UNVERIFIED).
 */
@Entity(tableName = "voice_messages")
data class VoiceMessageRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val sender: String, // "user" = recognised speech, "assistant" = reply
    val text: String,
    val toolExecuted: String? = null,
    val status: String = "completed",
    val sessionId: String? = null
)
