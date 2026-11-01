package com.marvneo.companion

import android.app.Application
import com.marvneo.companion.data.assistant.AssistantTranscriptRepository
import com.marvneo.companion.data.ble.MarvNeoBleManager
import com.marvneo.companion.data.db.AppDatabase
import com.marvneo.companion.data.repository.HealthRepository

class MarvNeoApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var healthRepository: HealthRepository
        private set

    lateinit var bleManager: MarvNeoBleManager
        private set

    lateinit var transcripts: AssistantTranscriptRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        healthRepository = HealthRepository(database.healthDao())
        transcripts = AssistantTranscriptRepository(database.voiceDao())
        bleManager = MarvNeoBleManager(this, healthRepository, database.healthDao())
    }
}
