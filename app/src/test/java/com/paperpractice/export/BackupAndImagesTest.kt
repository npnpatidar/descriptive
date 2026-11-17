package com.paperpractice.export

import com.paperpractice.domain.CategoryContent
import com.paperpractice.domain.ImageContent
import com.paperpractice.domain.PaperContent
import com.paperpractice.domain.PaperFormatException
import com.paperpractice.domain.PaperJsonParser
import com.paperpractice.domain.QuestionContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class BackupAndImagesTest {

    private val questionImage = ImageContent(
        id = "image-question",
        mimeType = "image/png",
        width = 12,
        height = 8,
        bytes = byteArrayOf(1, 2, 3, 4)
    )
    private val answerImage = ImageContent(
        id = "image-answer",
        mimeType = "image/jpeg",
        width = 20,
        height = 10,
        bytes = byteArrayOf(9, 8, 7)
    )

    private fun paper() = PaperContent(
        paperName = "Image paper",
        categories = listOf(
            CategoryContent(
                categoryName = "Biology",
                questions = listOf(
                    QuestionContent(
                        questionText = "Label the cell.",
                        answerText = "See the diagram.",
                        questionImages = listOf(questionImage),
                        answerImages = listOf(answerImage)
                    )
                )
            )
        )
    )

    @Test
    fun `json round trip keeps both image sets`() {
        val json = JsonExporter.build(paper(), includeAnswers = true)
        val parsed = PaperJsonParser.parse(json)
        val question = parsed.categories.first().questions.first()

        assertEquals("image/png", question.questionImages.single().mimeType)
        assertEquals(12, question.questionImages.single().width)
        assertArrayEquals(
            questionImage.bytes,
            question.questionImages.single().bytes
        )
        assertEquals("image/jpeg", question.answerImages.single().mimeType)
        assertArrayEquals(answerImage.bytes, question.answerImages.single().bytes)
    }

    @Test
    fun `answer images are removed when answers are excluded`() {
        val json = JsonExporter.build(paper(), includeAnswers = false)
        val parsed = PaperJsonParser.parse(json)
        val question = parsed.categories.first().questions.first()

        assertTrue(question.questionImages.isNotEmpty())
        assertTrue(question.answerImages.isEmpty())
    }

    @Test
    fun `html embeds images as data urls`() {
        val html = HtmlExporter.build(paper(), includeAnswers = true)
        assertTrue(html.contains("data:image/png;base64,"))
        assertTrue(html.contains("data:image/jpeg;base64,"))
        assertTrue(html.contains("class=\"question-image\""))
        assertTrue(html.contains("class=\"answer-image\""))
    }

    @Test
    fun `docx includes image parts and relationships`() {
        val output = ByteArrayOutputStream()
        DocxExporter.write(paper(), includeAnswers = true, answersAtEnd = false, output = output)

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val bytes = zip.readBytes()
                entries[entry.name] = String(bytes, Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }

        assertTrue(entries.containsKey("word/media/image1.png"))
        assertTrue(entries.containsKey("word/media/image2.jpeg"))
        assertTrue(entries.containsKey("word/_rels/document.xml.rels"))
        assertTrue(entries.getValue("word/document.xml").contains("r:embed=\"rId2\""))
        assertTrue(entries.getValue("word/document.xml").contains("r:embed=\"rId3\""))
    }

    @Test
    fun `docx without answers does not embed answer images`() {
        val output = ByteArrayOutputStream()
        DocxExporter.write(paper(), includeAnswers = false, answersAtEnd = false, output = output)

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = String(zip.readBytes(), Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }

        assertTrue(entries.containsKey("word/media/image1.png"))
        assertTrue(entries.none { it.key == "word/media/image2.jpeg" })
        assertTrue(!entries.getValue("word/document.xml").contains("r:embed=\"rId3\""))
    }

    @Test
    fun `backup wrapper round trips multiple papers and images`() {
        val second = PaperContent(
            paperName = "Second paper",
            categories = listOf(CategoryContent("Empty", emptyList()))
        )
        val backup = PaperBackup.build(listOf(paper(), second))
        val restored = PaperBackup.parse(backup)

        assertEquals(2, restored.size)
        assertEquals("Image paper", restored[0].paperName)
        assertEquals("Second paper", restored[1].paperName)
        assertArrayEquals(
            questionImage.bytes,
            restored[0].categories[0].questions[0].questionImages[0].bytes
        )
    }

    @Test
    fun `backup restore also accepts a plain paper export`() {
        val restored = PaperBackup.parse(JsonExporter.build(paper(), true))
        assertEquals(1, restored.size)
        assertEquals("Image paper", restored.single().paperName)
    }

    @Test
    fun `invalid backup format is rejected`() {
        try {
            PaperBackup.parse("""{"format":"something-else","papers":[]}""")
            fail("Expected PaperFormatException")
        } catch (e: PaperFormatException) {
            assertTrue(e.message.orEmpty().contains("not a Paper Practice backup"))
        }
    }

    @Test
    fun `backup from a newer app version is rejected`() {
        try {
            PaperBackup.parse(
                """{"format":"paper-practice-backup","version":99,"papers":[]}"""
            )
            fail("Expected PaperFormatException")
        } catch (e: PaperFormatException) {
            assertTrue(e.message.orEmpty().contains("newer version"))
        }
    }

    @Test
    fun `unsafe image mime type is rejected`() {
        val json = """
            {
              "paperName": "Bad image",
              "categories": [
                {
                  "categoryName": "One",
                  "questions": [
                    {
                      "questionText": "Q",
                      "answerText": "A",
                      "questionImages": [
                        {"mimeType": "image/png\" onerror=\"x", "data": "AQID"}
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        try {
            PaperJsonParser.parse(json)
            fail("Expected PaperFormatException")
        } catch (e: PaperFormatException) {
            assertTrue(e.message.orEmpty().contains("questionImages[0].mimeType"))
        }
    }

    @Test
    fun `invalid base64 image is rejected with a field path`() {
        val json = """
            {
              "paperName": "Bad image",
              "categories": [
                {
                  "categoryName": "One",
                  "questions": [
                    {
                      "questionText": "Q",
                      "answerText": "A",
                      "questionImages": [
                        {"mimeType": "image/png", "data": "not base64!"}
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        try {
            PaperJsonParser.parse(json)
            fail("Expected PaperFormatException")
        } catch (e: PaperFormatException) {
            assertTrue(e.message.orEmpty().contains("questionImages[0].data"))
        }
    }

    @Test
    fun `backup containing no papers is rejected`() {
        try {
            PaperBackup.parse(PaperBackup.build(emptyList()))
            fail("Expected PaperFormatException")
        } catch (e: PaperFormatException) {
            assertTrue(e.message.orEmpty().contains("no papers", ignoreCase = true))
        }
    }

    @Test
    fun `backup with the next version is rejected with a clear message`() {
        val backup = """
            {"format":"paper-practice-backup","version":${PaperBackup.VERSION + 1},
             "papers":[{"paperName":"P","categories":[]}]}
        """.trimIndent()
        try {
            PaperBackup.parse(backup)
            fail("Expected PaperFormatException")
        } catch (e: PaperFormatException) {
            val message = e.message.orEmpty()
            assertTrue(message.contains("newer version"))
            assertTrue(message.contains("cannot be restored"))
        }
    }

    @Test
    fun `restore accepts a plain paper export with nested categories`() {
        val plain = PaperContent(
            paperName = "Plain nested paper",
            categories = listOf(
                CategoryContent(
                    categoryName = "Science",
                    questions = emptyList(),
                    categories = listOf(
                        CategoryContent(
                            categoryName = "Physics",
                            questions = listOf(
                                QuestionContent("Q1", "A1"),
                                QuestionContent("Q2", "A2")
                            )
                        )
                    )
                ),
                CategoryContent(
                    categoryName = "Arts",
                    questions = listOf(QuestionContent("Q3", "A3"))
                )
            )
        )

        val restored = PaperBackup.parse(JsonExporter.build(plain, includeAnswers = true))

        assertEquals(1, restored.size)
        assertEquals("Plain nested paper", restored.single().paperName)
        assertEquals(3, restored.single().questionCount)
        assertEquals("Science", restored.single().categories[0].categoryName)
        assertEquals(
            listOf("Q1", "Q2"),
            restored.single().categories[0].categories.single().questions.map {
                it.questionText
            }
        )
        assertEquals("Q3", restored.single().categories[1].questions.single().questionText)
    }

    @Test
    fun `backup round trip preserves every paper name and question`() {
        val papers = listOf(
            PaperContent(
                paperName = "First",
                categories = listOf(
                    CategoryContent(
                        "C1",
                        listOf(QuestionContent("Q1", "A1"), QuestionContent("Q2", "A2"))
                    )
                )
            ),
            PaperContent(
                paperName = "Second",
                categories = listOf(
                    CategoryContent("C2", listOf(QuestionContent("Q3", "A3")))
                )
            ),
            PaperContent(
                paperName = "Third",
                categories = listOf(CategoryContent("C3", emptyList()))
            )
        )

        val restored = PaperBackup.parse(PaperBackup.build(papers))

        assertEquals(papers.size, restored.size)
        assertEquals(listOf("First", "Second", "Third"), restored.map { it.paperName })
        assertEquals(
            listOf(listOf("Q1", "Q2"), listOf("Q3"), emptyList<String>()),
            restored.map { paper ->
                paper.categories.single().questions.map { it.questionText }
            }
        )
        assertEquals(
            listOf("A1", "A2"),
            restored[0].categories.single().questions.map { it.answerText }
        )
    }

    @Test
    fun `backup write matches build apart from the timestamp`() {
        val papers = listOf(
            paper(),
            PaperContent("Second", listOf(CategoryContent("Empty", emptyList())))
        )
        val fromBuild = PaperBackup.build(papers)
        val fromWrite = ByteArrayOutputStream().also {
            PaperBackup.write(it, papers)
        }.toString(Charsets.UTF_8)

        // Both paths stamp exportedAt with the current time, so compare the
        // decoded objects with that one field removed.
        fun withoutTimestamp(json: String): JsonObject = JsonObject(
            Json.parseToJsonElement(json).jsonObject.filterKeys { it != "exportedAt" }
        )
        assertEquals(withoutTimestamp(fromBuild), withoutTimestamp(fromWrite))

        assertEquals(2, PaperBackup.parse(fromBuild).size)
        assertEquals(2, PaperBackup.parse(fromWrite).size)
    }
}
