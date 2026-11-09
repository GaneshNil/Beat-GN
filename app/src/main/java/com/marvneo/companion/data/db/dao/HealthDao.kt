package com.marvneo.companion.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.marvneo.companion.data.db.entity.AlarmRecord
import com.marvneo.companion.data.db.entity.BloodOxygenRecord
import com.marvneo.companion.data.db.entity.BloodPressureRecord
import com.marvneo.companion.data.db.entity.HeartRateRecord
import com.marvneo.companion.data.db.entity.SleepRecord
import com.marvneo.companion.data.db.entity.StepRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthDao {

    // ---- Steps ----
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStep(record: StepRecord): Long

    @Query("SELECT * FROM steps_record ORDER BY timestamp DESC LIMIT 1")
    fun getLatestSteps(): Flow<StepRecord?>

    @Query("SELECT * FROM steps_record WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    fun getStepsBetween(start: Long, end: Long): Flow<List<StepRecord>>

    // ---- Heart rate ----
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHeartRate(record: HeartRateRecord): Long

    @Query("SELECT * FROM heart_rate_record ORDER BY timestamp DESC LIMIT 100")
    fun getRecentHeartRates(): Flow<List<HeartRateRecord>>

    @Query("SELECT * FROM heart_rate_record WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    fun getHeartRatesBetween(start: Long, end: Long): Flow<List<HeartRateRecord>>

    @Query("SELECT COUNT(*) FROM heart_rate_record WHERE bpm = :bpm AND timestamp BETWEEN :from AND :to")
    suspend fun countHeartRateInWindow(bpm: Int, from: Long, to: Long): Int

    // ---- SpO2 ----
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBloodOxygen(record: BloodOxygenRecord): Long

    @Query("SELECT * FROM blood_oxygen_record ORDER BY timestamp DESC LIMIT 100")
    fun getRecentBloodOxygen(): Flow<List<BloodOxygenRecord>>

    @Query("SELECT * FROM blood_oxygen_record WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    fun getBloodOxygenBetween(start: Long, end: Long): Flow<List<BloodOxygenRecord>>

    @Query("SELECT COUNT(*) FROM blood_oxygen_record WHERE percentage = :percentage AND timestamp BETWEEN :from AND :to")
    suspend fun countBloodOxygenInWindow(percentage: Int, from: Long, to: Long): Int

    // ---- Blood pressure ----
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBloodPressure(record: BloodPressureRecord): Long

    @Query("SELECT * FROM blood_pressure_record ORDER BY timestamp DESC LIMIT 100")
    fun getRecentBloodPressure(): Flow<List<BloodPressureRecord>>

    @Query("SELECT * FROM blood_pressure_record WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    fun getBloodPressureBetween(start: Long, end: Long): Flow<List<BloodPressureRecord>>

    @Query("SELECT COUNT(*) FROM blood_pressure_record WHERE systolic = :systolic AND diastolic = :diastolic AND timestamp BETWEEN :from AND :to")
    suspend fun countBloodPressureInWindow(systolic: Int, diastolic: Int, from: Long, to: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM blood_pressure_record)")
    fun hasAnyBloodPressure(): Flow<Boolean>

    // ---- Sleep ----
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSleep(record: SleepRecord): Long

    @Query("SELECT * FROM sleep_record WHERE endTime BETWEEN :start AND :end ORDER BY endTime ASC")
    fun getSleepBetween(start: Long, end: Long): Flow<List<SleepRecord>>

    // ---- Alarms ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlarm(alarm: AlarmRecord)

    @Query("SELECT * FROM alarm_record ORDER BY id ASC")
    fun getAllAlarms(): Flow<List<AlarmRecord>>

    @Query("SELECT * FROM alarm_record WHERE id = :id")
    suspend fun getAlarm(id: Int): AlarmRecord?

    @Query("UPDATE alarm_record SET syncState = :syncState, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateAlarmSyncState(id: Int, syncState: String, updatedAt: Long)

    @Query("DELETE FROM alarm_record WHERE id = :id")
    suspend fun deleteAlarm(id: Int)
}
