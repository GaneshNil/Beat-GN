package com.marvneo.companion

import android.app.Application
import com.marvneo.companion.data.ble.MarvNeoBleManager
import com.marvneo.companion.data.db.AppDatabase

class MarvNeoApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var bleManager: MarvNeoBleManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        bleManager = MarvNeoBleManager(this)
    }
}

