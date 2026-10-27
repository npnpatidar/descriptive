package com.paperpractice.domain

/**
 * Reads pixel dimensions from common image headers without decoding the image.
 * Imported JSON may omit optional dimensions, and Word documents need the real
 * aspect ratio rather than a generic fallback.
 */
object ImageMetadata {

    fun dimensions(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < 10) return null
        return when {
            isPng(bytes) -> pngDimensions(bytes)
            isJpeg(bytes) -> jpegDimensions(bytes)
            isGif(bytes) -> gifDimensions(bytes)
            isWebp(bytes) -> webpDimensions(bytes)
            isBmp(bytes) -> bmpDimensions(bytes)
            else -> null
        }
    }

    private fun isPng(bytes: ByteArray): Boolean =
        bytes.size >= 24 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() &&
            bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() &&
            bytes[7] == 0x0A.toByte() &&
            bytes[12] == 0x49.toByte() &&
            bytes[13] == 0x48.toByte() &&
            bytes[14] == 0x44.toByte() &&
            bytes[15] == 0x52.toByte()

    private fun pngDimensions(bytes: ByteArray): Pair<Int, Int>? {
        val width = intBigEndian(bytes, 16)
        val height = intBigEndian(bytes, 20)
        return valid(width, height)
    }

    private fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte()

    private fun jpegDimensions(bytes: ByteArray): Pair<Int, Int>? {
        var index = 2
        while (index + 4 <= bytes.size) {
            if (bytes[index] != 0xFF.toByte()) {
                index++
                continue
            }
            val marker = bytes[index + 1].toInt() and 0xFF
            if (marker == 0xFF) {
                index++
                continue
            }
            if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) {
                index += 2
                continue
            }
            if (marker == 0xD9 || marker == 0xDA) break

            val length = intBigEndian16(bytes, index + 2)
            if (length < 2) break
            val isStartOfFrame = marker in 0xC0..0xCF &&
                marker != 0xC4 && marker != 0xC8 && marker != 0xCC
            if (isStartOfFrame && index + 9 <= bytes.size) {
                val height = intBigEndian16(bytes, index + 5)
                val width = intBigEndian16(bytes, index + 7)
                return valid(width, height)
            }
            index += 2 + length
        }
        return null
    }

    private fun isGif(bytes: ByteArray): Boolean =
        bytes.size >= 10 &&
            bytes[0] == 'G'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == '8'.code.toByte()

    private fun gifDimensions(bytes: ByteArray): Pair<Int, Int>? {
        val width = intLittleEndian(bytes, 6, 2)
        val height = intLittleEndian(bytes, 8, 2)
        return valid(width, height)
    }

    private fun isWebp(bytes: ByteArray): Boolean =
        bytes.size >= 30 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte()

    private fun webpDimensions(bytes: ByteArray): Pair<Int, Int>? {
        return when {
            bytes.copyOfRange(12, 16).contentEquals("VP8 ".toByteArray()) -> {
                if (bytes[23] != 0x9D.toByte() ||
                    bytes[24] != 0x01.toByte() ||
                    bytes[25] != 0x2A.toByte()
                ) {
                    return null
                }
                val width = intLittleEndian(bytes, 26, 2) and 0x3FFF
                val height = intLittleEndian(bytes, 28, 2) and 0x3FFF
                valid(width, height)
            }

            bytes.copyOfRange(12, 16).contentEquals("VP8L".toByteArray()) -> {
                if (bytes[20] != 0x2F.toByte()) return null
                val bits = intLittleEndian(bytes, 21, 4)
                val width = (bits and 0x3FFF) + 1
                val height = ((bits ushr 14) and 0x3FFF) + 1
                valid(width, height)
            }

            bytes.copyOfRange(12, 16).contentEquals("VP8X".toByteArray()) -> {
                val width = intLittleEndian(bytes, 24, 3) + 1
                val height = intLittleEndian(bytes, 27, 3) + 1
                valid(width, height)
            }

            else -> null
        }
    }

    private fun isBmp(bytes: ByteArray): Boolean =
        bytes.size >= 26 &&
            bytes[0] == 'B'.code.toByte() &&
            bytes[1] == 'M'.code.toByte()

    private fun bmpDimensions(bytes: ByteArray): Pair<Int, Int>? {
        val headerSize = intLittleEndian(bytes, 14, 4)
        val width: Int
        val height: Int
        if (headerSize == 12) {
            width = intLittleEndian(bytes, 18, 2)
            height = intLittleEndian(bytes, 20, 2)
        } else if (headerSize >= 40) {
            width = intLittleEndian(bytes, 18, 4)
            height = kotlin.math.abs(intLittleEndian(bytes, 22, 4))
        } else {
            return null
        }
        return valid(width, height)
    }

    private fun intBigEndian(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 4 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun intBigEndian16(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 2 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 8) or
            (bytes[offset + 1].toInt() and 0xFF)
    }

    private fun intLittleEndian(bytes: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length !in 1..4 || offset + length > bytes.size) return 0
        var value = 0
        for (index in 0 until length) {
            value = value or ((bytes[offset + index].toInt() and 0xFF) shl (index * 8))
        }
        return value
    }

    private fun valid(width: Int, height: Int): Pair<Int, Int>? =
        if (width > 0 && height > 0) width to height else null
}
