package com.paperpractice.export

import com.paperpractice.data.CategoryDetail
import com.paperpractice.data.PaperDetail
import com.paperpractice.data.QuestionDetail
import com.paperpractice.domain.CategoryContent
import com.paperpractice.domain.PaperContent
import com.paperpractice.domain.PaperJsonParser
import com.paperpractice.domain.QuestionContent
import com.paperpractice.domain.SessionQuestion
import com.paperpractice.domain.TestSession
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportersTest {

    private val content = PaperContent(
        paperName = "History & Science <Test>",
        categories = listOf(
            CategoryContent(
                categoryName = "History & Politics",
                questions = listOf(
                    QuestionContent(
                        questionText = "Explain the causes of World War I.",
                        answerText = "Alliances, militarism & nationalism."
                    ),
                    QuestionContent(
                        questionText = "Question with\nnew lines",
                        answerText = ""
                    )
                )
            ),
            CategoryContent(
                categoryName = "Science",
                questions = listOf(
                    QuestionContent("State Newton's second law.", "F = ma.")
                )
            )
        )
    )

    @Test
    fun jsonRoundTripsThroughImportParser() {
        val json = JsonExporter.build(content, includeAnswers = true)
        val reparsed = PaperJsonParser.parse(json)

        assertEquals(content, reparsed)
    }

    @Test
    fun jsonWithoutAnswersIsStillImportable() {
        val json = JsonExporter.build(content, includeAnswers = false)
        val reparsed = PaperJsonParser.parse(json)

        assertEquals(content.paperName, reparsed.paperName)
        assertEquals(content.questionCount, reparsed.questionCount)
        assertTrue(reparsed.categories.flatMap { it.questions }.all { it.answerText.isEmpty() })
    }

    @Test
    fun htmlEscapesContentAndHidesAnswersInitially() {
        val html = HtmlExporter.build(content, includeAnswers = true)

        assertTrue(html.contains("History &amp; Science &lt;Test&gt;"))
        assertTrue(html.contains("History &amp; Politics"))
        assertTrue(html.contains("Alliances, militarism &amp; nationalism."))
        assertTrue(html.contains("id=\"toggleAnswers\""))
        assertTrue(html.contains("Show Answers"))
        assertTrue(html.contains("Hide Answers"))
        assertTrue(html.contains("class=\"answer\" hidden"))
        assertTrue(html.contains("question-list"))
        assertTrue(html.contains("<script>"))
        assertFalse(html.contains("src=\"http"))
        assertFalse(html.contains("href=\"http"))
    }

    @Test
    fun htmlWithoutAnswersContainsNoAnswerControlsOrAnswerText() {
        val html = HtmlExporter.build(content, includeAnswers = false)

        assertTrue(html.contains("Explain the causes of World War I."))
        assertFalse(html.contains("Alliances, militarism"))
        assertFalse(html.contains("toggleAnswers"))
        assertFalse(html.contains("Show Answers"))
    }

    @Test
    fun htmlShowsAllQuestionsWithSequentialNumbers() {
        val html = HtmlExporter.build(content, includeAnswers = true)

        assertTrue(html.contains(">1</span>"))
        assertTrue(html.contains(">2</span>"))
        assertTrue(html.contains(">3</span>"))
    }

    @Test
    fun currentTestExportOnlyContainsSelectedQuestionsAndPreservesOrder() {
        val paper = PaperDetail(
            id = "p",
            name = "Paper",
            categories = listOf(
                CategoryDetail(
                    "c1",
                    "History",
                    listOf(
                        QuestionDetail("q1", "Q1", "A1"),
                        QuestionDetail("q2", "Q2", "A2")
                    )
                )
            )
        )
        val session = TestSession(
            paperId = "p",
            paperName = "Paper",
            createdAt = 0,
            questions = listOf(
                SessionQuestion(1, "q2", "c1", "History", "Q2", "A2")
            )
        )

        val export = ExportContentBuilder.currentTest(paper, session)

        assertEquals("Paper", export.paperName)
        assertEquals(1, export.categories.size)
        assertEquals("History", export.categories[0].categoryName)
        assertEquals(listOf("Q2"), export.categories[0].questions.map { it.questionText })
        assertEquals(listOf("A2"), export.categories[0].questions.map { it.answerText })
    }

    @Test
    fun selectedCategoryExportKeepsAllQuestionsInThatCategory() {
        val paper = PaperDetail(
            id = "p",
            name = "Paper",
            categories = listOf(
                CategoryDetail("c1", "One", listOf(QuestionDetail("q1", "Q1", "A1"))),
                CategoryDetail("c2", "Two", listOf(QuestionDetail("q2", "Q2", "A2")))
            )
        )

        val export = ExportContentBuilder.selectedCategories(paper, setOf("c2"))

        assertEquals(1, export.categories.size)
        assertEquals("Two", export.categories[0].categoryName)
        assertEquals("Q2", export.categories[0].questions.single().questionText)
    }

    @Test
    fun removingAnswersKeepsCategoriesAndQuestions() {
        val stripped = ExportContentBuilder.withoutAnswers(content)

        assertEquals(content.paperName, stripped.paperName)
        assertEquals(content.categories.map { it.categoryName }, stripped.categories.map {
            it.categoryName
        })
        assertEquals(content.questionCount, stripped.questionCount)
        assertTrue(stripped.categories.flatMap { it.questions }.all {
            it.answerText.isEmpty()
        })
    }

    @Test
    fun docxIsAValidZipWithWellFormedWordXml() {
        val bytes = ByteArrayOutputStream().also {
            DocxExporter.write(content, includeAnswers = true, output = it)
        }.toByteArray()

        val entries = unzip(bytes)
        assertNotNull(entries["[Content_Types].xml"])
        assertNotNull(entries["_rels/.rels"])
        val document = entries["word/document.xml"] ?: error("document.xml missing")
        val text = document.toString(Charsets.UTF_8)

        assertTrue(text.contains("History &amp; Science &lt;Test&gt;"))
        assertTrue(text.contains("Alliances, militarism &amp; nationalism."))
        assertTrue(text.contains("Answer:"))
        assertTrue(text.contains("<w:br/>"))

        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val parsed = factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(document))
        assertEquals("document", parsed.documentElement.localName)
    }

    @Test
    fun docxCanOmitAnswers() {
        val bytes = ByteArrayOutputStream().also {
            DocxExporter.write(content, includeAnswers = false, output = it)
        }.toByteArray()

        val text = unzip(bytes)["word/document.xml"]!!.toString(Charsets.UTF_8)

        assertTrue(text.contains("Explain the causes of World War I."))
        assertFalse(text.contains("Alliances, militarism"))
        assertFalse(text.contains("Answer:"))
    }

    @Test
    fun docxReportsMissingAnswerWithoutLosingQuestion() {
        val jsonBytes = ByteArrayOutputStream().also {
            DocxExporter.write(content, includeAnswers = true, output = it)
        }.toByteArray()

        val text = unzip(jsonBytes)["word/document.xml"]!!.toString(Charsets.UTF_8)
        assertTrue(text.contains("(No answer provided)"))
    }

    @Test
    fun docxBytesAreDeterministicEnoughToRoundTrip() {
        val first = ByteArrayOutputStream().also {
            DocxExporter.write(content, includeAnswers = true, output = it)
        }.toByteArray()
        val second = ByteArrayOutputStream().also {
            DocxExporter.write(content, includeAnswers = true, output = it)
        }.toByteArray()

        // The ZIP container carries timestamps, so compare the document part.
        assertArrayEquals(
            unzip(first)["word/document.xml"],
            unzip(second)["word/document.xml"]
        )
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                result[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        return result
    }
}
