package com.marvneo.companion.data.ble.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar

object CrrepaProtocol {

    // Crrepa Packet Header Constants
    const val MAGIC_BYTE_0: Byte = 0xFE.toByte()
    const val MAGIC_BYTE_1: Byte = 0xEA.toByte()
    const val PROTOCOL_VERSION_1: Byte = 0x10
    const val PROTOCOL_VERSION_2: Byte = 0x20

    // Authentic Crrepa Hardware Command Opcodes (Extracted from beatXP.apk)
    const val CMD_SYNC_TIME: Byte = 0x01
    const val CMD_QUERY_BATTERY: Byte = 0x03
    const val CMD_RAISE_TO_WAKE: Byte = 0x05
    const val CMD_FIND_WATCH: Byte = 0x61         // Real Crrepa findDevice opcode (97)
    const val CMD_HEART_RATE_ONCE: Byte = 0x6D    // Realtime/once heart rate measurement (109)
    const val CMD_BLOOD_OXYGEN: Byte = 0x6B       // Blood oxygen SpO2 measurement (107)
    const val CMD_BLOOD_PRESSURE: Byte = 0x69     // Blood pressure measurement (105)
    const val CMD_CAMERA_CONTROL: Byte = 0x09
    const val CMD_TIME_FORMAT: Byte = 0x0A
    const val CMD_ALARM: Byte = 0x11
    const val CMD_QUERY_STEPS: Byte = 0x12
    const val CMD_NOTIFICATION: Byte = 0x23

    // Notification Type IDs (from CRPBleMessageType)
    const val NOTIF_CALL: Byte = 0x01
    const val NOTIF_SMS: Byte = 0x02
    const val NOTIF_WECHAT: Byte = 0x03
    const val NOTIF_FACEBOOK: Byte = 0x04
    const val NOTIF_TWITTER: Byte = 0x05
    const val NOTIF_WHATSAPP: Byte = 0x06
    const val NOTIF_INSTAGRAM: Byte = 0x07
    const val NOTIF_SKYPE: Byte = 0x08
    const val NOTIF_TELEGRAM: Byte = 0x09
    const val NOTIF_LINKEDIN: Byte = 0x0A
    const val NOTIF_GMAIL: Byte = 0x0B
    const val NOTIF_YOUTUBE: Byte = 0x0C
    const val NOTIF_OTHER: Byte = 0xFF.toByte()

    /**
     * Encloses an opcode and its payload in the authentic Crrepa packet frame:
     * [0xFE, 0xEA, 0x10, TotalLength, Opcode, PayloadBytes...]
     */
    fun framePacket(opcode: Byte, payload: ByteArray = byteArrayOf()): ByteArray {
        val totalLength = (5 + payload.size).toByte()
        val packet = ByteArray(5 + payload.size)
        packet[0] = MAGIC_BYTE_0
        packet[1] = MAGIC_BYTE_1
        packet[2] = PROTOCOL_VERSION_1
        packet[3] = totalLength
        packet[4] = opcode
        if (payload.isNotEmpty()) {
            System.arraycopy(payload, 0, packet, 5, payload.size)
        }
        return packet
    }

    /**
     * Builds a Time Synchronization packet.
     * Format: [0xFE, 0xEA, 0x10, 0x0C, 0x01, YearLow, YearHigh, Month, Day, Hour, Minute, Second]
     */
    fun createTimeSyncPacket(calendar: Calendar = Calendar.getInstance()): ByteArray {
        val year = calendar.get(Calendar.YEAR)
        val month = (calendar.get(Calendar.MONTH) + 1).toByte()
        val day = calendar.get(Calendar.DAY_OF_MONTH).toByte()
        val hour = calendar.get(Calendar.HOUR_OF_DAY).toByte()
        val min = calendar.get(Calendar.MINUTE).toByte()
        val sec = calendar.get(Calendar.SECOND).toByte()

        val payload = byteArrayOf(
            (year and 0xFF).toByte(),
            ((year shr 8) and 0xFF).toByte(),
            month,
            day,
            hour,
            min,
            sec
        )
        return framePacket(CMD_SYNC_TIME, payload)
    }

