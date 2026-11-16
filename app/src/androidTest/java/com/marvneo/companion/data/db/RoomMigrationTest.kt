package com.marvneo.companion.data.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.marvneo.companion.data.db.entity.AlarmRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RoomMigrationTest {
    @Test
    fun versionOneOpensThroughRoomAndKeepsOldHealthRows() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = "migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(file)
        path.parentFile?.mkdirs()
        try {
            SQLiteDatabase.openOrCreateDatabase(path, null).use { legacy ->
                // Schema from the first shipped database in commit 56a5308.
                legacy.execSQL(
                    "CREATE TABLE steps_record (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "timestamp INTEGER NOT NULL, steps INTEGER NOT NULL, calories INTEGER NOT NULL, " +
                        "distanceMeters INTEGER NOT NULL)"
                )
                legacy.execSQL(
                    "CREATE TABLE heart_rate_record (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "timestamp INTEGER NOT NULL, bpm INTEGER NOT NULL)"
                )
                legacy.execSQL(
                    "CREATE TABLE blood_oxygen_record (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "timestamp INTEGER NOT NULL, percentage INTEGER NOT NULL)"
                )
                legacy.execSQL(
                    "CREATE TABLE sleep_record (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "startTime INTEGER NOT NULL, endTime INTEGER NOT NULL, totalMinutes INTEGER NOT NULL, " +
                        "deepMinutes INTEGER NOT NULL, lightMinutes INTEGER NOT NULL, remMinutes INTEGER NOT NULL)"
                )
                legacy.execSQL("INSERT INTO heart_rate_record (timestamp, bpm) VALUES (1000, 72)")
                legacy.version = 1
            }

            fun open(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, file)
                .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
                .build()

            val alarm = AlarmRecord(0, 7, 30, 127, true, "Test alarm")
            val migrated = open()
            try {
                assertEquals(72, migrated.healthDao().getHeartRatesBetween(0, 2000).first().single().bpm)
                assertTrue(migrated.voiceDao().getAllMessages().first().isEmpty())
                migrated.healthDao().upsertAlarm(alarm)
            } finally {
                migrated.close()
            }

            val reopened = open()
            try {
                assertEquals(alarm, reopened.healthDao().getAllAlarms().first().single())
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(file)
        }
    }
}
