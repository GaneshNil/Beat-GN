package com.marvneo.companion.data.assistant

import com.marvneo.companion.data.db.dao.VoiceDao
import com.marvneo.companion.data.db.entity.VoiceMessageRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Storage for assistant transcripts that the watch actually delivered to this phone.
 *
 * There is deliberately no text generation, no API key, and no synthetic "invocation" entry
 * here. If the watch never sends a transcript, this repository stays empty and the UI says so.
 */
class AssistantTranscriptRepository(
    private val voiceDao: VoiceDao,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    val messages: Flow<List<VoiceMessageRecord>> = voiceDao.getAllMessages()
    val messageCount: Flow<Int> = voiceDao.countMessages()
    private val _clearing = MutableStateFlow(false)
    val clearing = _clearing.asStateFlow()
    private val _clearError = MutableStateFlow<String?>(null)
    val clearError = _clearError.asStateFlow()

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

    fun clear() {
        if (!_clearing.compareAndSet(false, true)) return
        _clearError.value = null
        scope.launch {
            try {
                voiceDao.clearHistory()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _clearError.value = "Could not clear transcript history on this phone. Try again."
            } finally {
                _clearing.value = false
            }
        }
    }

    enum class Sender(val wireValue: String) {
        USER("user"),
        ASSISTANT("assistant")
    }
}
