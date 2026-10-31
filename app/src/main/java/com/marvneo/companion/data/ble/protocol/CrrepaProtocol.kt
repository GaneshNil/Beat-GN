package com.marvneo.companion.data.ble.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar

object CrrepaProtocol {

    // Command Opcodes
    const val CMD_SYNC_TIME: Byte = 0x01
    const val CMD_QUERY_STEPS: Byte = 0x11
    const val CMD_QUERY_BATTERY: Byte = 0x03
    const val CMD_HEART_RATE: Byte = 0x12
    const val CMD_BLOOD_OXYGEN: Byte = 0x15
    const val CMD_SLEEP: Byte = 0x18
    const val CMD_FIND_WATCH: Byte = 0x07
    const val CMD_CAMERA_CONTROL: Byte = 0x09
    const val CMD_RAISE_TO_WAKE: Byte = 0x05
    const val CMD_TIME_FORMAT: Byte = 0x0A
    const val CMD_VIBRATION: Byte = 0x20
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
     * Builds a Time Synchronization packet.
     * Format: [0x01, YearLow, YearHigh, Month, Day, Hour, Minute, Second]
     */
    fun createTimeSyncPacket(calendar: Calendar = Calendar.getInstance()): ByteArray {
        val year = calendar.get(Calendar.YEAR)
        val month = (calendar.get(Calendar.MONTH) + 1).toByte()
        val day = calendar.get(Calendar.DAY_OF_MONTH).toByte()
        val hour = calendar.get(Calendar.HOUR_OF_DAY).toByte()
        val min = calendar.get(Calendar.MINUTE).toByte()
        val sec = calendar.get(Calendar.SECOND).toByte()

        return byteArrayOf(
            CMD_SYNC_TIME,
            (year and 0xFF).toByte(),
            ((year shr 8) and 0xFF).toByte(),
            month,
            day,
            hour,
            min,
            sec
        )
    }

    /**
     * Builds a Battery Level query packet.
     */
    fun createBatteryQueryPacket(): ByteArray = byteArrayOf(CMD_QUERY_BATTERY, 0x00)

    /**
     * Builds a Step query packet.
     */
    fun createStepsQueryPacket(): ByteArray = byteArrayOf(CMD_QUERY_STEPS, 0x01, 0x00)

    /**
     * Builds a Heart Rate measure trigger packet.
     * @param enable true to start measurement, false to stop.
     */
    fun createHeartRateControlPacket(enable: Boolean): ByteArray =
        byteArrayOf(CMD_HEART_RATE, if (enable) 0x01 else 0x00)

    /**
     * Builds an SpO2 measure trigger packet.
     */
    fun createSpO2ControlPacket(enable: Boolean): ByteArray =
        byteArrayOf(CMD_BLOOD_OXYGEN, if (enable) 0x01 else 0x00)

    /**
     * Builds a Find Watch vibration packet.
     */
    fun createFindWatchPacket(enable: Boolean): ByteArray =
        byteArrayOf(CMD_FIND_WATCH, 0x01, if (enable) 0x01 else 0x00)

    /**
     * Builds Camera shutter mode packet.
     */
    fun createCameraModePacket(open: Boolean): ByteArray =
        byteArrayOf(CMD_CAMERA_CONTROL, if (open) 0x01 else 0x00)

    /**
     * Builds Raise-To-Wake wrist turn packet.
     */
    fun createRaiseToWakePacket(enabled: Boolean, startHour: Int = 8, endHour: Int = 22): ByteArray {
        return if (enabled) {
            byteArrayOf(CMD_RAISE_TO_WAKE, 0x01, startHour.toByte(), 0, endHour.toByte(), 0)
        } else {
            byteArrayOf(CMD_RAISE_TO_WAKE, 0x00)
        }
    }

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

    // Parsers for incoming telemetry

    data class StepsData(val steps: Int, val calories: Int, val distanceMeters: Int)
    data class BatteryData(val percentage: Int, val isCharging: Boolean)
    data class HeartRateData(val bpm: Int, val status: Int)
    data class SpO2Data(val percentage: Int, val status: Int)

    fun parseSteps(payload: ByteArray): StepsData? {
        if (payload.size < 12) return null
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val steps = buffer.getInt(0)
        val calories = buffer.getInt(4)
        val distance = buffer.getInt(8)
        return StepsData(steps, calories, distance)
    }

    fun parseBattery(payload: ByteArray): BatteryData? {
        if (payload.size < 2) return null
        val percentage = payload[1].toInt() and 0xFF
        val isCharging = if (payload.size > 2) payload[2].toInt() == 1 else false
        return BatteryData(percentage, isCharging)
    }

    fun parseHeartRate(payload: ByteArray): HeartRateData? {
        if (payload.size < 2) return null
        val bpm = payload[1].toInt() and 0xFF
        val status = if (payload.size > 2) payload[2].toInt() and 0xFF else 0
        return HeartRateData(bpm, status)
    }

    fun parseSpO2(payload: ByteArray): SpO2Data? {
        if (payload.size < 2) return null
        val percentage = payload[1].toInt() and 0xFF
        val status = if (payload.size > 2) payload[2].toInt() and 0xFF else 0
        return SpO2Data(percentage, status)
    }
}

