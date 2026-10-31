package com.marvneo.companion.data.ble.protocol

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WatchFaceConverter {

    /**
     * Converts a Bitmap to the watch's native RGB565 binary payload with Crrepa header.
     *
     * @param bitmap Scaled bitmap matching watch screen dimensions (e.g. 240x280).
     * @param timeX X position of the digital clock overlay.
     * @param timeY Y position of the digital clock overlay.
     * @param textColor RGB565 color for the clock text.
     * @return Complete binary image payload ready for chunked flashing.
     */
    fun convertBitmapToDialBinary(
        bitmap: Bitmap,
        timeX: Short = 40,
        timeY: Short = 40,
        textColor: Short = 0xFFFF.toShort()
    ): ByteArray {
        val width = bitmap.width
        val height = bitmap.height

        // 16-Byte Header
        val header = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(width.toShort())
        header.putShort(height.toShort())
        header.putShort(timeX)
        header.putShort(timeY)
        header.putShort(textColor)
        header.put(0.toByte()) // Compression: 0 = Raw RGB565, 1 = MiniLZO
        header.put(ByteArray(5)) // Padding

        val output = ByteArrayOutputStream()
        output.write(header.array())

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val pixelBuffer = ByteBuffer.allocate(pixels.size * 2).order(ByteOrder.BIG_ENDIAN)
        for (color in pixels) {
            // ARGB_8888 to RGB_565 conversion
            val r = (color shr 16 and 0xFF) shr 3
            val g = (color shr 8 and 0xFF) shr 2
            val b = (color and 0xFF) shr 3
            val rgb565 = ((r shl 11) or (g shl 5) or b).toShort()
            pixelBuffer.putShort(rgb565)
        }

        output.write(pixelBuffer.array())
        return output.toByteArray()
    }

    /**
     * Splits a binary dial payload into MTU-safe chunks.
     * @param payload Full binary array.
     * @param chunkSize Size per chunk (e.g. 240 bytes).
     */
    fun createChunks(payload: ByteArray, chunkSize: Int = 240): List<ByteArray> {
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < payload.size) {
            val length = minOf(chunkSize, payload.size - offset)
            val chunk = ByteArray(length)
            System.arraycopy(payload, offset, chunk, 0, length)
            chunks.add(chunk)
            offset += length
        }
        return chunks
    }
}

