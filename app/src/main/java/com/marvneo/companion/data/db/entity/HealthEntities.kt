package com.marvneo.companion.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "steps_record")
data class StepRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val steps: Int,
    val calories: Int,
    val distanceMeters: Int
)

@Entity(tableName = "heart_rate_record")
data class HeartRateRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val bpm: Int
)

@Entity(tableName = "blood_oxygen_record")
data class BloodOxygenRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val percentage: Int
)

/**
 * Blood-pressure readings are only ever written when the watch itself returns a
 * systolic/diastolic pair. The table existing does NOT mean the hardware supports BP.
 */
@Entity(tableName = "blood_pressure_record")
data class BloodPressureRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val systolic: Int,
    val diastolic: Int
)

@Entity(tableName = "sleep_record")
data class SleepRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val totalMinutes: Int,
    val deepMinutes: Int,
    val lightMinutes: Int,
    val remMinutes: Int
)

/**
 * Alarm slots mirrored from the phone. [syncState] is the honest record of what the watch
 * has acknowledged:
 *  - PENDING  : stored on the phone, not yet acknowledged by the watch
 *  - SYNCED   : watch acknowledged the write in this session
 *  - FAILED   : last write attempt timed out / errored
 * "Pending" is never displayed as "synced".
 */
@Entity(tableName = "alarm_record")
data class AlarmRecord(
    @PrimaryKey val id: Int, // 0..4 slot on the watch
    val hour: Int,
    val minute: Int,
    val repeatDays: Int,
    val isEnabled: Boolean,
    val label: String,
    @ColumnInfo(defaultValue = "PENDING") val syncState: String = AlarmSyncState.PENDING,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L
)

object AlarmSyncState {
    const val PENDING = "PENDING"
    const val SYNCED = "SYNCED"
    const val FAILED = "FAILED"
}