    /**
     * Builds a Battery Level query packet.
     */
    fun createBatteryQueryPacket(): ByteArray = framePacket(CMD_QUERY_BATTERY)

    /**
     * Builds a Step query packet.
     */
    fun createStepsQueryPacket(): ByteArray = framePacket(CMD_QUERY_STEPS, byteArrayOf(0x01, 0x00))

    /**
     * Builds a Find Watch vibration/screen alert packet using authentic opcode 0x61.
     * @param enable true to start ringing/vibrating, false to cancel.
     */
    fun createFindWatchPacket(enable: Boolean): ByteArray =
        framePacket(CMD_FIND_WATCH, byteArrayOf(if (enable) 0x01 else 0x00))

    /**
     * Builds a Heart Rate measure trigger packet using authentic Crrepa opcode 0x6D.
     * @param enable true to start measurement, false to stop.
     */
    fun createHeartRateControlPacket(enable: Boolean): ByteArray =
        framePacket(CMD_HEART_RATE_ONCE, byteArrayOf(if (enable) 0x01 else 0x00))

    /**
     * Builds an SpO2 (Blood Oxygen) measure trigger packet using authentic Crrepa opcode 0x6B.
     * @param enable true to start measurement, false to stop.
     */
    fun createSpO2ControlPacket(enable: Boolean): ByteArray =
        framePacket(CMD_BLOOD_OXYGEN, byteArrayOf(if (enable) 0x01 else 0x00))

    /**
     * Builds a Blood Pressure measure trigger packet using authentic Crrepa opcode 0x69.
     * @param enable true to start measurement, false to stop.
     */
    fun createBloodPressureControlPacket(enable: Boolean): ByteArray =
        framePacket(CMD_BLOOD_PRESSURE, byteArrayOf(if (enable) 0x01 else 0x00))

    /**
     * Builds an Alarm synchronization packet using authentic Crrepa opcode 0x11.
     * Format: [0xFE, 0xEA, 0x10, 0x0A, 0x11, Id, IsEnable, RepeatMode, Hour, Minute]
     * @param id Alarm slot index (0..2)
     * @param isEnabled Whether alarm is active
     * @param repeatDaysMask Bitmask of repeating days (bit 0=Mon, 1=Tue, ..., 6=Sun; 0x7F = every day)
     */
    fun createAlarmPacket(
        id: Int,
        isEnabled: Boolean,
        repeatDaysMask: Int,
        hour: Int,
        minute: Int
    ): ByteArray {
        val payload = byteArrayOf(
            (id and 0xFF).toByte(),
            if (isEnabled) 0x01 else 0x00,
            (repeatDaysMask and 0xFF).toByte(),
            (hour and 0xFF).toByte(),
            (minute and 0xFF).toByte()
        )
        return framePacket(CMD_ALARM, payload)
    }

    /**
     * Query existing alarms configured on the watch.
     */
    fun createAlarmQueryPacket(): ByteArray = framePacket(CMD_ALARM, byteArrayOf(0x00))

    /**
     * Builds Camera shutter mode packet.
     */
    fun createCameraModePacket(open: Boolean): ByteArray =
        framePacket(CMD_CAMERA_CONTROL, byteArrayOf(if (open) 0x01 else 0x00))

    /**
     * Builds Raise-To-Wake wrist turn packet.
     */
    fun createRaiseToWakePacket(enabled: Boolean, startHour: Int = 8, endHour: Int = 22): ByteArray {
        val payload = if (enabled) {
            byteArrayOf(0x01, startHour.toByte(), 0x00, endHour.toByte(), 0x00)
        } else {
            byteArrayOf(0x00)
        }
        return framePacket(CMD_RAISE_TO_WAKE, payload)
    }

