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

    // Steps
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStep(record: StepRecord)

    @Query("SELECT * FROM steps_record ORDER BY timestamp DESC LIMIT 1")
    fun getLatestSteps(): Flow<StepRecord?>

    // Heart Rate
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHeartRate(record: HeartRateRecord)

    @Query("SELECT * FROM heart_rate_record ORDER BY timestamp DESC LIMIT 100")
    fun getRecentHeartRates(): Flow<List<HeartRateRecord>>

    @Query("SELECT * FROM heart_rate_record WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    fun getHeartRatesBetween(start: Long, end: Long): Flow<List<HeartRateRecord>>

    @Query("SELECT AVG(bpm) FROM heart_rate_record WHERE timestamp >= :since AND bpm > 0")
    fun getAvgHeartRateSince(since: Long): Flow<Double?>

    @Query("SELECT MIN(bpm) FROM heart_rate_record WHERE timestamp >= :since AND bpm > 0")
    fun getMinHeartRateSince(since: Long): Flow<Int?>

    @Query("SELECT MAX(bpm) FROM heart_rate_record WHERE timestamp >= :since AND bpm > 0")
    fun getMaxHeartRateSince(since: Long): Flow<Int?>

    @Query("SELECT COUNT(*) FROM heart_rate_record WHERE timestamp >= :since AND bpm > 0")
    fun getHeartRateCountSince(since: Long): Flow<Int>

    // Blood Oxygen (SpO2)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBloodOxygen(record: BloodOxygenRecord)

    @Query("SELECT * FROM blood_oxygen_record ORDER BY timestamp DESC LIMIT 100")
    fun getRecentBloodOxygen(): Flow<List<BloodOxygenRecord>>

    @Query("SELECT * FROM blood_oxygen_record WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    fun getBloodOxygenBetween(start: Long, end: Long): Flow<List<BloodOxygenRecord>>

    @Query("SELECT AVG(percentage) FROM blood_oxygen_record WHERE timestamp >= :since AND percentage > 0")
    fun getAvgBloodOxygenSince(since: Long): Flow<Double?>

    @Query("SELECT MIN(percentage) FROM blood_oxygen_record WHERE timestamp >= :since AND percentage > 0")
    fun getMinBloodOxygenSince(since: Long): Flow<Int?>

    @Query("SELECT MAX(percentage) FROM blood_oxygen_record WHERE timestamp >= :since AND percentage > 0")
    fun getMaxBloodOxygenSince(since: Long): Flow<Int?>

    @Query("SELECT COUNT(*) FROM blood_oxygen_record WHERE timestamp >= :since AND percentage > 0")
    fun getBloodOxygenCountSince(since: Long): Flow<Int>

    // Blood Pressure
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBloodPressure(record: BloodPressureRecord)

    @Query("SELECT * FROM blood_pressure_record ORDER BY timestamp DESC LIMIT 100")
    fun getRecentBloodPressure(): Flow<List<BloodPressureRecord>>

    @Query("SELECT * FROM blood_pressure_record WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    fun getBloodPressureBetween(start: Long, end: Long): Flow<List<BloodPressureRecord>>

    @Query("SELECT AVG(systolic) FROM blood_pressure_record WHERE timestamp >= :since AND systolic > 0")
    fun getAvgSystolicSince(since: Long): Flow<Double?>

    @Query("SELECT AVG(diastolic) FROM blood_pressure_record WHERE timestamp >= :since AND diastolic > 0")
    fun getAvgDiastolicSince(since: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM blood_pressure_record WHERE timestamp >= :since AND systolic > 0")
    fun getBloodPressureCountSince(since: Long): Flow<Int>

    // Sleep
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleep(record: SleepRecord)

    @Query("SELECT * FROM sleep_record ORDER BY endTime DESC LIMIT 7")
    fun getWeeklySleep(): Flow<List<SleepRecord>>

    // Alarms
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: AlarmRecord)

    @Query("SELECT * FROM alarm_record ORDER BY id ASC")
    fun getAllAlarms(): Flow<List<AlarmRecord>>

    @Query("DELETE FROM alarm_record WHERE id = :id")
    suspend fun deleteAlarm(id: Int)
}
