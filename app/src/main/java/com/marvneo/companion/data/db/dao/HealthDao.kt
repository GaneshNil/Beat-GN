package com.marvneo.companion.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.marvneo.companion.data.db.entity.BloodOxygenRecord
import com.marvneo.companion.data.db.entity.HeartRateRecord
import com.marvneo.companion.data.db.entity.SleepRecord
import com.marvneo.companion.data.db.entity.StepRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStep(record: StepRecord)

    @Query("SELECT * FROM steps_record ORDER BY timestamp DESC LIMIT 1")
    fun getLatestSteps(): Flow<StepRecord?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHeartRate(record: HeartRateRecord)

    @Query("SELECT * FROM heart_rate_record ORDER BY timestamp DESC LIMIT 50")
    fun getRecentHeartRates(): Flow<List<HeartRateRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBloodOxygen(record: BloodOxygenRecord)

    @Query("SELECT * FROM blood_oxygen_record ORDER BY timestamp DESC LIMIT 50")
    fun getRecentBloodOxygen(): Flow<List<BloodOxygenRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleep(record: SleepRecord)

    @Query("SELECT * FROM sleep_record ORDER BY endTime DESC LIMIT 7")
    fun getWeeklySleep(): Flow<List<SleepRecord>>
}

