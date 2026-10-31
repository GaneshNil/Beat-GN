package com.marvneo.companion.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_messages")
data class VoiceMessageRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val sender: String, // "user" or "gemini"
    val text: String,
    val toolExecuted: String? = null,
    val status: String = "completed"
)
