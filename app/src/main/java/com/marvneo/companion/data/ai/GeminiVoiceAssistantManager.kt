package com.marvneo.companion.data.ai

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.marvneo.companion.data.ble.MarvNeoBleManager
import com.marvneo.companion.data.db.AppDatabase
import com.marvneo.companion.data.db.entity.VoiceMessageRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@SuppressLint("MissingPermission")
class GeminiVoiceAssistantManager(
    private val context: Context,
    private val bleManager: MarvNeoBleManager,
    private val database: AppDatabase
) {
    private val tag = "VoiceAssistantManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _bluetoothScoActive = MutableStateFlow(false)
    val bluetoothScoActive: StateFlow<Boolean> = _bluetoothScoActive.asStateFlow()

    /**
     * Records a voice command or assistant response into the local database history.
     */
    fun recordMessage(sender: String, text: String, toolExecuted: String? = null, status: String = "completed") {
        if (text.isBlank()) return
        scope.launch {
            val record = VoiceMessageRecord(
                timestamp = System.currentTimeMillis(),
                sender = sender,
                text = text.trim(),
                toolExecuted = toolExecuted,
                status = status
            )
            database.voiceDao().insertMessage(record)
            Log.i(tag, "Logged voice message ($sender): $text")
        }
    }

    /**
     * Clears all recorded conversation logs.
     */
    fun clearHistory() {
        scope.launch {
            database.voiceDao().clearHistory()
        }
    }

    /**
     * Called when the watch physical voice button triggers AT+BVRA activation.
     */
    fun handleWatchVoiceTrigger() {
        Log.i(tag, "Smartwatch voice trigger (AT+BVRA) received. Watch invokes native Gemini via Bluetooth HFP.")
        // The watch invokes native Gemini directly via Bluetooth Classic SCO.
        // We log the voice activation event in the conversation history.
        recordMessage(
            sender = "watch",
            text = "Voice Assistant invoked from watch (AT+BVRA trigger)",
            toolExecuted = "Bluetooth HFP Handsfree Activation",
            status = "completed"
        )
    }

    fun startListening() {
        _isListening.value = true
        handleWatchVoiceTrigger()
    }

    fun stopListening() {
        _isListening.value = false
    }
}
