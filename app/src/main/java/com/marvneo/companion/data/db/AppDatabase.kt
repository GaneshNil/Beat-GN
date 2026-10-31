package com.marvneo.companion.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.marvneo.companion.data.db.dao.HealthDao
import com.marvneo.companion.data.db.dao.VoiceDao
import com.marvneo.companion.data.db.entity.BloodOxygenRecord
import com.marvneo.companion.data.db.entity.HeartRateRecord
import com.marvneo.companion.data.db.entity.SleepRecord
import com.marvneo.companion.data.db.entity.StepRecord
import com.marvneo.companion.data.db.entity.VoiceMessageRecord

@Database(
    entities = [
        StepRecord::class,
        HeartRateRecord::class,
        BloodOxygenRecord::class,
        SleepRecord::class,
        VoiceMessageRecord::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun healthDao(): HealthDao
    abstract fun voiceDao(): VoiceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "marv_neo_health.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
