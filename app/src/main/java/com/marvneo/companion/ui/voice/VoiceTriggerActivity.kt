package com.marvneo.companion.ui.voice

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.marvneo.companion.MainActivity
import com.marvneo.companion.MarvNeoApp

/**
 * Intercepts Voice Assistant events triggered by the smartwatch (AT+BVRA / ACTION_VOICE_COMMAND)
 * and opens the in-app Gemini Voice Agent with Bluetooth SCO audio link.
 */
class VoiceTriggerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Launch MainActivity and switch directly to Voice Assistant History tab (index 4)
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_TAB", 4)
            putExtra("TRIGGER_VOICE_LISTENING", true)
        }
        startActivity(launchIntent)

        // Start listening immediately through the watch microphone
        val voiceManager = (application as MarvNeoApp).voiceManager
        voiceManager.startListening()

        finish()
    }
}
