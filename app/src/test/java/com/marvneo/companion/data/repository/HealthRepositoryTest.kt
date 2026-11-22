package com.marvneo.companion.data.repository

import com.marvneo.companion.data.db.dao.HealthDao
import com.marvneo.companion.data.db.entity.AlarmRecord
import com.marvneo.companion.data.db.entity.AlarmSyncState
import com.marvneo.companion.data.db.entity.BloodOxygenRecord
import com.marvneo.companion.data.db.entity.BloodPressureRecord
import com.marvneo.companion.data.db.entity.HeartRateRecord
import com.marvneo.companion.data.db.entity.SleepRecord
import com.marvneo.companion.data.db.entity.StepRecord
import com.marvneo.companion.domain.model.CommandOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.LocalDate

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

    @Test
    fun `simultaneous copies of a reading are stored once`() = runBlocking {
        val dao = FakeHealthDao()
        val repo = repository(dao)
        val first = async { repo.recordBloodOxygen(96) }
        val second = async { repo.recordBloodOxygen(96) }

        assertEquals(setOf(HealthRepository.WriteResult.STORED, HealthRepository.WriteResult.DUPLICATE),
            setOf(first.await(), second.await()))
        assertEquals(1, dao.bloodOxygen.size)
    }

    @Test
    fun `steps preserve changed fields and midnight even when the count is identical`() = runBlocking {
        val dao = FakeHealthDao()
        val midnight = LocalDate.of(2026, 11, 17).atStartOfDay(zone).toInstant().toEpochMilli()
        val repo = repository(dao)
        repo.recordSteps(0, 0, 0, midnight - 3_000)
        repo.recordSteps(0, 0, 0, midnight - 2_000)
        repo.recordSteps(0, 0, 1, midnight - 1_000)
        repository(dao).recordSteps(0, 0, 1, midnight)
        repository(dao).recordSteps(0, 0, 1, midnight + 1)
        assertEquals(listOf(midnight - 3_000, midnight - 1_000, midnight), dao.stepRows.map { it.timestamp })
    }

    @Test
    fun `partial activity snapshots preserve missing fields and changes to active minutes`() = runBlocking {
        val dao = FakeHealthDao()
        val repo = repository(dao)
        repo.recordSteps(0, null, null, now)
        repo.recordSteps(0, null, null, now + 1)
        repo.recordSteps(null, null, null, now + 2, activeMinutes = 3)
        repo.recordSteps(null, null, null, now + 3, activeMinutes = 4)
        repo.recordSteps(null, null, null, now + 4)
        assertEquals(listOf(
            StepRecord(timestamp = now, steps = 0, calories = null, distanceMeters = null),
            StepRecord(timestamp = now + 2, steps = null, calories = null, distanceMeters = null, activeMinutes = 3),
            StepRecord(timestamp = now + 3, steps = null, calories = null, distanceMeters = null, activeMinutes = 4)
        ), dao.stepRows)
    }

    @Test
    fun `transport exceptions leave the phone alarm saved with a retryable failed status`() = runBlocking {
        val dao = FakeHealthDao()
        val repo = AlarmRepository(dao, { throw java.io.IOException("link lost") }, scope = this)
        repo.save(AlarmRecord(0, 8, 0, 127, true, "Alarm"), newSlot = true, sendWhenReady = true)
        repo.busy.first { !it }
        assertEquals(AlarmSyncState.FAILED, dao.alarms.single().syncState)
        assertTrue(repo.error.value.orEmpty().contains("watch"))
    }

    @Test
    fun `recording the same pulse just after local midnight keeps the first reading of the day`() = runBlocking {
        val dao = FakeHealthDao()
        val midnight = LocalDate.of(2026, 11, 17).atStartOfDay(zone).toInstant().toEpochMilli()
        val repo = repository(dao)
        repo.recordHeartRate(72, midnight - 1_000)
        assertEquals(HealthRepository.WriteResult.STORED, repo.recordHeartRate(72, midnight + 1_000))
        assertEquals(2, dao.heartRates.size)
    }

    @Test
    fun `saving offline and sending later uses the latest alarm and records transport only`() = runBlocking {
        val dao = FakeHealthDao()
        val writes = mutableListOf<AlarmRecord>()
        val repo = AlarmRepository(dao, { calls ->
            writes += calls
            CommandOutcome.AwaitingAcknowledgement()
        }, scope = this)
        val alarm = AlarmRecord(4, 7, 30, 127, true, "Alarm")
        repo.save(alarm, newSlot = true, sendWhenReady = false)
        repo.busy.first { !it }
        assertTrue(writes.isEmpty())
        assertEquals(0, dao.alarms.single().id)
        assertEquals(AlarmSyncState.PENDING, dao.alarms.single().syncState)

        repo.save(dao.alarms.single().copy(hour = 8), newSlot = false, sendWhenReady = false)
        repo.busy.first { !it }
        repo.send(0)
        repo.busy.first { !it }
        assertEquals(8, writes.single().hour)
        assertEquals(AlarmSyncState.SENT, dao.alarms.single().syncState)
    }

    @Test
    fun `failed alarm send is retained and an enabled alarm needs at least one repeat day`() = runBlocking {
        val dao = FakeHealthDao()
        val repo = AlarmRepository(dao, { CommandOutcome.Failed("Watch is not ready") }, scope = this)
        val alarm = AlarmRecord(0, 8, 0, 0, true, "Alarm")
        repo.save(alarm, newSlot = true, sendWhenReady = true)
        repo.busy.first { !it }
        assertTrue(dao.alarms.isEmpty())

        repo.save(alarm.copy(repeatDays = 127), newSlot = true, sendWhenReady = true)
        repo.busy.first { !it }
        assertEquals(AlarmSyncState.FAILED, dao.alarms.single().syncState)
        assertEquals("Watch is not ready", repo.error.value)
        repo.delete(0)
        repo.busy.first { !it }
        assertTrue(dao.alarms.isEmpty())
    }

    /** Minimal in-memory DAO. Only the paths used by [HealthRepository] do real work. */
    private class FakeHealthDao : HealthDao {

        val heartRates = mutableListOf<HeartRateRecord>()
        val bloodOxygen = mutableListOf<BloodOxygenRecord>()
        val bloodPressure = mutableListOf<BloodPressureRecord>()
        val alarms = mutableListOf<AlarmRecord>()
        val stepRows = mutableListOf<StepRecord>()
        private val everBloodPressure = MutableStateFlow(false)

        override suspend fun insertStep(record: StepRecord): Long {
            stepRows.add(record)
            return record.id
        }
        override fun getLatestSteps(): Flow<StepRecord?> = flowOf(stepRows.maxByOrNull { it.timestamp })
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
            yield()
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

        override fun hasValidBloodPressure(): Flow<Boolean> = everBloodPressure

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
