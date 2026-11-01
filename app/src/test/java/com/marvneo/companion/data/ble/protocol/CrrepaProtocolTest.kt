package com.marvneo.companion.data.ble.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Wire-format tests. Every expected value is derived from the reference APK bytecode documented
 * in PROTOCOL_EVIDENCE.md, not from guessing.
 */
class CrrepaProtocolTest {

    // ---------------------------------------------------------------- framing

    @Test
    fun `frame has FE EA header, version, total length and opcode`() {
        val frame = CrrepaProtocol.frame(CrrepaProtocol.CMD_FIND_DEVICE)
        assertArrayEquals(byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x10, 0x05, 0x61), frame)
    }

    @Test
    fun `frame length counts the five header bytes`() {
        val frame = CrrepaProtocol.frame(
            CrrepaProtocol.CMD_TIME_SYNC,
            byteArrayOf(1, 2, 3, 4, 5)
        )
        assertEquals(10, frame.size)
        assertEquals(10, frame[3].toInt() and 0xFF)
    }

    @Test
    fun `version 0x20 is used for larger MTU`() {
        val frame = CrrepaProtocol.frame(CrrepaProtocol.CMD_FIND_DEVICE, version = CrrepaProtocol.VERSION_2)
        assertEquals(0x20, frame[2].toInt() and 0xFF)
    }

    @Test
    fun `parse round-trips a framed command`() {
        val raw = CrrepaProtocol.frame(CrrepaProtocol.CMD_HEART_RATE_ONCE, byteArrayOf(0))
        val parsed = CrrepaProtocol.parse(raw)
        assertNotNull(parsed)
        assertEquals(CrrepaProtocol.CMD_HEART_RATE_ONCE, parsed!!.opcode)
        assertArrayEquals(byteArrayOf(0), parsed.payload)
    }

    @Test
    fun `parse rejects wrong magic, short header and impossible length`() {
        assertNull(CrrepaProtocol.parse(byteArrayOf(0x00, 0xEA.toByte(), 0x10, 0x05, 0x61)))
        assertNull(CrrepaProtocol.parse(byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x10, 0x05)))
        assertNull(CrrepaProtocol.parse(byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x10, 0x02, 0x61)))
        assertNull(CrrepaProtocol.parse(byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x10, 0x09, 0x61)))
    }

    @Test
    fun `parse honours the declared length and ignores trailing bytes`() {
        val frame = CrrepaProtocol.frame(CrrepaProtocol.CMD_FIND_DEVICE) + byteArrayOf(9, 9, 9)
        val parsed = CrrepaProtocol.parse(frame)
        assertNotNull(parsed)
        assertEquals(0, parsed!!.payload.size)
    }

    // ---------------------------------------------------------------- reassembly

    @Test
    fun `a frame split across three notifications is reassembled once`() {
        val assembler = CrrepaFrameAssembler()
        val packet = CrrepaProtocol.frame(CrrepaProtocol.CMD_BLOOD_OXYGEN, byteArrayOf(97))

        assertTrue(assembler.append(packet.copyOfRange(0, 2)).isEmpty())
        assertTrue(assembler.append(packet.copyOfRange(2, 4)).isEmpty())
        val frames = assembler.append(packet.copyOfRange(4, packet.size))

        assertEquals(1, frames.size)
        assertEquals(CrrepaProtocol.CMD_BLOOD_OXYGEN, frames[0].opcode)
        assertArrayEquals(byteArrayOf(97), frames[0].payload)
        assertEquals(0, assembler.pending)
    }

    @Test
    fun `two frames in one notification produce two results`() {
        val assembler = CrrepaFrameAssembler()
        val a = CrrepaProtocol.frame(CrrepaProtocol.CMD_BLOOD_OXYGEN, byteArrayOf(98))
        val b = CrrepaProtocol.frame(CrrepaProtocol.CMD_BLOOD_PRESSURE, byteArrayOf(0, 120, 80))
        val frames = assembler.append(a + b)

        assertEquals(2, frames.size)
        assertEquals(CrrepaProtocol.CMD_BLOOD_OXYGEN, frames[0].opcode)
        assertEquals(CrrepaProtocol.CMD_BLOOD_PRESSURE, frames[1].opcode)
    }

    @Test
    fun `leading garbage is dropped and the next valid frame is still delivered`() {
        val assembler = CrrepaFrameAssembler()
        val valid = CrrepaProtocol.frame(CrrepaProtocol.CMD_ALARM, ByteArray(8) { it.toByte() })
        val frames = assembler.append(byteArrayOf(0x00, 0x11, 0x22) + valid)

        assertEquals(3, assembler.dropped)
        assertEquals(1, frames.size)
        assertEquals(CrrepaProtocol.CMD_ALARM, frames[0].opcode)
    }

    @Test
    fun `an impossible declared length is treated as garbage`() {
        val assembler = CrrepaFrameAssembler()
        val frames = assembler.append(byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x10, 0x01, 0x61))
        assertTrue(frames.isEmpty())
        // Only the leading 0xFE is discarded; the remaining bytes stay buffered awaiting a header.
        assertEquals(4, assembler.pending)
        assertEquals(1, assembler.dropped)
    }

    // ---------------------------------------------------------------- time sync

    @Test
    fun `time sync payload is unix seconds little endian plus timezone hours`() {
        val zone = TimeZone.getTimeZone("GMT+05:30")
        val millis = 1_700_000_000_000L // 2023-11-14T22:13:20Z
        val packet = CrrepaProtocol.timeSync(millis, zone)
        val parsed = CrrepaProtocol.parse(packet)!!

        assertEquals(CrrepaProtocol.CMD_TIME_SYNC, parsed.opcode)
        assertEquals(5, parsed.payload.size)
        val seconds = parsed.payload.take(4).foldIndexed(0L) { index, acc, byte ->
            acc or ((byte.toLong() and 0xFF) shl (8 * index))
        }
        assertEquals(millis / 1000, seconds)
        // Vendor SDK sends whole hours; GMT+05:30 -> 5.
        assertEquals(5, parsed.payload[4].toInt())
    }

    // ---------------------------------------------------------------- measurements

    @Test
    fun `heart rate start and stop payloads match the SDK`() {
        val start = CrrepaProtocol.parse(CrrepaProtocol.heartRateMeasurement(true))!!
        val stop = CrrepaProtocol.parse(CrrepaProtocol.heartRateMeasurement(false))!!
        assertEquals(CrrepaProtocol.CMD_HEART_RATE_ONCE, start.opcode)
        assertArrayEquals(byteArrayOf(0x00), start.payload)
        assertArrayEquals(byteArrayOf(0xFF.toByte()), stop.payload)
    }

    @Test
    fun `blood pressure start and stop payloads are three bytes`() {
        val start = CrrepaProtocol.parse(CrrepaProtocol.bloodPressureMeasurement(true))!!
        val stop = CrrepaProtocol.parse(CrrepaProtocol.bloodPressureMeasurement(false))!!
        assertEquals(CrrepaProtocol.CMD_BLOOD_PRESSURE, start.opcode)
        assertArrayEquals(byteArrayOf(0, 0, 0), start.payload)
        assertArrayEquals(byteArrayOf(-1, -1, -1), stop.payload)
    }

    @Test
    fun `heart rate measurement parses 8-bit and 16-bit bpm`() {
        assertEquals(72, CrrepaProtocol.parseHeartRateMeasurement(byteArrayOf(0x00, 72)))
        // flags bit0 set -> 16-bit little endian 0x0130 = 304 is plausible for this parser test
        assertEquals(304, CrrepaProtocol.parseHeartRateMeasurement(byteArrayOf(0x01, 0x30, 0x01)))
        assertNull(CrrepaProtocol.parseHeartRateMeasurement(byteArrayOf(0x00)))
        assertNull(CrrepaProtocol.parseHeartRateMeasurement(byteArrayOf(0x00, 0)))
    }

    @Test
    fun `spo2 reply is one percentage byte`() {
        assertEquals(98, CrrepaProtocol.parseBloodOxygen(byteArrayOf(98))!!.percentage)
        assertNull(CrrepaProtocol.parseBloodOxygen(byteArrayOf(0)))
        assertNull(CrrepaProtocol.parseBloodOxygen(byteArrayOf(49)))
        assertNull(CrrepaProtocol.parseBloodOxygen(byteArrayOf(101)))
        assertNull(CrrepaProtocol.parseBloodOxygen(ByteArray(0)))
    }

    @Test
    fun `blood pressure reply reads systolic from byte one and diastolic from byte two`() {
        val bp = CrrepaProtocol.parseBloodPressure(byteArrayOf(0, 120, 80))!!
        assertEquals(120, bp.systolic)
        assertEquals(80, bp.diastolic)
        assertNull(CrrepaProtocol.parseBloodPressure(byteArrayOf(0, 120)))
        assertNull(CrrepaProtocol.parseBloodPressure(byteArrayOf(0, 0, 80)))
    }

    @Test
    fun `step notification reads three little-endian triplets`() {
        val payload = byteArrayOf(
            0x10, 0x00, 0x00,       // 16 steps
            0x20, 0x00, 0x00,       // 32 metres
            0x05, 0x00, 0x00,       // 5 kcal
            0x07, 0x00, 0x00        // 7 active minutes
        )
        val steps = CrrepaProtocol.parseSteps(payload)!!
        assertEquals(16, steps.steps)
        assertEquals(32, steps.distanceMeters)
        assertEquals(5, steps.calories)
        assertEquals(7, steps.activeMinutes)
    }

    @Test
    fun `step notification rejects a payload that is not a multiple of three`() {
        assertNull(CrrepaProtocol.parseSteps(byteArrayOf(1, 2, 3, 4)))
        assertNull(CrrepaProtocol.parseSteps(ByteArray(0)))
    }

    // ---------------------------------------------------------------- alarms

    @Test
    fun `alarm payload is eight bytes with documented field order`() {
        val packet = CrrepaProtocol.alarm(
            slot = 2,
            enabled = true,
            repeatMask = CrrepaProtocol.REPEAT_EVERY_DAY,
            hour = 6,
            minute = 45
        )
        val parsed = CrrepaProtocol.parse(packet)!!
        assertEquals(CrrepaProtocol.CMD_ALARM, parsed.opcode)

        val p = parsed.payload
        assertEquals(8, p.size)
        assertEquals(2, p[0].toInt())
        assertEquals(1, p[1].toInt())
        assertEquals(0x7F, p[2].toInt())
        assertEquals(6, p[3].toInt())
        assertEquals(45, p[4].toInt())
        assertEquals(0, p[5].toInt())
        assertEquals(0, p[6].toInt())
        assertEquals(0x7F, p[7].toInt())
    }

    @Test
    fun `one-off alarm encodes a date code as year minus 2015`() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 9, 7, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val packet = CrrepaProtocol.alarm(
            slot = 0,
            enabled = true,
            repeatMask = 0,
            hour = 7,
            minute = 30,
            oneOffDateMillis = calendar.timeInMillis
        )
        val p = CrrepaProtocol.parse(packet)!!.payload
        assertEquals("single-occurrence mode is 0", 0, p[2].toInt())
        val dateCode = (p[5].toInt() and 0xFF) or ((p[6].toInt() and 0xFF) shl 8)
        val expected = ((2026 - 2015) shl 12) or (3 shl 8) or 9
        assertEquals(expected, dateCode)
    }

    @Test
    fun `repeat mask bits map Sunday to bit zero and Saturday to bit six`() {
        assertEquals(0x01, CrrepaProtocol.maskBitForCalendarDay(Calendar.SUNDAY))
        assertEquals(0x02, CrrepaProtocol.maskBitForCalendarDay(Calendar.MONDAY))
        assertEquals(0x40, CrrepaProtocol.maskBitForCalendarDay(Calendar.SATURDAY))
        assertTrue(CrrepaProtocol.repeatsOn(0x7F, Calendar.WEDNESDAY))
        assertTrue(!CrrepaProtocol.repeatsOn(0x02, Calendar.WEDNESDAY))
    }

    @Test
    fun `query alarms payload matches the SDK constant`() {
        val packet = CrrepaProtocol.parse(CrrepaProtocol.queryNewAlarms())!!
        assertEquals(CrrepaProtocol.CMD_QUERY_NEW_ALARMS, packet.opcode)
        assertArrayEquals(byteArrayOf(0x15, 0x04), packet.payload)
    }
}
