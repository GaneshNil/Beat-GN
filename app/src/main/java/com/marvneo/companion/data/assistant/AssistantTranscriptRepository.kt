package com.marvneo.companion.data.assistant

import com.marvneo.companion.data.db.dao.VoiceDao
import com.marvneo.companion.data.db.entity.VoiceMessageRecord
import kotlinx.coroutines.flow.Flow

/**
 * Storage for assistant transcripts that the watch actually delivered to this phone.
 *
 * There is deliberately no text generation, no API key, and no synthetic "invocation" entry
 * here. If the watch never sends a transcript, this repository stays empty and the UI says so.
 */
class AssistantTranscriptRepository(private val voiceDao: VoiceDao) {

    val messages: Flow<List<VoiceMessageRecord>> = voiceDao.getAllMessages()
    val messageCount: Flow<Int> = voiceDao.countMessages()

    /**
     * Records one captured message. Only call this from a code path that decoded the message
     * from data received over the watch link.
     */
    suspend fun recordCaptured(
        sender: Sender,
        text: String,
        sessionId: String?,
        status: String = "captured"
    ): Boolean {
        if (text.isBlank()) return false
        voiceDao.insertMessage(
            VoiceMessageRecord(
                timestamp = System.currentTimeMillis(),
                sender = sender.wireValue,
                text = text.trim(),
                sessionId = sessionId,
                status = status
            )
        )
        return true
    }

    suspend fun clear() = voiceDao.clearHistory()

    enum class Sender(val wireValue: String) {
        USER("user"),
        ASSISTANT("assistant")
    }
}
