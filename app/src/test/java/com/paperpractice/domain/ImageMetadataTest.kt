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
