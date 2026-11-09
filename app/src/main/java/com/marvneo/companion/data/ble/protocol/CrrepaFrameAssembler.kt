package com.marvneo.companion.data.ble.protocol

/**
 * Incremental reassembler for the Crrepa `FE EA` frame format.
 *
 * Notifications are not guaranteed to line up with frame boundaries: a frame may arrive in
 * several notifications, and several frames may arrive in one. The reference SDK collects bytes
 * in `com.crrepa.l.a#n0` and only dispatches once the declared length has been reached; the same
 * behaviour is implemented here as a pure function so it can be verified without hardware.
 *
 * Behaviour on garbage is deliberately conservative: a byte that cannot start a frame is
 * discarded one at a time until the stream is back in sync. Nothing is guessed or zero-filled.
 */
class CrrepaFrameAssembler {

    private val buffer = ArrayList<Byte>(512)
    private var droppedBytes = 0

    /** Number of bytes discarded during resynchronisation since construction/last reset. */
    val dropped: Int get() = droppedBytes

    /** Bytes currently buffered but not yet forming a complete frame. */
    val pending: Int get() = buffer.size

    fun reset() {
        buffer.clear()
        droppedBytes = 0
    }

    /**
     * Appends [chunk] and returns every complete frame that is now available, in arrival order.
     */
    fun append(chunk: ByteArray): List<CrrepaProtocol.Frame> {
        if (chunk.isEmpty()) return emptyList()
        chunk.forEach { buffer.add(it) }

        val frames = ArrayList<CrrepaProtocol.Frame>(2)
        while (true) {
            if (buffer.size < CrrepaProtocol.HEADER_SIZE) break

            val b0 = buffer[0].toInt() and 0xFF
            val b1 = buffer[1].toInt() and 0xFF
            if (b0 != CrrepaProtocol.MAGIC_0 || b1 != CrrepaProtocol.MAGIC_1) {
                dropFirst()
                continue
            }

            val declared = buffer[3].toInt() and 0xFF
            if (declared < CrrepaProtocol.HEADER_SIZE) {
                dropFirst()
                continue
            }
            val version = buffer[2].toInt() and 0xFF
            if (version != CrrepaProtocol.VERSION_1 && version != CrrepaProtocol.VERSION_2) {
                // A `FE EA` byte pair with an impossible version is not a frame header; resync
                // instead of buffering up to 255 bytes of unrelated traffic.
                dropFirst()
                continue
            }
            if (buffer.size < declared) break

            val packet = ByteArray(declared)
            for (i in 0 until declared) packet[i] = buffer[i]

            repeat(declared) { dropFirst(counting = false) }

            val frame = CrrepaProtocol.parse(packet)
            if (frame != null) frames.add(frame)
        }
        return frames
    }

    private fun dropFirst(counting: Boolean = true) {
        if (buffer.isNotEmpty()) {
            buffer.removeAt(0)
            if (counting) droppedBytes++
        }
    }
}
