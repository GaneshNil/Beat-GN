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
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun healthDao(): HealthDao
    abstract fun voiceDao(): VoiceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v1 -> v2 added `voice_messages`. The previous build left this migration empty, so a v1
         * database failed v2 validation and then failed `ALTER TABLE voice_messages` at v3 -> v4.
         * The SQL is additive; every v1 health row survives.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.V1_TO_V2.forEach(db::execSQL)
            }
        }

        /**
         * v2 -> v3 introduced blood pressure and alarm tables.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.V2_TO_V3.forEach(db::execSQL)
            }
        }

        /**
         * v3 -> v4: alarm acknowledgement state + transcript session identity.
         * Additive only: existing health and transcript rows are preserved.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.V3_TO_V4.forEach(db::execSQL)
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
