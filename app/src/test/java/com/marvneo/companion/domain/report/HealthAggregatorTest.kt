package com.marvneo.companion.domain.report

import com.marvneo.companion.data.db.entity.BloodOxygenRecord
import com.marvneo.companion.data.db.entity.BloodPressureRecord
import com.marvneo.companion.data.db.entity.HeartRateRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Report logic tests. Windows are always checked against an explicit time zone so the results do
 * not depend on the machine running the tests.
 */
class HealthAggregatorTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun startOfDay(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(zone).toInstant().toEpochMilli()

    // ---------------------------------------------------------------- window boundaries

    @Test
    fun `today starts at local midnight, not 24 hours ago`() {
        val now = at(2026, 3, 9, 6, 30)
        val window = ReportWindows.of(ReportPeriod.TODAY, now, zone)
        assertEquals(startOfDay(2026, 3, 9), window.startMillis)
        assertEquals(now, window.endMillis)
    }

    @Test
    fun `seven day window covers today plus six preceding calendar days`() {
        val now = at(2026, 3, 9, 6, 30)
        val window = ReportWindows.of(ReportPeriod.LAST_7_DAYS, now, zone)
        assertEquals(startOfDay(2026, 3, 3), window.startMillis)
    }

    @Test
    fun `month window starts on the first at midnight`() {
        val now = at(2026, 3, 9, 6, 30)
        val window = ReportWindows.of(ReportPeriod.THIS_MONTH, now, zone)
        assertEquals(startOfDay(2026, 3, 1), window.startMillis)
    }

    @Test
    fun `month window survives a month rollover`() {
        val now = at(2026, 3, 1, 0, 5)
        val window = ReportWindows.of(ReportPeriod.THIS_MONTH, now, zone)
        assertEquals(startOfDay(2026, 3, 1), window.startMillis)
    }

    // ---------------------------------------------------------------- validity

    @Test
    fun `heart rate outside 25-250 is excluded`() {
        val records = listOf(
            HeartRateRecord(timestamp = at(2026, 3, 9, 9), bpm = 0),
            HeartRateRecord(timestamp = at(2026, 3, 9, 9, 1), bpm = 24),
            HeartRateRecord(timestamp = at(2026, 3, 9, 9, 2), bpm = 25),
            HeartRateRecord(timestamp = at(2026, 3, 9, 9, 3), bpm = 72),
            HeartRateRecord(timestamp = at(2026, 3, 9, 9, 4), bpm = 251)
        )
        val valid = HealthAggregator.validHeartRates(records)
        assertEquals(listOf(25, 72), valid.map { it.bpm })
    }

    @Test
    fun `spo2 outside 50-100 is excluded`() {
        val records = listOf(
            BloodOxygenRecord(timestamp = at(2026, 3, 9, 9), percentage = 49),
            BloodOxygenRecord(timestamp = at(2026, 3, 9, 9, 1), percentage = 50),
            BloodOxygenRecord(timestamp = at(2026, 3, 9, 9, 2), percentage = 100),
            BloodOxygenRecord(timestamp = at(2026, 3, 9, 9, 3), percentage = 101)
        )
        assertEquals(listOf(50, 100), HealthAggregator.validBloodOxygen(records).map { it.percentage })
    }

    @Test
    fun `blood pressure requires systolic above diastolic and sane ranges`() {
        val records = listOf(
            BloodPressureRecord(timestamp = at(2026, 3, 9, 9), systolic = 80, diastolic = 80),
            BloodPressureRecord(timestamp = at(2026, 3, 9, 9, 1), systolic = 120, diastolic = 80),
            BloodPressureRecord(timestamp = at(2026, 3, 9, 9, 2), systolic = 59, diastolic = 40),
            BloodPressureRecord(timestamp = at(2026, 3, 9, 9, 3), systolic = 261, diastolic = 80)
        )
        assertEquals(1, HealthAggregator.validBloodPressure(records).size)
    }

    // ---------------------------------------------------------------- dedupe

    @Test
    fun `identical value arriving twice within three seconds is counted once`() {
        val base = at(2026, 3, 9, 9)
        val records = listOf(
            HeartRateRecord(timestamp = base, bpm = 71),
            HeartRateRecord(timestamp = base + 1_000, bpm = 71),
            HeartRateRecord(timestamp = base + 2_999, bpm = 71)
        )
        assertEquals(1, HealthAggregator.validHeartRates(records).size)
    }

    @Test
    fun `identical value after the dedupe window is kept`() {
        val base = at(2026, 3, 9, 9)
        val records = listOf(
            HeartRateRecord(timestamp = base, bpm = 71),
            HeartRateRecord(timestamp = base + 3_500, bpm = 71)
        )
        assertEquals(2, HealthAggregator.validHeartRates(records).size)
    }

    @Test
    fun `different values close together are both kept`() {
        val base = at(2026, 3, 9, 9)
        val records = listOf(
            HeartRateRecord(timestamp = base, bpm = 71),
            HeartRateRecord(timestamp = base + 1_000, bpm = 72)
        )
        assertEquals(2, HealthAggregator.validHeartRates(records).size)
    }

    // ---------------------------------------------------------------- statistics

    @Test
    fun `stats report count, average, min and max`() {
        val stats = HealthAggregator.stats(listOf(60, 70, 80))
        assertEquals(3, stats.count)
        assertEquals(70.0, stats.average!!, 0.0001)
        assertEquals(60, stats.minimum)
        assertEquals(80, stats.maximum)
        assertTrue(stats.hasData)
    }

    @Test
    fun `empty stats have no average rather than zero`() {
        val stats = HealthAggregator.stats(emptyList())
        assertEquals(0, stats.count)
        assertNull(stats.average)
        assertFalse(stats.hasData)
    }

    @Test
    fun `aggregate excludes readings outside the window`() {
        val now = at(2026, 3, 9, 12)
        val report = HealthAggregator.aggregate(
            period = ReportPeriod.TODAY,
            nowMillis = now,
            zone = zone,
            heartRates = listOf(
                HeartRateRecord(timestamp = at(2026, 3, 8, 23, 59), bpm = 60),
                HeartRateRecord(timestamp = at(2026, 3, 9, 0, 0), bpm = 70)
            ),
            bloodOxygen = emptyList(),
            bloodPressure = emptyList()
        )
        assertEquals(1, report.heartRate.count)
        assertEquals(70, report.heartRate.minimum)
        assertEquals(0, report.spo2.count)
        assertFalse(report.bloodPressureSupported)
    }

    // ---------------------------------------------------------------- daily buckets

    @Test
    fun `seven day report has one bucket per calendar day including empty days`() {
        val now = at(2026, 3, 9, 12)
        val report = HealthAggregator.aggregate(
            period = ReportPeriod.LAST_7_DAYS,
            nowMillis = now,
            zone = zone,
            heartRates = listOf(
                HeartRateRecord(timestamp = at(2026, 3, 3, 8), bpm = 60),
                HeartRateRecord(timestamp = at(2026, 3, 9, 8), bpm = 80)
            ),
            bloodOxygen = emptyList(),
            bloodPressure = emptyList()
        )

        assertEquals(7, report.daily.size)
        assertEquals(60.0, report.daily.first().heartRateAverage!!, 0.0001)
        assertEquals(80.0, report.daily.last().heartRateAverage!!, 0.0001)
        assertTrue(report.daily.drop(1).dropLast(1).all { it.heartRateAverage == null })
    }

    @Test
    fun `daily average is computed from that day only and empty days stay null`() {
        val now = at(2026, 3, 9, 12)
        val report = HealthAggregator.aggregate(
            period = ReportPeriod.LAST_7_DAYS,
            nowMillis = now,
            zone = zone,
            heartRates = listOf(
                HeartRateRecord(timestamp = at(2026, 3, 9, 8), bpm = 60),
                HeartRateRecord(timestamp = at(2026, 3, 9, 9), bpm = 80)
            ),
            bloodOxygen = emptyList(),
            bloodPressure = emptyList()
        )

        val today = report.daily.last()
        assertEquals(70.0, today.heartRateAverage!!, 0.0001)
        assertEquals(2, today.sampleCount)
        assertTrue(report.daily.dropLast(1).all { it.heartRateAverage == null })
    }
}