    /**
     * Builds 12h/24h Time format packet.
     * @param is24Hour true for 24-hour format, false for 12-hour format.
     */
    fun createTimeFormatPacket(is24Hour: Boolean): ByteArray =
        framePacket(CMD_TIME_FORMAT, byteArrayOf(if (is24Hour) 0x00 else 0x01))

    /**
     * Formats an incoming notification push packet.
     * Format: [0x23, AppType, TitleLen, TitleBytes..., MsgLen, MsgBytes...]
     */
    fun createNotificationPacket(type: Byte, title: String, message: String): ByteArray {
        val titleBytes = title.take(32).toByteArray(Charsets.UTF_8)
        val msgBytes = message.take(128).toByteArray(Charsets.UTF_8)

        val buffer = ByteBuffer.allocate(3 + titleBytes.size + 1 + msgBytes.size)
        buffer.put(CMD_NOTIFICATION)
        buffer.put(type)
        buffer.put(titleBytes.size.toByte())
        buffer.put(titleBytes)
        buffer.put(msgBytes.size.toByte())
        buffer.put(msgBytes)

        return buffer.array()
    }

    // Telemetry Data Types
    data class StepsData(val steps: Int, val calories: Int, val distanceMeters: Int)
    data class BatteryData(val percentage: Int, val isCharging: Boolean)
    data class HeartRateData(val bpm: Int, val status: Int)
    data class SpO2Data(val percentage: Int, val status: Int)
    data class BloodPressureData(val systolic: Int, val diastolic: Int, val status: Int)

    fun parseSteps(payload: ByteArray): StepsData? {
        // Strip Crrepa framing if present [FE, EA, 10, len, 0x12/0x11, ...]
        val data = unframeIfNeeded(payload)
        if (data.size < 12) return null
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val steps = buffer.getInt(0)
        val calories = buffer.getInt(4)
        val distance = buffer.getInt(8)
        return StepsData(steps, calories, distance)
    }

    fun parseBattery(payload: ByteArray): BatteryData? {
        val data = unframeIfNeeded(payload)
        if (data.isEmpty()) return null
        val percentage = data[0].toInt() and 0xFF
        val isCharging = if (data.size > 1) data[1].toInt() == 1 else false
        return BatteryData(percentage.coerceIn(0, 100), isCharging)
    }

    fun parseHeartRate(payload: ByteArray): HeartRateData? {
        val data = unframeIfNeeded(payload)
        if (data.isEmpty()) return null
        val bpm = data[0].toInt() and 0xFF
        val status = if (data.size > 1) data[1].toInt() and 0xFF else 0
        return HeartRateData(bpm, status)
    }

    fun parseSpO2(payload: ByteArray): SpO2Data? {
        val data = unframeIfNeeded(payload)
        if (data.isEmpty()) return null
        val percentage = data[0].toInt() and 0xFF
        val status = if (data.size > 1) data[1].toInt() and 0xFF else 0
        return SpO2Data(percentage.coerceIn(0, 100), status)
    }

    fun parseBloodPressure(payload: ByteArray): BloodPressureData? {
        val data = unframeIfNeeded(payload)
        if (data.size < 2) return null
        val systolic = data[0].toInt() and 0xFF
        val diastolic = data[1].toInt() and 0xFF
        val status = if (data.size > 2) data[2].toInt() and 0xFF else 0
        return BloodPressureData(systolic, diastolic, status)
    }

    /**
     * Unwraps packet if enclosed with Crrepa [0xFE, 0xEA, version, len, opcode, payload...]
     */
    fun unframeIfNeeded(packet: ByteArray): ByteArray {
        if (packet.size >= 5 && packet[0] == MAGIC_BYTE_0 && packet[1] == MAGIC_BYTE_1) {
            val payloadLen = packet.size - 5
            val out = ByteArray(payloadLen)
            System.arraycopy(packet, 5, out, 0, payloadLen)
            return out
        }
        return packet
    }
}
