package com.marvneo.companion.data.repository

import com.marvneo.companion.data.db.dao.HealthDao
import com.marvneo.companion.data.db.entity.BloodOxygenRecord
import com.marvneo.companion.data.db.entity.BloodPressureRecord
import com.marvneo.companion.data.db.entity.HeartRateRecord
import com.marvneo.companion.data.db.entity.StepRecord
import com.marvneo.companion.domain.report.HealthAggregator
import com.marvneo.companion.domain.report.HealthReport
import com.marvneo.companion.domain.report.ReportPeriod
import com.marvneo.companion.domain.report.ReportWindows
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

/**
 * Single source of truth for health data. The watch manager only calls the `record*` methods
 * when a real measurement has arrived, and screens only read from here.
 */
class HealthRepository(
    private val dao: HealthDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId? = null
) {

    enum class WriteResult { STORED, DUPLICATE, INVALID }

    private val refreshSignal = MutableStateFlow(0L)
    private val writeMutex = Mutex()
    private var latestStep: StepRecord? = null

    /**
     * True when this install has stored a valid blood-pressure pair, including after a restart.
     */
    val bloodPressureEverRecorded: Flow<Boolean> = dao.hasValidBloodPressure()

    fun requestRefresh() {
        refreshSignal.update { it + 1 }
    }

    suspend fun recordSteps(
        steps: Int?,
        distance: Int?,
        calories: Int?,
        at: Long = clock(),
        activeMinutes: Int? = null
    ) =
        writeMutex.withLock {
            if (steps == null && distance == null && calories == null && activeMinutes == null) return@withLock
            val current = StepRecord(
                timestamp = at,
                steps = steps,
                distanceMeters = distance,
                calories = calories,
                activeMinutes = activeMinutes
            )
            val previous = latestStep ?: dao.getLatestSteps().first()
            val localZone = zone ?: ZoneId.systemDefault()
            if (previous != null && previous.copy(id = 0, timestamp = at) == current &&
                localDate(previous.timestamp, localZone) == localDate(at, localZone)
            ) {
                latestStep = previous
                return@withLock
            }
            dao.insertStep(current)
            latestStep = current
        }

    suspend fun recordHeartRate(bpm: Int, at: Long = clock()): WriteResult = writeMutex.withLock {
        if (bpm !in 25..250) return@withLock WriteResult.INVALID
        val window = dedupeWindow(at)
        if (dao.countHeartRateInWindow(bpm, window.first, window.last) > 0) return@withLock WriteResult.DUPLICATE
        dao.insertHeartRate(HeartRateRecord(timestamp = at, bpm = bpm))
        requestRefresh()
        WriteResult.STORED
    }

    suspend fun recordBloodOxygen(percentage: Int, at: Long = clock()): WriteResult = writeMutex.withLock {
        if (percentage !in 50..100) return@withLock WriteResult.INVALID
        val window = dedupeWindow(at)
        if (dao.countBloodOxygenInWindow(percentage, window.first, window.last) > 0) {
            return@withLock WriteResult.DUPLICATE
        }
        dao.insertBloodOxygen(BloodOxygenRecord(timestamp = at, percentage = percentage))
        requestRefresh()
        WriteResult.STORED
    }

    suspend fun recordBloodPressure(
        systolic: Int,
        diastolic: Int,
        at: Long = clock()
    ): WriteResult = writeMutex.withLock {
        val valid = systolic in 60..255 && diastolic in 30..160 && systolic > diastolic
        if (!valid) return@withLock WriteResult.INVALID
        val window = dedupeWindow(at)
        if (dao.countBloodPressureInWindow(systolic, diastolic, window.first, window.last) > 0) {
            return@withLock WriteResult.DUPLICATE
        }
        dao.insertBloodPressure(BloodPressureRecord(timestamp = at, systolic = systolic, diastolic = diastolic))
        requestRefresh()
        WriteResult.STORED
    }

    /**
     * Recomputes the window on every refresh so the "today" boundary moves with the clock,
     * and re-subscribes to the DAO flows for that window. The clock and zone are injected so tests
     * can exercise DST and time-zone changes without touching the machine's settings.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun report(period: ReportPeriod): Flow<HealthReport> =
        combine(refreshSignal, calendarClock) { _, _ -> Unit }.flatMapLatest {
            val zone = zone ?: ZoneId.systemDefault()
            val window = ReportWindows.of(period, clock(), zone)
            combine(
                dao.getHeartRatesBetween(window.startMillis, window.endMillis),
                dao.getBloodOxygenBetween(window.startMillis, window.endMillis),
                dao.getBloodPressureBetween(window.startMillis, window.endMillis)
            ) { heartRates, bloodOxygen, bloodPressure ->
                HealthAggregator.aggregate(
                    period = period,
                    nowMillis = window.endMillis,
                    zone = zone,
                    heartRates = heartRates,
                    bloodOxygen = bloodOxygen,
                    bloodPressure = bloodPressure
                )
            }
        }

    private val calendarClock = flow {
        while (true) {
            val localZone = zone ?: ZoneId.systemDefault()
            emit(localZone to localDate(clock(), localZone))
            delay(30_000)
        }
    }.distinctUntilChanged()

    private fun localDate(timestamp: Long, zone: ZoneId) =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()

    private fun dedupeWindow(at: Long): LongRange {
        val localZone = zone ?: ZoneId.systemDefault()
        val day = localDate(at, localZone)
        val start = day.atStartOfDay(localZone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(localZone).toInstant().toEpochMilli() - 1
        return maxOf(start, at - 5_000)..minOf(end, at + 5_000)
    }
}
