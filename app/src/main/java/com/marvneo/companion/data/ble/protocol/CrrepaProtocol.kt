package com.marvneo.companion.data.ble.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Packet codec for Crrepa-class smartwatch firmware (the family the beatXP Marv Neo reference
 * application talks to).
 *
 * Every constant below is traceable to bytecode in the reference APK. See PROTOCOL_EVIDENCE.md
 * for the exact class and method for each one — nothing here is guessed.
 *
 * ## Wire frame
 * Built by `Lio/grpc/w;->Q(I[B)[B`:
 * ```
 * [0]=0xFE  [1]=0xEA  [2]=version  [3]=totalLength  [4]=command  [5..]=payload
 * ```
 * `version` is `0x10` when the negotiated chunk size is the default 20 bytes, otherwise `0x20`.
 * `totalLength` counts the whole packet including the 5 header bytes.
 *
 * Inbound reassembly (`com.crrepa.l.a#n0`) checks `bArr[0] == 0xFE && bArr[1] == 0xEA`, reads the
 * version from byte 2 and the total length from byte 3.
 *
 * This file is pure JVM byte handling so it can be unit tested without a device.
 */
object CrrepaProtocol {

    const val MAGIC_0 = 0xFE
    const val MAGIC_1 = 0xEA

    /** Protocol version used when the chunk size is the default 20 bytes. */
    const val VERSION_1 = 0x10
    /** Protocol version used once a larger MTU is negotiated. */
    const val VERSION_2 = 0x20

    const val HEADER_SIZE = 5

    // ---------------------------------------------------------------- outbound commands
    // Values are the first argument passed to `io.grpc.w.Q(...)` in `com.crrepa.g.a`.

    /** `com.crrepa.g.a#syncTime` -> `Q(49, {unixSecondsLe32, timezoneHours})`. */
    const val CMD_TIME_SYNC = 0x31

    /** `com.crrepa.g.a#findDevice` -> `Q(97, null)`. No reply frame is defined for it. */
    const val CMD_FIND_DEVICE = 0x61

    /**
     * `com.crrepa.g.a#startMeasureOnceHeartRate` -> `Q(109, {0x00})` and
     * `#stopMeasureOnceHeartRate` -> `Q(109, {0xFF})`.
     *
     * The reply (`com.crrepa.l.a#K0`) carries a *completion state*, not a BPM. Actual beats per
     * minute arrive on the standard Heart Rate Measurement characteristic (0x2A37).
     */
    const val CMD_HEART_RATE_ONCE = 0x6D

    /** `#startMeasureBloodOxygen` -> `Q(107, {0x00})`; `#stopMeasureBloodOxygen` -> `Q(107, {0xFF})`. */
    const val CMD_BLOOD_OXYGEN = 0x6B

    /** `#startMeasureBloodPressure` -> `Q(105, {0,0,0})`; stop -> `Q(105, {0xFF,0xFF,0xFF})`. */
    const val CMD_BLOOD_PRESSURE = 0x69

    /** `#sendAlarm` -> `Q(17, <8-byte alarm payload>)`. */
    const val CMD_ALARM = 0x11

    /** `#queryAllNewAlarm` -> `Q(-71, {0x15, 0x04})`. */
    const val CMD_QUERY_NEW_ALARMS = 0xB9

    // ---------------------------------------------------------------- frame assembly

