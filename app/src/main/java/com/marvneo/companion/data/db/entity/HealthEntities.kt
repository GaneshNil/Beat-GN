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

