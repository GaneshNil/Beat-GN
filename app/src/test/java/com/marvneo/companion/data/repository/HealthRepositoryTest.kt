package com.marvneo.companion.data.repository

import com.marvneo.companion.data.db.dao.HealthDao
import com.marvneo.companion.data.db.entity.AlarmRecord
import com.marvneo.companion.data.db.entity.BloodOxygenRecord
import com.marvneo.companion.data.db.entity.BloodPressureRecord
import com.marvneo.companion.data.db.entity.HeartRateRecord
import com.marvneo.companion.data.db.entity.SleepRecord
import com.marvneo.companion.data.db.entity.StepRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class HealthRepositoryTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private var now = 1_700_000_000_000L

    private fun repository(dao: HealthDao): HealthRepository =
        HealthRepository(dao = dao, clock = { now }, zone = zone)

    @Test
    fun `an identical blood-pressure pair inside five seconds is a duplicate`() = runBlocking {
        val dao = FakeHealthDao()
        val repo = repository(dao)

        assertEquals(
            HealthRepository.WriteResult.STORED,
            repo.recordBloodPressure(120, 80, at = now)
        )
        assertEquals(
            HealthRepository.WriteResult.DUPLICATE,
            repo.recordBloodPressure(120, 80, at = now + 1_000)
        )
        assertEquals(1, dao.bloodPressure.size)
    }

    @Test
    fun `identical blood pressure after five seconds is stored again`() = runBlocking {
        val dao = FakeHealthDao()
        val repo = repository(dao)

        repo.recordBloodPressure(120, 80, at = now)
        assertEquals(
            HealthRepository.WriteResult.STORED,
            repo.recordBloodPressure(120, 80, at = now + 5_001)
        )
        assertEquals(2, dao.bloodPressure.size)
    }

    @Test
    fun `a different blood-pressure pair inside the window is stored`() = runBlocking {
        val dao = FakeHealthDao()
        val repo = repository(dao)

        repo.recordBloodPressure(120, 80, at = now)
        assertEquals(
            HealthRepository.WriteResult.STORED,
            repo.recordBloodPressure(118, 78, at = now + 1_000)
        )
        assertEquals(2, dao.bloodPressure.size)
    }

    @Test
    fun `implausible blood pressure is rejected before it reaches the database`() = runBlocking {
        val dao = FakeHealthDao()
        val repo = repository(dao)

        assertEquals(
            HealthRepository.WriteResult.INVALID,
            repo.recordBloodPressure(80, 80, at = now)
        )
        assertEquals(
            HealthRepository.WriteResult.INVALID,
            repo.recordBloodPressure(59, 40, at = now)
        )
        assertEquals(
            HealthRepository.WriteResult.INVALID,
            repo.recordBloodPressure(120, 161, at = now)
        )
        assertTrue(dao.bloodPressure.isEmpty())
    }

    @Test
    fun `blood pressure ever recorded is false until a reading is stored`() = runBlocking {
        val dao = FakeHealthDao()
        val repo = repository(dao)

        assertFalse(repo.bloodPressureEverRecorded.first())
        repo.recordBloodPressure(120, 80, at = now)
        assertTrue(repo.bloodPressureEverRecorded.first())
    }

    @Test
    fun `heart-rate dedupe uses the same five second window`() = runBlocking {
        val dao = FakeHealthDao()
        val repo = repository(dao)

        assertEquals(
            HealthRepository.WriteResult.STORED,
            repo.recordHeartRate(72, at = now)
        )
        assertEquals(
            HealthRepository.WriteResult.DUPLICATE,
            repo.recordHeartRate(72, at = now + 2_000)
        )
        assertEquals(
            HealthRepository.WriteResult.INVALID,
            repo.recordHeartRate(24, at = now + 10_000)
        )
        assertEquals(1, dao.heartRates.size)
        assertNotNull(dao.heartRates.single())
    }

    /** Minimal in-memory DAO. Only the paths used by [HealthRepository] do real work. */
    private class FakeHealthDao : HealthDao {

        val heartRates = mutableListOf<HeartRateRecord>()
        val bloodOxygen = mutableListOf<BloodOxygenRecord>()
        val bloodPressure = mutableListOf<BloodPressureRecord>()
        val alarms = mutableListOf<AlarmRecord>()
        private val everBloodPressure = MutableStateFlow(false)

        override suspend fun insertStep(record: StepRecord): Long = 0L
        override fun getLatestSteps(): Flow<StepRecord?> = flowOf(null)
        override fun getStepsBetween(start: Long, end: Long): Flow<List<StepRecord>> = flowOf(emptyList())

        override suspend fun insertHeartRate(record: HeartRateRecord): Long {
            heartRates.add(record)
            return record.id
        }

        override fun getRecentHeartRates(): Flow<List<HeartRateRecord>> = flowOf(heartRates.toList())
        override fun getHeartRatesBetween(start: Long, end: Long): Flow<List<HeartRateRecord>> =
            flowOf(heartRates.filter { it.timestamp in start..end })

        override suspend fun countHeartRateInWindow(bpm: Int, from: Long, to: Long): Int =
            heartRates.count { it.bpm == bpm && it.timestamp in from..to }

        override suspend fun insertBloodOxygen(record: BloodOxygenRecord): Long {
            bloodOxygen.add(record)
            return record.id
        }

        override fun getRecentBloodOxygen(): Flow<List<BloodOxygenRecord>> = flowOf(bloodOxygen.toList())
        override fun getBloodOxygenBetween(start: Long, end: Long): Flow<List<BloodOxygenRecord>> =
            flowOf(bloodOxygen.filter { it.timestamp in start..end })

        override suspend fun countBloodOxygenInWindow(percentage: Int, from: Long, to: Long): Int =
            bloodOxygen.count { it.percentage == percentage && it.timestamp in from..to }

        override suspend fun insertBloodPressure(record: BloodPressureRecord): Long {
            bloodPressure.add(record)
            everBloodPressure.value = true
            return record.id
        }

        override fun getRecentBloodPressure(): Flow<List<BloodPressureRecord>> =
            flowOf(bloodPressure.toList())

        override fun getBloodPressureBetween(start: Long, end: Long): Flow<List<BloodPressureRecord>> =
            flowOf(bloodPressure.filter { it.timestamp in start..end })

        override suspend fun countBloodPressureInWindow(
            systolic: Int,
            diastolic: Int,
            from: Long,
            to: Long
        ): Int = bloodPressure.count {
            it.systolic == systolic && it.diastolic == diastolic && it.timestamp in from..to
        }

        override fun hasAnyBloodPressure(): Flow<Boolean> = everBloodPressure

        override suspend fun insertSleep(record: SleepRecord): Long = 0L
        override fun getSleepBetween(start: Long, end: Long): Flow<List<SleepRecord>> = flowOf(emptyList())

        override suspend fun upsertAlarm(alarm: AlarmRecord) {
            alarms.removeAll { it.id == alarm.id }
            alarms.add(alarm)
        }

        override fun getAllAlarms(): Flow<List<AlarmRecord>> = flowOf(alarms.toList())
        override suspend fun getAlarm(id: Int): AlarmRecord? = alarms.firstOrNull { it.id == id }

        override suspend fun updateAlarmSyncState(id: Int, syncState: String, updatedAt: Long) {
            val index = alarms.indexOfFirst { it.id == id }
            if (index >= 0) {
                alarms[index] = alarms[index].copy(syncState = syncState, updatedAt = updatedAt)
            }
        }

        override suspend fun deleteAlarm(id: Int) {
            alarms.removeAll { it.id == id }
        }
    }
}