    data class Frame(val version: Int, val opcode: Int, val payload: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Frame) return false
            return version == other.version &&
                opcode == other.opcode &&
                payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int =
            (version * 31 + opcode) * 31 + payload.contentHashCode()
    }

    /**
     * Wraps [opcode] and [payload] in the vendor frame.
     *
     * @param version 0x10 for the default 20-byte chunk size, 0x20 after a larger MTU.
     */
    fun frame(
        opcode: Int,
        payload: ByteArray = ByteArray(0),
        version: Int = VERSION_1
    ): ByteArray {
        val total = payload.size + HEADER_SIZE
        require(total <= 0xFF) { "Crrepa packet too large: $total bytes" }
        val out = ByteArray(total)
        out[0] = MAGIC_0.toByte()
        out[1] = MAGIC_1.toByte()
        out[2] = version.toByte()
        out[3] = total.toByte()
        out[4] = opcode.toByte()
        payload.copyInto(out, HEADER_SIZE)
        return out
    }

    /**
     * Strict parser: returns null unless the buffer is a complete, well-formed frame.
     * A strict parser is what keeps the UI honest — junk is never interpreted as data.
     */
    fun parse(raw: ByteArray): Frame? {
        if (raw.size < HEADER_SIZE) return null
        if ((raw[0].toInt() and 0xFF) != MAGIC_0) return null
        if ((raw[1].toInt() and 0xFF) != MAGIC_1) return null
        val declaredLength = raw[3].toInt() and 0xFF
        if (declaredLength < HEADER_SIZE || declaredLength > raw.size) return null
        return Frame(
            version = raw[2].toInt() and 0xFF,
            opcode = raw[4].toInt() and 0xFF,
            payload = raw.copyOfRange(HEADER_SIZE, declaredLength)
        )
    }

    // ---------------------------------------------------------------- command builders

    /**
     * Time synchronisation. Payload is a 4-byte little-endian Unix timestamp in seconds followed
     * by the device's UTC offset in whole hours, which is what the vendor SDK sends.
     */
    fun timeSync(nowMillis: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): ByteArray {
        val seconds = nowMillis / 1000
        val offsetHours = zone.getOffset(nowMillis) / 3_600_000
        val payload = ByteArray(5)
        payload[0] = (seconds and 0xFF).toByte()
        payload[1] = ((seconds shr 8) and 0xFF).toByte()
        payload[2] = ((seconds shr 16) and 0xFF).toByte()
        payload[3] = ((seconds shr 24) and 0xFF).toByte()
        payload[4] = offsetHours.toByte()
        return frame(CMD_TIME_SYNC, payload)
    }

    /** Starts/stops the watch's find-me alert. The vendor protocol defines no reply. */
    fun findDevice(): ByteArray = frame(CMD_FIND_DEVICE)

    fun heartRateMeasurement(start: Boolean): ByteArray =
        frame(CMD_HEART_RATE_ONCE, byteArrayOf(if (start) 0x00 else 0xFF.toByte()))

    fun bloodOxygenMeasurement(start: Boolean): ByteArray =
        frame(CMD_BLOOD_OXYGEN, byteArrayOf(if (start) 0x00 else 0xFF.toByte()))

    fun bloodPressureMeasurement(start: Boolean): ByteArray =
        frame(
            CMD_BLOOD_PRESSURE,
            if (start) byteArrayOf(0x00, 0x00, 0x00) else byteArrayOf(-1, -1, -1)
        )

    /**
     * Alarm slot write.
     *
     * Payload layout, verified from `Lio/grpc/w;->S(CRPAlarmInfo)[B`:
     * ```
     * [0] slot id
     * [1] enabled (0/1)
     * [2] mode: 0 = single occurrence, 0x7F = every day, 2 = custom repeat
     * [3] hour
     * [4] minute
     * [5..6] date code, little endian, only used for single-occurrence alarms
     * [7] repeat bitmask
     * ```
     *
     * Repeat mask bits follow `CRPAlarmInfo`: bit0 = Sunday, bit1 = Monday, ... bit6 = Saturday,
     * with 0x7F meaning every day.
     */
    fun alarm(
        slot: Int,
        enabled: Boolean,
        repeatMask: Int,
        hour: Int,
        minute: Int,
        oneOffDateMillis: Long? = null
    ): ByteArray {
        val payload = ByteArray(8)
        payload[0] = slot.toByte()
        payload[1] = if (enabled) 1 else 0
        payload[2] = when {
            oneOffDateMillis != null -> 0x00
            repeatMask == REPEAT_EVERY_DAY -> 0x7F
            else -> 0x02
        }
        payload[3] = hour.toByte()
        payload[4] = minute.toByte()

        val dateCode = if (oneOffDateMillis != null) {
            val cal = Calendar.getInstance().apply { timeInMillis = oneOffDateMillis }
            // year - 2015, month (1-12), day-of-month packed as the vendor SDK does.
            ((cal.get(Calendar.YEAR) - 2015) shl 12) or
                ((cal.get(Calendar.MONTH) + 1) shl 8) or
                cal.get(Calendar.DAY_OF_MONTH)
        } else {
            0
        }
        payload[5] = (dateCode and 0xFF).toByte()
        payload[6] = ((dateCode shr 8) and 0xFF).toByte()
        payload[7] = repeatMask.toByte()
        return frame(CMD_ALARM, payload)
    }

    /** Asks the watch to report every alarm slot it currently holds. */
    fun queryNewAlarms(): ByteArray =
        frame(CMD_QUERY_NEW_ALARMS, byteArrayOf(0x15, 0x04))

    /** Repeat-mask bits, deliberately named so call sites cannot get Sunday/Monday wrong. */
    const val REPEAT_SUNDAY = 0x01
    const val REPEAT_MONDAY = 0x02
    const val REPEAT_TUESDAY = 0x04
    const val REPEAT_WEDNESDAY = 0x08
    const val REPEAT_THURSDAY = 0x10
    const val REPEAT_FRIDAY = 0x20
    const val REPEAT_SATURDAY = 0x40
    const val REPEAT_EVERY_DAY = 0x7F

    // ---------------------------------------------------------------- inbound parsing

    data class HeartRateState(val state: Int)
    data class SpO2(val percentage: Int)
    data class BloodPressure(val systolic: Int, val diastolic: Int)

    /**
     * A step notification. Any field the watch marks as "no value" (all three bytes `0xFF`) is
     * null; it is never turned into a fabricated zero. A 3-, 6-, 9- or 12-byte payload is accepted,
     * so a 3-byte step-only notification is not discarded.
     */
    data class Steps(
        val steps: Int?,
        val distanceMeters: Int?,
        val calories: Int?,
        val activeMinutes: Int?
    )

    /** `com.crrepa.l.a#K0`: one byte of measurement state for the 0x6D reply. */
    fun parseHeartRateState(payload: ByteArray): HeartRateState? {
        if (payload.isEmpty()) return null
        return HeartRateState(payload[0].toInt() and 0xFF)
    }

    /** `com.crrepa.l.a#N0`: `onBloodOxygen(payload[0])`. */
    fun parseBloodOxygen(payload: ByteArray): SpO2? {
        if (payload.isEmpty()) return null
        val percent = payload[0].toInt() and 0xFF
        if (percent !in 50..100) return null
        return SpO2(percent)
    }

    /**
     * `com.crrepa.i.b#a`: requires exactly three bytes and reads
     * `systolic = payload[1]`, `diastolic = payload[2]`.
     *
     * Physiological bounds and `systolic > diastolic` are enforced here, not only at the call site,
     * so no caller can persist an implausible pair.
     */
    fun parseBloodPressure(payload: ByteArray): BloodPressure? {
        if (payload.size != 3) return null
        val systolic = payload[1].toInt() and 0xFF
        val diastolic = payload[2].toInt() and 0xFF
        // Both values are single unsigned bytes, so 255 is the highest representable systolic.
        if (systolic !in 60..255 || diastolic !in 30..160) return null
        if (systolic <= diastolic) return null
        return BloodPressure(systolic, diastolic)
    }

    /**
     * Standard Heart Rate Measurement characteristic (0x2A37).
     * Byte 0 holds flags; bit0 clear means the BPM is a single byte at index 1.
     */
    fun parseHeartRateMeasurement(value: ByteArray): Int? {
        if (value.size < 2) return null
        val flags = value[0].toInt() and 0xFF
        val bpm = if (flags and 0x01 == 0) {
            value[1].toInt() and 0xFF
        } else {
            if (value.size < 3) return null
            // 16-bit little-endian
            ((value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8))
        }
        return bpm.takeIf { it in 1..65535 }
    }

    /**
     * Live step notification on FEE1 (`Lcom/google/android/play/core/appupdate/d;->D([B)`).
     * The payload is 3, 6, 9 or 12 bytes of 3-byte little-endian values: steps, distance, calories
     * and optionally active minutes.
     *
     * `com.crrepa.n0.a#x([B)` treats an all-`0xFF` 24-bit field as "no value". Preserving that as
     * null is what keeps `16,777,215` steps out of the UI and the database.
     */
    fun parseSteps(value: ByteArray): Steps? {
        if (value.size !in setOf(3, 6, 9, 12)) return null
        return Steps(
            steps = readUInt24LeOrNull(value, 0),
            distanceMeters = if (value.size >= 6) readUInt24LeOrNull(value, 3) else null,
            calories = if (value.size >= 9) readUInt24LeOrNull(value, 6) else null,
            activeMinutes = if (value.size >= 12) readUInt24LeOrNull(value, 9) else null
        )
    }

    /** Returns null for the documented "no value" marker (`0xFF 0xFF 0xFF`). */
    private fun readUInt24LeOrNull(bytes: ByteArray, offset: Int): Int? =
        readUInt24Le(bytes, offset).takeUnless { it == 0xFFFFFF }

    private fun readUInt24Le(bytes: ByteArray, offset: Int): Int {
        if (offset + 3 > bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16)
    }

    /** Firmware string is read from the standard 0x2A28 characteristic, not from a packet. */
    fun decodeFirmware(raw: ByteArray): String =
        String(raw, Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }

    fun formatAlarmLabel(hour: Int, minute: Int): String =
        String.format(Locale.getDefault(), "%02d:%02d", hour, minute)

    /** Converts a `java.util.Calendar` day-of-week constant into the vendor repeat-mask bit. */
    fun maskBitForCalendarDay(calendarDayOfWeek: Int): Int = when (calendarDayOfWeek) {
        Calendar.SUNDAY -> REPEAT_SUNDAY
        Calendar.MONDAY -> REPEAT_MONDAY
        Calendar.TUESDAY -> REPEAT_TUESDAY
        Calendar.WEDNESDAY -> REPEAT_WEDNESDAY
        Calendar.THURSDAY -> REPEAT_THURSDAY
        Calendar.FRIDAY -> REPEAT_FRIDAY
        else -> REPEAT_SATURDAY
    }

    fun repeatsOn(mask: Int, calendarDayOfWeek: Int): Boolean =
        (mask and maskBitForCalendarDay(calendarDayOfWeek)) != 0

    /** Little-endian 2-byte helper, matching `com.crrepa.n0.a#o`. */
    fun readShortLe(bytes: ByteArray, offset: Int): Int {
        if (offset + 1 >= bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    /** Little-endian 4-byte helper, matching `com.crrepa.n0.a#v`. */
    fun readIntLe(bytes: ByteArray, offset: Int): Long {
        if (offset + 3 >= bytes.size) return 0
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
    }
}
