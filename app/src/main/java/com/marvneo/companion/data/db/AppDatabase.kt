package com.marvneo.companion.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.marvneo.companion.data.db.dao.HealthDao
import com.marvneo.companion.data.db.dao.VoiceDao
import com.marvneo.companion.data.db.entity.AlarmRecord
import com.marvneo.companion.data.db.entity.BloodOxygenRecord
import com.marvneo.companion.data.db.entity.BloodPressureRecord
import com.marvneo.companion.data.db.entity.HeartRateRecord
import com.marvneo.companion.data.db.entity.SleepRecord
import com.marvneo.companion.data.db.entity.StepRecord
import com.marvneo.companion.data.db.entity.VoiceMessageRecord

@Database(
    entities = [
        StepRecord::class,
        HeartRateRecord::class,
        BloodOxygenRecord::class,
        BloodPressureRecord::class,
        SleepRecord::class,
        VoiceMessageRecord::class,
        AlarmRecord::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun healthDao(): HealthDao
    abstract fun voiceDao(): VoiceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v1 -> v2 shipped as an empty migration in the previous build; kept for databases
         * already on disk so no user data is dropped.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No structural change was made between these versions.
            }
        }

        /**
         * v2 -> v3 introduced blood pressure and alarm tables.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `blood_pressure_record` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `systolic` INTEGER NOT NULL,
                        `diastolic` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `alarm_record` (
                        `id` INTEGER NOT NULL,
                        `hour` INTEGER NOT NULL,
                        `minute` INTEGER NOT NULL,
                        `repeatDays` INTEGER NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `label` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * v3 -> v4: alarm acknowledgement state + transcript session identity.
         * Additive only: existing health and transcript rows are preserved.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `alarm_record` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'PENDING'"
                )
                db.execSQL(
                    "ALTER TABLE `alarm_record` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `voice_messages` ADD COLUMN `sessionId` TEXT"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "marv_neo_health.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
