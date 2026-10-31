package com.marvneo.companion.data.db.entity

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

@Entity(tableName = "alarm_record")
data class AlarmRecord(
    @PrimaryKey val id: Int, // 0..2
    val hour: Int,
    val minute: Int,
    val repeatDays: Int,
    val isEnabled: Boolean,
    val label: String
)
