package com.marvneo.companion.domain.report

import com.marvneo.companion.data.db.entity.BloodOxygenRecord
import com.marvneo.companion.data.db.entity.BloodPressureRecord
import com.marvneo.companion.data.db.entity.HeartRateRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
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

    // ---------------------------------------------------------------- time zones and DST

    @Test
    fun `the injected zone decides the calendar day, not the machine zone`() {
        val instant = Instant.parse("2026-03-08T11:30:00Z")
        val auckland = ZoneId.of("Pacific/Auckland")
        val utc = ZoneId.of("UTC")

        val aucklandWindow = ReportWindows.of(ReportPeriod.TODAY, instant.toEpochMilli(), auckland)
        val utcWindow = ReportWindows.of(ReportPeriod.TODAY, instant.toEpochMilli(), utc)

        assertEquals(
            LocalDate.of(2026, 3, 9),
            Instant.ofEpochMilli(aucklandWindow.startMillis).atZone(auckland).toLocalDate()
        )
        assertEquals(
            LocalDate.of(2026, 3, 8),
            Instant.ofEpochMilli(utcWindow.startMillis).atZone(utc).toLocalDate()
        )
        assertTrue(aucklandWindow.startMillis != utcWindow.startMillis)
    }

    @Test
    fun `a DST spring-forward day is still one calendar bucket and is 23 hours long`() {
        val newYork = ZoneId.of("America/New_York")
        // 2026-03-08 is the US spring-forward day (02:00 -> 03:00 local).
        val now = LocalDateTime.of(2026, 3, 9, 12, 0).atZone(newYork).toInstant().toEpochMilli()
        val report = HealthAggregator.aggregate(
            period = ReportPeriod.LAST_7_DAYS,
            nowMillis = now,
            zone = newYork,
            heartRates = listOf(
                HeartRateRecord(
                    timestamp = LocalDateTime.of(2026, 3, 8, 3, 30)
                        .atZone(newYork).toInstant().toEpochMilli(),
                    bpm = 66
                )
            ),
            bloodOxygen = emptyList(),
            bloodPressure = emptyList()
        )

        assertEquals(7, report.daily.size)
        val dstDay = report.daily[5] // 2026-03-08
        assertEquals(66.0, dstDay.heartRateAverage!!, 0.0001)
        val dayAfter = report.daily[6] // 2026-03-09
        assertEquals(
            "the spring-forward day is 23 hours, not 24",
            23 * 60 * 60 * 1000L,
            dayAfter.dayStartMillis - dstDay.dayStartMillis
        )
    }

    @Test
    fun `a DST fall-back day is still one calendar bucket and is 25 hours long`() {
        val newYork = ZoneId.of("America/New_York")
        // 2026-11-01 is the US fall-back day (02:00 -> 01:00 local).
        val now = LocalDateTime.of(2026, 11, 2, 12, 0).atZone(newYork).toInstant().toEpochMilli()
        val report = HealthAggregator.aggregate(
            period = ReportPeriod.LAST_7_DAYS,
            nowMillis = now,
            zone = newYork,
            heartRates = emptyList(),
            bloodOxygen = emptyList(),
            bloodPressure = emptyList()
        )

        assertEquals(7, report.daily.size)
        val dstDay = report.daily[5] // 2026-11-01
        val dayAfter = report.daily[6] // 2026-11-02
        assertEquals(
            "the fall-back day is 25 hours, not 24",
            25 * 60 * 60 * 1000L,
            dayAfter.dayStartMillis - dstDay.dayStartMillis
        )
    }

    @Test
    fun `seven day window crosses a year boundary without losing days`() {
        val now = at(2027, 1, 2, 12)
        val window = ReportWindows.of(ReportPeriod.LAST_7_DAYS, now, zone)
        assertEquals(startOfDay(2026, 12, 27), window.startMillis)

        val report = HealthAggregator.aggregate(
            period = ReportPeriod.LAST_7_DAYS,
            nowMillis = now,
            zone = zone,
            heartRates = emptyList(),
            bloodOxygen = emptyList(),
            bloodPressure = emptyList()
        )
        assertEquals(7, report.daily.size)
        assertEquals(startOfDay(2026, 12, 27), report.daily.first().dayStartMillis)
        assertEquals(7, report.daily.map { it.dayStartMillis }.distinct().size)
    }

    @Test
    fun `an empty seven day report keeps every day null and has no stats`() {
        val now = at(2026, 3, 9, 12)
        val report = HealthAggregator.aggregate(
            period = ReportPeriod.LAST_7_DAYS,
            nowMillis = now,
            zone = zone,
            heartRates = emptyList(),
            bloodOxygen = emptyList(),
            bloodPressure = emptyList()
        )

        assertEquals(7, report.daily.size)
        assertTrue(report.daily.all { it.heartRateAverage == null && it.spo2Average == null })
        assertTrue(report.daily.all { it.sampleCount == 0 })
        assertEquals(0, report.heartRate.count)
        assertNull(report.heartRate.average)
        assertFalse(report.bloodPressureSupported)
        assertTrue(report.heartRateRecords.isEmpty())
    }

    // ---------------------------------------------------------------- blood-pressure dedupe

    @Test
    fun `identical blood pressure within three seconds is counted once`() {
        val base = at(2026, 3, 9, 9)
        val records = listOf(
            BloodPressureRecord(timestamp = base, systolic = 120, diastolic = 80),
            BloodPressureRecord(timestamp = base + 1_000, systolic = 120, diastolic = 80),
            BloodPressureRecord(timestamp = base + 3_000, systolic = 120, diastolic = 80)
        )
        assertEquals(1, HealthAggregator.validBloodPressure(records).size)
    }

    @Test
    fun `identical blood pressure after three seconds is kept`() {
        val base = at(2026, 3, 9, 9)
        val records = listOf(
            BloodPressureRecord(timestamp = base, systolic = 120, diastolic = 80),
            BloodPressureRecord(timestamp = base + 3_001, systolic = 120, diastolic = 80)
        )
        assertEquals(2, HealthAggregator.validBloodPressure(records).size)
    }

    @Test
    fun `different blood pressure close together is kept`() {
        val base = at(2026, 3, 9, 9)
        val records = listOf(
            BloodPressureRecord(timestamp = base, systolic = 120, diastolic = 80),
            BloodPressureRecord(timestamp = base + 1_000, systolic = 118, diastolic = 78)
        )
        assertEquals(2, HealthAggregator.validBloodPressure(records).size)
    }
}
