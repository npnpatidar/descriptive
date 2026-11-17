package com.paperpractice.domain

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageMetadataTest {

    @Test
    fun `reads png dimensions`() {
        val png = pngHeader(width = 1200, height = 800)
        assertEquals(1200 to 800, ImageMetadata.dimensions(png))
    }

    @Test
    fun `reads jpeg dimensions`() {
        val jpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE0.toByte(), 0x00, 0x04, 0x00, 0x00,
            0xFF.toByte(), 0xC0.toByte(), 0x00, 0x11, 0x08,
            0x00, 0x64,
            0x00, 0xC8.toByte(),
            0x03, 0x01, 0x11, 0x00, 0x02, 0x11, 0x00, 0x03, 0x11, 0x00
        )
        assertEquals(200 to 100, ImageMetadata.dimensions(jpeg))
    }

    @Test
    fun `reads gif dimensions`() {
        val gif = "GIF89a".toByteArray() +
            byteArrayOf(0x40, 0x01, 0xF0.toByte(), 0x00)
        assertEquals(320 to 240, ImageMetadata.dimensions(gif))
    }

    @Test
    fun `reads bmp dimensions including top-down height`() {
        val bmp = ByteArray(54).also { bytes ->
            bytes[0] = 'B'.code.toByte()
            bytes[1] = 'M'.code.toByte()
            bytes[14] = 40
            bytes[18] = 0x2C
            bytes[19] = 0x01
            bytes[22] = 0x20
            bytes[23] = 0xFF.toByte()
            bytes[24] = 0xFF.toByte()
            bytes[25] = 0xFF.toByte()
        }
        assertEquals(300 to 224, ImageMetadata.dimensions(bmp))
    }

    @Test
    fun `rejects unknown headers`() {
        assertNull(ImageMetadata.dimensions(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)))
    }

    @Test
    fun `parser infers omitted image dimensions`() {
        val png = pngHeader(width = 640, height = 480)
        val encoded = Base64.getEncoder().encodeToString(png)
        val json = """
            {
              "paperName": "Inferred image",
              "categories": [
                {
                  "categoryName": "Diagrams",
                  "questions": [
                    {
                      "questionText": "See the image.",
                      "answerText": "",
                      "questionImages": [
                        {
                          "mimeType": "image/png",
                          "data": "$encoded"
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val image = PaperJsonParser.parse(json)
            .categories.first()
            .questions.first()
            .questionImages.single()
        assertEquals(640, image.width)
        assertEquals(480, image.height)
    }

    @Test
    fun `jpeg exif orientation six swaps inferred dimensions`() {
        // TIFF/IFD0 little-endian block with a single Orientation (0x0112)
        // tag whose value is 6 (rotate 90 degrees -> transpose width/height).
        val tiff = byteArrayOf(
            'I'.code.toByte(), 'I'.code.toByte(),
            0x2A.toByte(), 0x00,
            0x08, 0x00, 0x00, 0x00, // IFD offset
            0x01, 0x00,             // one directory entry
            0x12, 0x01,             // tag 0x0112 (Orientation)
            0x03, 0x00,             // type SHORT
            0x01, 0x00, 0x00, 0x00, // count = 1
            0x06, 0x00, 0x00, 0x00, // value = 6
            0x00, 0x00, 0x00, 0x00  // no next IFD
        )
        val exif = "Exif\u0000\u0000".toByteArray()
        val appLength = 2 + exif.size + tiff.size
        val jpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE1.toByte(),
            (appLength ushr 8).toByte(), appLength.toByte()
        ) + exif + tiff + byteArrayOf(
            0xFF.toByte(), 0xC0.toByte(),
            0x00, 0x11, 0x08,
            0x00, 0x64,          // stored height 100
            0x00, 0xC8.toByte(), // stored width 200
            0x03,
            0x01, 0x11, 0x00, 0x02, 0x11, 0x00, 0x03, 0x11, 0x00
        )

        assertEquals(100 to 200, ImageMetadata.dimensions(jpeg))
    }

    @Test
    fun `short or truncated headers return null without throwing`() {
        assertNull(ImageMetadata.dimensions(byteArrayOf()))
        assertNull(ImageMetadata.dimensions(byteArrayOf(0xFF.toByte(), 0xD8.toByte())))

        // A JPEG whose APP1 length runs past the available bytes.
        val truncatedJpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10, 0x00, 0x00,
            0xFF.toByte(), 0xC0.toByte(), 0x00
        )
        assertNull(ImageMetadata.dimensions(truncatedJpeg))

        // A 25-byte VP8 WebP is too short to hold the full VP8 key frame
        // header; it must return null rather than throw.
        val shortVp8 = byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            0x00, 0x00, 0x00, 0x00,
            'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(),
            'V'.code.toByte(), 'P'.code.toByte(), '8'.code.toByte(), ' '.code.toByte(),
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x9D.toByte(), 0x01
        )
        assertNull(ImageMetadata.dimensions(shortVp8))
    }

    @Test
    fun `accepts valid gif signatures and rejects unknown ones`() {
        val dimensions = byteArrayOf(0x40, 0x01, 0xF0.toByte(), 0x00)
        assertEquals(320 to 240, ImageMetadata.dimensions("GIF89a".toByteArray() + dimensions))
        assertEquals(320 to 240, ImageMetadata.dimensions("GIF87a".toByteArray() + dimensions))
        assertNull(ImageMetadata.dimensions("GIF8xx".toByteArray() + dimensions))
    }

    @Test
    fun `accepts minimal VP8L webp header`() {
        // 25 bytes are enough for the VP8L lossless header. bits encodes
        // width - 1 = 110 and height - 1 = 221 (little endian).
        val webp = byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            0x00, 0x00, 0x00, 0x00,
            'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(),
            'V'.code.toByte(), 'P'.code.toByte(), '8'.code.toByte(), 'L'.code.toByte(),
            0x05, 0x00, 0x00, 0x00, // chunk size
            0x2F.toByte(),          // VP8L signature
            0x6E, 0x40, 0x37, 0x00  // packed width/height
        )
        assertEquals(111 to 222, ImageMetadata.dimensions(webp))
    }

    private fun pngHeader(width: Int, height: Int): ByteArray {
        val bytes = ByteArray(24)
        byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52
        ).copyInto(bytes)
        bytes[16] = (width ushr 24).toByte()
        bytes[17] = (width ushr 16).toByte()
        bytes[18] = (width ushr 8).toByte()
        bytes[19] = width.toByte()
        bytes[20] = (height ushr 24).toByte()
        bytes[21] = (height ushr 16).toByte()
        bytes[22] = (height ushr 8).toByte()
        bytes[23] = height.toByte()
        return bytes
    }
}
