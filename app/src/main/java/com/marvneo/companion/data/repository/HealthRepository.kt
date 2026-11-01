package com.marvneo.companion.data.repository

import com.marvneo.companion.data.db.dao.HealthDao
import com.marvneo.companion.data.db.entity.BloodOxygenRecord
import com.marvneo.companion.data.db.entity.BloodPressureRecord
import com.marvneo.companion.data.db.entity.HeartRateRecord
import com.marvneo.companion.domain.report.HealthAggregator
import com.marvneo.companion.domain.report.HealthReport
import com.marvneo.companion.domain.report.ReportPeriod
import com.marvneo.companion.domain.report.ReportWindows
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Single source of truth for health data. The watch manager only calls the `record*` methods
 * when a real measurement has arrived, and screens only read from here.
 */
class HealthRepository(private val dao: HealthDao) {

    enum class WriteResult { STORED, DUPLICATE, INVALID }

    private val refreshSignal = MutableStateFlow(0L)

    fun requestRefresh() {
        refreshSignal.value = System.currentTimeMillis()
    }

    suspend fun recordHeartRate(bpm: Int, at: Long = System.currentTimeMillis()): WriteResult {
        if (bpm !in 25..250) return WriteResult.INVALID
        if (dao.countHeartRateInWindow(bpm, at - 5_000, at + 5_000) > 0) return WriteResult.DUPLICATE
        dao.insertHeartRate(HeartRateRecord(timestamp = at, bpm = bpm))
        requestRefresh()
        return WriteResult.STORED
    }

    suspend fun recordBloodOxygen(percentage: Int, at: Long = System.currentTimeMillis()): WriteResult {
        if (percentage !in 50..100) return WriteResult.INVALID
        if (dao.countBloodOxygenInWindow(percentage, at - 5_000, at + 5_000) > 0) return WriteResult.DUPLICATE
        dao.insertBloodOxygen(BloodOxygenRecord(timestamp = at, percentage = percentage))
        requestRefresh()
        return WriteResult.STORED
    }

    suspend fun recordBloodPressure(
        systolic: Int,
        diastolic: Int,
        at: Long = System.currentTimeMillis()
    ): WriteResult {
        val valid = systolic in 60..260 && diastolic in 30..160 && systolic > diastolic
        if (!valid) return WriteResult.INVALID
        dao.insertBloodPressure(BloodPressureRecord(timestamp = at, systolic = systolic, diastolic = diastolic))
        requestRefresh()
        return WriteResult.STORED
    }

    /**
     * Recomputes the window on every refresh so the "today" boundary moves with the clock,
     * and re-subscribes to the DAO flows for that window.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun report(period: ReportPeriod): Flow<HealthReport> =
        refreshSignal.flatMapLatest {
            val window = ReportWindows.of(period, System.currentTimeMillis())
            combine(
                dao.getHeartRatesBetween(window.startMillis, window.endMillis),
                dao.getBloodOxygenBetween(window.startMillis, window.endMillis),
                dao.getBloodPressureBetween(window.startMillis, window.endMillis)
            ) { heartRates, bloodOxygen, bloodPressure ->
                HealthAggregator.aggregate(
                    period = period,
                    nowMillis = window.endMillis,
                    heartRates = heartRates,
                    bloodOxygen = bloodOxygen,
                    bloodPressure = bloodPressure
                )
            }
        }
}
