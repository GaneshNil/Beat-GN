package com.marvneo.companion.data.ai

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import com.marvneo.companion.data.ble.MarvNeoBleManager
import com.marvneo.companion.data.ble.protocol.CrrepaProtocol
import com.marvneo.companion.data.db.AppDatabase
import com.marvneo.companion.data.db.entity.VoiceMessageRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

@SuppressLint("MissingPermission")
class GeminiVoiceAssistantManager(
    private val context: Context,
    private val bleManager: MarvNeoBleManager,
    private val database: AppDatabase
) {
    private val tag = "GeminiVoiceAssistant"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    private val prefs = context.getSharedPreferences("marv_neo_prefs", Context.MODE_PRIVATE)

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _liveSpokenText = MutableStateFlow("")
    val liveSpokenText: StateFlow<String> = _liveSpokenText.asStateFlow()

    private val _bluetoothScoActive = MutableStateFlow(false)
    val bluetoothScoActive: StateFlow<Boolean> = _bluetoothScoActive.asStateFlow()

    init {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.getDefault()
                ttsReady = true
                Log.i(tag, "TextToSpeech initialized.")
            } else {
                Log.e(tag, "TextToSpeech initialization failed.")
            }
        }
    }

    fun setBluetoothScoEnabled(enabled: Boolean) {
        try {
            if (enabled) {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                _bluetoothScoActive.value = true
                Log.i(tag, "Bluetooth SCO activated.")
            } else {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                _bluetoothScoActive.value = false
                Log.i(tag, "Bluetooth SCO deactivated.")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error setting Bluetooth SCO", e)
        }
    }

    fun startListening() {
        if (_isListening.value) return

        scope.launch(Dispatchers.Main) {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    Log.e(tag, "Speech recognition is not available on this device.")
                    return@launch
                }

                setBluetoothScoEnabled(true)

                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _liveSpokenText.value = "Listening to your voice..."
                    }

                    override fun onBeginningOfSpeech() {
                        _liveSpokenText.value = "Hearing speech..."
                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _isListening.value = false
                    }

                    override fun onError(error: Int) {
                        Log.e(tag, "SpeechRecognizer error: $error")
                        _isListening.value = false
                        setBluetoothScoEnabled(false)
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            _liveSpokenText.value = text
                            processUserPrompt(text)
                        } else {
                            setBluetoothScoEnabled(false)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        partial?.firstOrNull()?.let {
                            _liveSpokenText.value = it
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(tag, "Error launching speech recognizer", e)
                _isListening.value = false
                setBluetoothScoEnabled(false)
            }
        }
    }

    fun stopListening() {
        scope.launch(Dispatchers.Main) {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e(tag, "Error stopping recognizer", e)
            }
            _isListening.value = false
        }
    }

    fun processUserPrompt(query: String) {
        scope.launch {
            _isProcessing.value = true

            // 1. Log user query in local Room DB
            database.voiceDao().insertMessage(
                VoiceMessageRecord(
                    timestamp = System.currentTimeMillis(),
                    sender = "user",
                    text = query
                )
            )

            // 2. Fetch API key from SharedPreferences
            val apiKey = prefs.getString("gemini_api_key", "")?.trim() ?: ""

            // 3. Process with Gemini API or local commands
            val result: Pair<String, String?> = if (apiKey.isBlank()) {
                executeLocalAgentIntent(query, isKeyConfigured = false)
            } else {
                callGeminiApi(apiKey, query)
            }

            val responseText = result.first
            val toolExecuted = result.second

            // 4. Log response in local Room DB
            database.voiceDao().insertMessage(
                VoiceMessageRecord(
                    timestamp = System.currentTimeMillis(),
                    sender = "gemini",
                    text = responseText,
                    toolExecuted = toolExecuted
                )
            )

            _isProcessing.value = false

            // 5. Speak answer over Bluetooth watch speaker
            speakResponse(responseText)
        }
    }

    private fun speakResponse(text: String) {
        if (!ttsReady || text.isBlank()) {
            setBluetoothScoEnabled(false)
            return
        }

        scope.launch(Dispatchers.Main) {
            _isSpeaking.value = true
            setBluetoothScoEnabled(true)

            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "MarvNeoTtsUtterance")
            delay(minOf(7000L, 80L * text.length))
            _isSpeaking.value = false
            setBluetoothScoEnabled(false)
        }
    }

    private suspend fun executeLocalAgentIntent(query: String, isKeyConfigured: Boolean = true): Pair<String, String?> {
        val lower = query.lowercase(Locale.ROOT)
        return when {
            lower.contains("find") || lower.contains("locate") || lower.contains("vibrate") -> {
                bleManager.sendCommand(CrrepaProtocol.createFindWatchPacket(true))
                Pair("I have signaled your Marv Neo. It is vibrating now.", "FindWatch")
            }
            lower.contains("heart rate") || lower.contains("pulse") || lower.contains("bpm") -> {
                val bpm = bleManager.heartRateFlow.value
                val msg = if (bpm > 0) "Your current heart rate is $bpm beats per minute."
                else "Starting live heart rate measurement on your watch."
                if (bpm == 0) bleManager.sendCommand(CrrepaProtocol.createHeartRateControlPacket(true))
                Pair(msg, "MeasureHeartRate")
            }
            lower.contains("blood oxygen") || lower.contains("spo2") || lower.contains("oxygen") -> {
                val spo2 = bleManager.spO2Flow.value
                val msg = if (spo2 > 0) "Your blood oxygen level is $spo2 percent."
                else "Triggering SpO2 sensor measurement on your watch."
                if (spo2 == 0) bleManager.sendCommand(CrrepaProtocol.createSpO2ControlPacket(true))
                Pair(msg, "MeasureSpO2")
            }
            lower.contains("step") || lower.contains("walk") || lower.contains("distance") -> {
                val steps = bleManager.stepsFlow.value
                val cal = bleManager.caloriesFlow.value
                Pair("You have walked $steps steps today, burning $cal kcal.", "QuerySteps")
            }
            lower.contains("battery") -> {
                val batt = bleManager.batteryFlow.value
                Pair("Your watch battery is at $batt percent.", "QueryBattery")
            }
            lower.contains("sync time") || lower.contains("what time is it") || lower.contains("clock") -> {
                bleManager.sendCommand(CrrepaProtocol.createTimeSyncPacket())
                Pair("Watch clock has been synchronized with your phone.", "SyncTime")
            }
            lower.contains("camera") || lower.contains("photo") || lower.contains("shutter") -> {
                bleManager.sendCommand(CrrepaProtocol.createCameraModePacket(true))
                Pair("Camera remote shutter is now active on your watch.", "CameraRemote")
            }
            else -> {
                if (!isKeyConfigured) {
                    Pair(
                        "Command recognized: \"$query\". Please configure your Gemini API Key in Settings for open conversational reasoning.",
                        null
                    )
                } else {
                    Pair(
                        "I processed your command: \"$query\". Watch telemetry: ${bleManager.stepsFlow.value} steps, ${bleManager.heartRateFlow.value} BPM.",
                        null
                    )
                }
            }
        }
    }

    /**
     * Calls Google Gemini API using the official gemini-1.5-flash endpoint.
     */
    private suspend fun callGeminiApi(apiKey: String, query: String): Pair<String, String?> = withContext(Dispatchers.IO) {
        try {
            // Use gemini-1.5-flash (official stable API endpoint)
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val currentSteps = bleManager.stepsFlow.value
            val currentHr = bleManager.heartRateFlow.value
            val currentBattery = bleManager.batteryFlow.value

            val promptText = """
                You are the autonomous voice AI agent for the user's beatXP Marv Neo smartwatch.
                Current Watch Telemetry: Steps: $currentSteps, Heart Rate: $currentHr BPM, Battery: $currentBattery%.
                You can execute physical watch actions by prefixing your reply with an action tag:
                [TOOL:FindWatch] - If asked to find, vibrate, or locate the watch.
                [TOOL:SyncTime] - If asked to sync time or clock.
                [TOOL:MeasureHR] - If asked to measure pulse or heart rate.
                [TOOL:CameraShutter] - If asked to take a photo.
                Keep spoken responses very concise (1 to 2 sentences) because they will be read aloud through the watch speaker.

                User command: $query
            """.trimIndent()

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", promptText))
                        })
                    })
                })
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val rawResponse = reader.readText()
                reader.close()

                val root = JSONObject(rawResponse)
                val candidates = root.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    val text = parts.getJSONObject(0).getString("text")

                    var tool: String? = null
                    var cleanText = text
                    if (text.contains("[TOOL:")) {
                        val start = text.indexOf("[TOOL:")
                        val end = text.indexOf("]", start)
                        if (start >= 0 && end > start) {
                            tool = text.substring(start + 6, end).trim()
                            cleanText = text.removeRange(start, end + 1).trim()
                            executeTool(tool)
                        }
                    }

                    Pair(cleanText, tool)
                } else {
                    executeLocalAgentIntent(query, isKeyConfigured = true)
                }
            } else {
                val errBody = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
                Log.e(tag, "Gemini API HTTP Error ($responseCode): $errBody")
                val cleanErrorMsg = try {
                    JSONObject(errBody).getJSONObject("error").getString("message")
                } catch (_: Exception) {
                    errBody.take(120)
                }
                Pair("Gemini API Error ($responseCode): $cleanErrorMsg", null)
            }
        } catch (e: Exception) {
            Log.e(tag, "Gemini API Connection Exception", e)
            Pair("Network Error: ${e.localizedMessage ?: "Failed to reach Gemini API"}", null)
        }
    }

    private suspend fun executeTool(tool: String) {
        when (tool) {
            "FindWatch" -> bleManager.sendCommand(CrrepaProtocol.createFindWatchPacket(true))
            "SyncTime" -> bleManager.sendCommand(CrrepaProtocol.createTimeSyncPacket())
            "MeasureHR" -> bleManager.sendCommand(CrrepaProtocol.createHeartRateControlPacket(true))
            "CameraShutter" -> bleManager.sendCommand(CrrepaProtocol.createCameraModePacket(true))
        }
    }

    fun cleanUp() {
        stopListening()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        setBluetoothScoEnabled(false)
    }
}
