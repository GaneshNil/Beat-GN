package com.marvneo.companion.domain.report

import com.marvneo.companion.data.db.entity.BloodOxygenRecord
import com.marvneo.companion.data.db.entity.BloodPressureRecord
import com.marvneo.companion.data.db.entity.HeartRateRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Reporting periods, with explicitly documented boundaries. */
enum class ReportPeriod {
    TODAY,
    LAST_7_DAYS,
    THIS_MONTH
}

/**
 * A concrete time window. Boundaries are computed in the device's local time zone so that
 * "today" means the user's calendar day, not a 24-hour offset from UTC.
 */
data class PeriodWindow(
    val startMillis: Long,
    val endMillis: Long,
    val title: String,
    val description: String
)

object ReportWindows {

    fun of(period: ReportPeriod, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): PeriodWindow {
        val today: LocalDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return when (period) {
            ReportPeriod.TODAY -> PeriodWindow(
                startMillis = today.atStartOfDay(zone).toInstant().toEpochMilli(),
                endMillis = nowMillis,
                title = "Today",
                description = "Since local midnight"
            )

            ReportPeriod.LAST_7_DAYS -> PeriodWindow(
                startMillis = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli(),
                endMillis = nowMillis,
                title = "Last 7 days",
                description = "Calendar days: today plus the 6 preceding days"
            )

            ReportPeriod.THIS_MONTH -> PeriodWindow(
                startMillis = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                endMillis = nowMillis,
                title = "This month",
                description = "Calendar month, from the 1st at 00:00"
            )
        }
    }
}

/** Aggregate statistics calculated only from valid readings. Missing data is never zero. */
data class MetricStats(
    val count: Int = 0,
    val average: Double? = null,
    val minimum: Int? = null,
    val maximum: Int? = null
) {
    val hasData: Boolean get() = count > 0 && average != null
}

data class DailyPoint(
    val dayStartMillis: Long,
    val label: String,
    val heartRateAverage: Double?,
    val spo2Average: Double?,
    val sampleCount: Int
)

data class HealthReport(
    val period: ReportPeriod,
    val window: PeriodWindow,
    val heartRate: MetricStats,
    val spo2: MetricStats,
    val bloodPressure: MetricStats,
    val bloodPressureSamples: Int,
    val daily: List<DailyPoint>,
    val heartRateRecords: List<HeartRateRecord>,
    val spo2Records: List<BloodOxygenRecord>,
    val bloodPressureRecords: List<BloodPressureRecord>
) {
    val totalSamples: Int get() = heartRate.count + spo2.count
    /** BP is only reported as supported when the watch actually produced readings. */
    val bloodPressureSupported: Boolean get() = bloodPressureSamples > 0
}

/**
 * Pure aggregation logic. Kept free of Android dependencies so it can be unit-tested and so
 * the same rules are used by every screen.
 *
 * Validity rules (values outside these ranges are treated as sensor errors and excluded):
 *  - heart rate 25..250 bpm
 *  - SpO2       50..100 %
 *  - BP         systolic 60..255, diastolic 30..160, systolic > diastolic
 */
object HealthAggregator {

    private const val DEDUPE_WINDOW_MS = 3_000L

    fun aggregate(
        period: ReportPeriod,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        heartRates: List<HeartRateRecord>,
        bloodOxygen: List<BloodOxygenRecord>,
        bloodPressure: List<BloodPressureRecord>
    ): HealthReport {
        val window = ReportWindows.of(period, nowMillis, zone)

        val hr = validHeartRates(heartRates).filter { it.timestamp in window.startMillis..window.endMillis }
        val spo2 = validBloodOxygen(bloodOxygen).filter { it.timestamp in window.startMillis..window.endMillis }
        val bp = validBloodPressure(bloodPressure).filter { it.timestamp in window.startMillis..window.endMillis }

        return HealthReport(
            period = period,
            window = window,
            heartRate = stats(hr.map { it.bpm }),
            spo2 = stats(spo2.map { it.percentage }),
            bloodPressure = stats(bp.map { it.systolic }),
            bloodPressureSamples = bp.size,
            daily = dailyPoints(window, zone, hr, spo2),
            heartRateRecords = hr.sortedByDescending { it.timestamp },
            spo2Records = spo2.sortedByDescending { it.timestamp },
            bloodPressureRecords = bp.sortedByDescending { it.timestamp }
        )
    }

    fun validHeartRates(records: List<HeartRateRecord>): List<HeartRateRecord> =
        dedupe(records.sortedBy { it.timestamp }, { it.timestamp }, { it.bpm })
            .filter { it.bpm in 25..250 }

    fun validBloodOxygen(records: List<BloodOxygenRecord>): List<BloodOxygenRecord> =
        dedupe(records.sortedBy { it.timestamp }, { it.timestamp }, { it.percentage })
            .filter { it.percentage in 50..100 }

    fun validBloodPressure(records: List<BloodPressureRecord>): List<BloodPressureRecord> =
        dedupe(records.sortedBy { it.timestamp }, { it.timestamp }, { "${it.systolic}/${it.diastolic}" })
            .filter {
                it.systolic in 60..255 && it.diastolic in 30..160 && it.systolic > it.diastolic
            }

    fun stats(values: List<Int>): MetricStats {
        if (values.isEmpty()) return MetricStats()
        return MetricStats(
            count = values.size,
            average = values.average(),
            minimum = values.minOrNull(),
            maximum = values.maxOrNull()
        )
    }

    /**
     * Builds one bucket per calendar day in the window (so charts show gaps honestly).
     */
    private fun dailyPoints(
        window: PeriodWindow,
        zone: ZoneId,
        heartRates: List<HeartRateRecord>,
        bloodOxygen: List<BloodOxygenRecord>
    ): List<DailyPoint> {
        val startDate = Instant.ofEpochMilli(window.startMillis).atZone(zone).toLocalDate()
        val endDate = Instant.ofEpochMilli(window.endMillis).atZone(zone).toLocalDate()
        val labelFormat = if (startDate == endDate) {
            DateTimeFormatter.ofPattern("HH:mm")
        } else {
            DateTimeFormatter.ofPattern("d MMM")
        }

        val days = generateSequence(startDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(endDate) }
            .toList()

        val hrByDay = heartRates.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
        val spo2ByDay = bloodOxygen.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }

        return days.map { day ->
            val dayHr = hrByDay[day].orEmpty()
            val daySpo2 = spo2ByDay[day].orEmpty()
            DailyPoint(
                dayStartMillis = day.atStartOfDay(zone).toInstant().toEpochMilli(),
                label = if (days.size == 1) "Today" else day.format(labelFormat),
                heartRateAverage = dayHr.takeIf { it.isNotEmpty() }?.map { it.bpm }?.average(),
                spo2Average = daySpo2.takeIf { it.isNotEmpty() }?.map { it.percentage }?.average(),
                sampleCount = dayHr.size + daySpo2.size
            )
        }
    }

    /**
     * Drops repeated values that arrive within [DEDUPE_WINDOW_MS] of each other, which is what
     * happens when a watch re-sends the same notification or a retry delivers twice.
     */
    private fun <T> dedupe(
        sorted: List<T>,
        timestamp: (T) -> Long,
        value: (T) -> Any
    ): List<T> {
        val out = ArrayList<T>(sorted.size)
        for (item in sorted) {
            val previous = out.lastOrNull()
            val duplicate = previous != null &&
                value(previous) == value(item) &&
                timestamp(item) - timestamp(previous) <= DEDUPE_WINDOW_MS
            if (!duplicate) out.add(item)
        }
        return out
    }
}
