package com.paperpractice.export

import com.paperpractice.data.CategoryDetail
import com.paperpractice.data.PaperDetail
import com.paperpractice.data.QuestionDetail
import com.paperpractice.domain.CategoryContent
import com.paperpractice.domain.ImageContent
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

    private val richContent = PaperContent(
        paperName = "Rich <Text>",
        categories = listOf(
            CategoryContent(
                categoryName = "Mixed",
                questions = listOf(
                    QuestionContent(
                        questionText = "**Bold** and *italic*",
                        answerText = "Use ++under++, ~~strike~~ and ==mark==.\n\n" +
                            "| Name | Value |\n" +
                            "| :--- | ---: |\n" +
                            "| **x** | \\frac{1}{2} |"
                    )
                )
            )
        )
    )

    private val taggedContent = PaperContent(
        paperName = "Tagged paper",
        categories = listOf(
            CategoryContent(
                categoryName = "History",
                questions = listOf(
                    QuestionContent(
                        questionText = "Tagged question one?",
                        answerText = "Answer one",
                        tags = listOf("WW1", "Causes")
                    ),
                    QuestionContent(
                        questionText = "Tagged question two?",
                        answerText = "Answer two",
                        tags = listOf("WW1")
                    )
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
    fun jsonExportContainsTagsAndRoundTripsWithoutLoss() {
        val json = JsonExporter.build(taggedContent, includeAnswers = true)

        assertTrue(json.contains("\"tags\""))
        assertTrue(json.contains("WW1"))
        assertTrue(json.contains("Causes"))
        assertEquals(taggedContent, PaperJsonParser.parse(json))
    }

    @Test
    fun jsonOmitsTagsKeyWhenQuestionHasNoTags() {
        val json = JsonExporter.build(content, includeAnswers = true)

        assertFalse(json.contains("\"tags\""))
    }

    @Test
    fun withoutAnswersKeepsTags() {
        val stripped = ExportContentBuilder.withoutAnswers(taggedContent)
        assertEquals(
            listOf(listOf("WW1", "Causes"), listOf("WW1")),
            stripped.categories.single().questions.map { it.tags }
        )

        val parsed = PaperJsonParser.parse(
            JsonExporter.build(taggedContent, includeAnswers = false)
        )
        assertEquals(
            listOf(listOf("WW1", "Causes"), listOf("WW1")),
            parsed.categories.single().questions.map { it.tags }
        )
        assertTrue(parsed.categories.flatMap { it.questions }.all { it.answerText.isEmpty() })
    }

    @Test
    fun everyExportScopePreservesTags() {
        val paper = PaperDetail(
            id = "p",
            name = "Tagged",
            categories = listOf(
                CategoryDetail(
                    id = "c1",
                    name = "Science",
                    questions = listOf(
                        QuestionDetail("q1", "Q1", "A1", tags = listOf("Physics"))
                    ),
                    children = listOf(
                        CategoryDetail(
                            id = "c2",
                            name = "Physics",
                            questions = listOf(
                                QuestionDetail(
                                    "q2", "Q2", "A2", tags = listOf("Mechanics", "Motion")
                                )
                            )
                        )
                    )
                )
            )
        )
        val session = TestSession(
            paperId = "p",
            paperName = "Tagged",
            createdAt = 0,
            questions = listOf(
                SessionQuestion(
                    number = 1,
                    questionId = "q2",
                    categoryId = "c2",
                    categoryName = "Science / Physics",
                    questionText = "Q2",
                    answerText = "A2",
                    categoryPath = listOf("Science", "Physics"),
                    // Deliberately distinct from the paper's q2 tags
                    // ("Mechanics"/"Motion") so this test proves the
                    // current-test scope snapshots the session, not the paper.
                    tags = listOf("SnapshotTag")
                )
            )
        )

        val entire = ExportContentBuilder.entirePaper(paper)
        val selected = ExportContentBuilder.selectedCategories(paper, setOf("c2"))
        val current = ExportContentBuilder.currentTest(paper, session)

        assertEquals(
            listOf("Physics"),
            entire.categories.single().questions.single().tags
        )
        assertEquals(
            listOf("Mechanics", "Motion"),
            entire.categories.single().categories.single().questions.single().tags
        )
        assertEquals(
            listOf("Mechanics", "Motion"),
            selected.categories.single().categories.single().questions.single().tags
        )
        // The current-test scope must use the session's distinct tag set. An
        // implementation that ignored the session and re-read the paper's q2
        // would produce "Mechanics"/"Motion" here and fail this assertion.
        assertEquals(
            listOf("SnapshotTag"),
            current.categories.single().categories.single().questions.single().tags
        )

        // All three scopes survive export -> re-import without losing tags.
        listOf(entire, selected, current).forEach { scope ->
            assertEquals(scope, PaperJsonParser.parse(JsonExporter.build(scope, true)))
        }

        // The exported AND re-imported current-test JSON must carry exactly the
        // session tags, never the paper question's tags.
        val reparsedCurrent = PaperJsonParser.parse(JsonExporter.build(current, true))
        assertEquals(
            listOf("SnapshotTag"),
            reparsedCurrent.categories.single().categories.single().questions.single().tags
        )
        assertFalse(
            reparsedCurrent.categories.single().categories.single()
                .questions.single().tags.contains("Mechanics")
        )
    }

    @Test
    fun taggedContentSurvivesBuildWriteByteParity() {
        listOf(true, false).forEach { includeAnswers ->
            val fromBuild = JsonExporter
                .build(taggedContent, includeAnswers).toByteArray(Charsets.UTF_8)
            val fromWrite = ByteArrayOutputStream().also {
                JsonExporter.write(it, taggedContent, includeAnswers)
            }.toByteArray()

            assertArrayEquals(
                "JSON bytes differ with tags (includeAnswers=$includeAnswers)",
                fromBuild,
                fromWrite
            )
        }
    }

    @Test
    fun taggedContentDoesNotBreakHtmlAndDocxExports() {
        val html = HtmlExporter.build(taggedContent, includeAnswers = true)
        assertTrue(html.contains("Tagged question one?"))
        assertTrue(html.contains("Tagged question two?"))
        assertEquals(2, Regex("<article class=\"question\">").findAll(html).count())

        val docx = ByteArrayOutputStream().also {
            DocxExporter.write(taggedContent, includeAnswers = true, answersAtEnd = false, output = it)
        }.toByteArray()
        val document = unzip(docx)["word/document.xml"] ?: error("document.xml missing")
        val text = document.toString(Charsets.UTF_8)
        assertTrue(text.contains("Tagged question one?"))
        assertTrue(text.contains("Tagged question two?"))

        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        assertEquals(
            "document",
            factory.newDocumentBuilder()
                .parse(ByteArrayInputStream(document)).documentElement.localName
        )
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
    fun richMarkupRoundTripsThroughJson() {
        val json = JsonExporter.build(richContent, includeAnswers = true)
        val reparsed = PaperJsonParser.parse(json)

        assertEquals(richContent, reparsed)
        assertTrue(reparsed.categories.flatMap { it.questions }.single().answerText.contains("**"))
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
    fun nestedCategoriesRoundTripThroughJsonAndKeepRenderedHierarchy() {
        val nested = PaperContent(
            paperName = "Nested",
            categories = listOf(
                CategoryContent(
                    categoryName = "Science",
                    questions = emptyList(),
                    categories = listOf(
                        CategoryContent(
                            categoryName = "Physics",
                            questions = listOf(QuestionContent("What is force?", "F = ma"))
                        )
                    )
                )
            )
        )

        val reparsed = PaperJsonParser.parse(JsonExporter.build(nested, includeAnswers = true))
        assertEquals(nested, reparsed)
        assertEquals(2, reparsed.categoryCount)
        assertEquals(1, reparsed.questionCount)

        val html = HtmlExporter.build(nested, includeAnswers = true)
        assertFalse(html.contains("<h2>Science</h2>"))
        assertTrue(html.contains("<h2>Science/Physics</h2>"))
    }

    @Test
    fun renderedExportsShowFullPathsWithoutIndentingQuestions() {
        val deep = PaperContent(
            paperName = "Deep",
            categories = listOf(
                CategoryContent(
                    categoryName = "Science",
                    questions = emptyList(),
                    categories = listOf(
                        CategoryContent(
                            categoryName = "Physics",
                            questions = emptyList(),
                            categories = listOf(
                                CategoryContent(
                                    categoryName = "Mechanics",
                                    questions = listOf(QuestionContent("What is force?", "F = ma"))
                                )
                            )
                        )
                    )
                )
            )
        )

        val html = HtmlExporter.build(deep, includeAnswers = true)
        assertTrue(html.contains("<h2>Science/Physics/Mechanics</h2>"))
        assertTrue(html.contains("What is force?"))

        val docx = ByteArrayOutputStream().also {
            DocxExporter.write(
                deep,
                includeAnswers = true,
                answersAtEnd = false,
                output = it
            )
        }.toByteArray()
        val documentXml = unzip(docx)["word/document.xml"]!!.toString(Charsets.UTF_8)
        assertTrue(documentXml.contains("Science/Physics/Mechanics"))
        // Questions must stay at the left margin regardless of category depth.
        assertFalse(documentXml.contains("w:left=\"720\""))
        assertFalse(documentXml.contains("w:left=\"1080\""))
    }

    @Test
    fun exportedJsonIsImportableForEveryExportScope() {
        val paper = PaperDetail(
            id = "p",
            name = "Nested",
            categories = listOf(
                CategoryDetail(
                    id = "science",
                    name = "Science",
                    questions = listOf(QuestionDetail("q1", "Parent Q", "A1")),
                    children = listOf(
                        CategoryDetail(
                            id = "physics",
                            name = "Physics",
                            questions = listOf(QuestionDetail("q2", "Child Q", "A2")),
                            children = listOf(
                                CategoryDetail(
                                    id = "mechanics",
                                    name = "Mechanics",
                                    questions = listOf(QuestionDetail("q3", "Deep Q", "A3"))
                                )
                            )
                        )
                    )
                )
            )
        )
        val session = TestSession(
            paperId = "p",
            paperName = "Nested",
            createdAt = 0,
            questions = listOf(
                SessionQuestion(
                    number = 1,
                    questionId = "q2",
                    categoryId = "physics",
                    categoryName = "Science / Physics",
                    questionText = "Child Q",
                    answerText = "A2",
                    categoryPath = listOf("Science", "Physics")
                ),
                SessionQuestion(
                    number = 2,
                    questionId = "q3",
                    categoryId = "mechanics",
                    categoryName = "Science / Physics / Mechanics",
                    questionText = "Deep Q",
                    answerText = "A3",
                    categoryPath = listOf("Science", "Physics", "Mechanics")
                )
            )
        )

        val exports = listOf(
            "entire paper" to ExportContentBuilder.entirePaper(paper),
            "selected categories" to ExportContentBuilder.selectedCategories(
                paper,
                setOf("physics")
            ),
            "current test" to ExportContentBuilder.currentTest(paper, session)
        )

        exports.forEach { (scope, content) ->
            val reparsed = PaperJsonParser.parse(
                JsonExporter.build(content, includeAnswers = true)
            )
            assertEquals(scope, content, reparsed)
        }
    }

    @Test
    fun selectedNestedCategoryKeepsAncestorPath() {
        val paper = PaperDetail(
            id = "p",
            name = "Nested",
            categories = listOf(
                CategoryDetail(
                    id = "science",
                    name = "Science",
                    questions = emptyList(),
                    children = listOf(
                        CategoryDetail(
                            id = "physics",
                            name = "Physics",
                            questions = listOf(QuestionDetail("q1", "Q1", "A1"))
                        )
                    )
                )
            )
        )

        val export = ExportContentBuilder.selectedCategories(paper, setOf("physics"))
        val parent = export.categories.single()

        assertEquals("Science", parent.categoryName)
        assertTrue(parent.questions.isEmpty())
        assertEquals("Physics", parent.categories.single().categoryName)
        assertEquals("Q1", parent.categories.single().questions.single().questionText)
    }

    @Test
    fun selectingParentCategoryExportsItsQuestionsAndSubcategoryQuestions() {
        val paper = PaperDetail(
            id = "p",
            name = "Nested",
            categories = listOf(
                CategoryDetail(
                    id = "science",
                    name = "Science",
                    questions = listOf(QuestionDetail("q1", "Parent Q", "A1")),
                    children = listOf(
                        CategoryDetail(
                            id = "physics",
                            name = "Physics",
                            questions = listOf(QuestionDetail("q2", "Child Q", "A2"))
                        )
                    )
                )
            )
        )

        val export = ExportContentBuilder.selectedCategories(paper, setOf("science"))
        val science = export.categories.single()

        assertEquals("Science", science.categoryName)
        assertEquals(listOf("Parent Q"), science.questions.map { it.questionText })
        assertEquals("Physics", science.categories.single().categoryName)
        assertEquals(
            listOf("Child Q"),
            science.categories.single().questions.map { it.questionText }
        )
        assertEquals(2, export.questionCount)
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
    fun htmlRendersRichStylesTablesAndFractions() {
        val html = HtmlExporter.build(richContent, includeAnswers = true)

        assertTrue(html.contains("<strong>Bold</strong>"))
        assertTrue(html.contains("<em>italic</em>"))
        assertTrue(html.contains("<u>under</u>"))
        assertTrue(html.contains("<s>strike</s>"))
        assertTrue(html.contains("<mark>mark</mark>"))
        assertTrue(html.contains("<table class=\"content-table\">"))
        assertTrue(html.contains("<span class=\"frac\">"))
        assertTrue(html.contains("border: 1px solid var(--line)"))
        assertTrue(html.contains("text-align:right"))
    }

    @Test
    fun docxRendersRichStylesTablesAndFractions() {
        val bytes = ByteArrayOutputStream().also {
            DocxExporter.write(
                richContent,
                includeAnswers = true,
                answersAtEnd = false,
                output = it
            )
        }.toByteArray()

        val document = unzip(bytes)["word/document.xml"] ?: error("document.xml missing")
        val text = document.toString(Charsets.UTF_8)

        assertTrue(text.contains("<w:tbl>"))
        assertTrue(text.contains("<w:tblBorders>"))
        assertTrue(text.contains("<w:insideH"))
        assertTrue(text.contains("<w:insideV"))
        assertTrue(text.contains("<m:f>"))
        assertTrue(text.contains("<w:b/>"))
        assertTrue(text.contains("<w:u w:val=\"single\"/>"))
        assertTrue(text.contains("<w:strike/>"))
        assertTrue(text.contains("w:fill=\"FFF0A8\""))

        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val parsed = factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(document))
        assertEquals("document", parsed.documentElement.localName)
    }

    @Test
    fun docxIsAValidZipWithWellFormedWordXml() {
        val bytes = ByteArrayOutputStream().also {
            DocxExporter.write(
                content,
                includeAnswers = true,
                answersAtEnd = false,
                output = it
            )
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
        assertFalse(text.contains("<w:pageBreakBefore/>"))

        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val parsed = factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(document))
        assertEquals("document", parsed.documentElement.localName)
    }

    @Test
    fun docxCanOmitAnswers() {
        val bytes = ByteArrayOutputStream().also {
            DocxExporter.write(
                content,
                includeAnswers = false,
                answersAtEnd = false,
                output = it
            )
        }.toByteArray()

        val text = unzip(bytes)["word/document.xml"]!!.toString(Charsets.UTF_8)

        assertTrue(text.contains("Explain the causes of World War I."))
        assertFalse(text.contains("Alliances, militarism"))
        assertFalse(text.contains("Answer:"))
    }

    @Test
    fun docxCanPlaceAnswersOnNewPageAtEnd() {
        val bytes = ByteArrayOutputStream().also {
            DocxExporter.write(
                content,
                includeAnswers = true,
                answersAtEnd = true,
                output = it
            )
        }.toByteArray()

        val text = unzip(bytes)["word/document.xml"]!!.toString(Charsets.UTF_8)

        val answersIndex = text.indexOf("Answers")
        val pageBreakIndex = text.indexOf("<w:pageBreakBefore/>")
        val lastQuestionIndex = text.indexOf("State Newton&apos;s second law.")
        val firstAnswerIndex = text.indexOf("Alliances, militarism &amp; nationalism.")

        assertTrue(answersIndex > lastQuestionIndex)
        assertTrue(pageBreakIndex > lastQuestionIndex)
        assertTrue(pageBreakIndex < answersIndex)
        assertTrue(firstAnswerIndex > answersIndex)
        assertTrue(text.indexOf("(No answer provided)") > answersIndex)
        assertFalse(text.contains("Answer:"))
    }

    @Test
    fun docxReportsMissingAnswerWithoutLosingQuestion() {
        val jsonBytes = ByteArrayOutputStream().also {
            DocxExporter.write(
                content,
                includeAnswers = true,
                answersAtEnd = false,
                output = it
            )
        }.toByteArray()

        val text = unzip(jsonBytes)["word/document.xml"]!!.toString(Charsets.UTF_8)
        assertTrue(text.contains("(No answer provided)"))
    }

    @Test
    fun docxBytesAreDeterministicEnoughToRoundTrip() {
        val first = ByteArrayOutputStream().also {
            DocxExporter.write(
                content,
                includeAnswers = true,
                answersAtEnd = false,
                output = it
            )
        }.toByteArray()
        val second = ByteArrayOutputStream().also {
            DocxExporter.write(
                content,
                includeAnswers = true,
                answersAtEnd = false,
                output = it
            )
        }.toByteArray()

        // The ZIP container carries timestamps, so compare the document part.
        assertArrayEquals(
            unzip(first)["word/document.xml"],
            unzip(second)["word/document.xml"]
        )
    }

    @Test
    fun docxExportsNestedFractionsAsRecursiveOmml() {
        val nestedFraction = PaperContent(
            paperName = "Fractions",
            categories = listOf(
                CategoryContent(
                    categoryName = "Math",
                    questions = listOf(
                        QuestionContent(
                            questionText = "Compute \\frac{1 + \\frac{1}{2}}{3}",
                            answerText = ""
                        )
                    )
                )
            )
        )

        val bytes = ByteArrayOutputStream().also {
            DocxExporter.write(
                nestedFraction,
                includeAnswers = true,
                answersAtEnd = false,
                output = it
            )
        }.toByteArray()

        val document = unzip(bytes)["word/document.xml"] ?: error("document.xml missing")
        val text = document.toString(Charsets.UTF_8)

        assertTrue(text.contains("<m:oMath>"))
        assertTrue(text.contains("<m:num>"))
        assertTrue(text.contains("<m:den>"))
        // One fraction expression with a nested fraction must produce two
        // m:f nodes, with the inner one sitting inside the outer numerator.
        assertEquals(2, Regex("<m:f>").findAll(text).count())
        val outerNum = text.indexOf("<m:f><m:num>")
        val innerFraction = text.indexOf("<m:f>", outerNum + 1)
        val outerNumClose = text.indexOf("</m:num>", outerNum)
        assertTrue(innerFraction in (outerNum + 1) until outerNumClose)

        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val parsed = factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(document))
        assertEquals("document", parsed.documentElement.localName)
    }

    @Test
    fun thousandQuestionPaperExportsThroughHtmlJsonAndDocx() {
        val image = ImageContent(
            id = "stress-image",
            mimeType = "image/png",
            width = 2,
            height = 2,
            bytes = byteArrayOf(1, 2, 3, 4, 5, 6)
        )
        val categories = (0 until 10).map { categoryIndex ->
            CategoryContent(
                categoryName = "Category $categoryIndex",
                questions = (0 until 100).map { questionIndex ->
                    val number = categoryIndex * 100 + questionIndex + 1
                    QuestionContent(
                        questionText = "Stress question $number",
                        answerText = if (number == 500) {
                            "| A | B |\n| :--- | ---: |\n| x | y |"
                        } else {
                            "Answer $number"
                        },
                        questionImages = if (number == 1 || number == 1000) {
                            listOf(image.copy(id = "stress-image-$number"))
                        } else {
                            emptyList()
                        }
                    )
                }
            )
        }
        val big = PaperContent("Stress paper", categories)
        assertEquals(1000, big.questionCount)

        val html = HtmlExporter.build(big, includeAnswers = true)
        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.trimEnd().endsWith("</html>"))
        assertEquals(
            1000,
            Regex("<article class=\"question\">").findAll(html).count()
        )
        assertTrue(html.contains("<span class=\"number\">1000</span>"))
        assertTrue(html.contains("Stress question 1000"))
        assertTrue(html.contains("data:image/png;base64,"))
        assertTrue(html.contains("<table class=\"content-table\">"))

        val parsed = PaperJsonParser.parse(JsonExporter.build(big, includeAnswers = true))
        assertEquals(1000, parsed.questionCount)
        assertEquals(10, parsed.categoryCount)
        assertEquals(
            "Stress question 1000",
            parsed.categories.last().questions.last().questionText
        )
        assertArrayEquals(
            image.bytes,
            parsed.categories.first().questions.first().questionImages.single().bytes
        )

        val docx = ByteArrayOutputStream().also {
            DocxExporter.write(big, includeAnswers = true, answersAtEnd = false, output = it)
        }.toByteArray()
        val entries = unzip(docx)
        val document = entries["word/document.xml"] ?: error("document.xml missing")
        val documentText = document.toString(Charsets.UTF_8)
        assertEquals(
            1000,
            Regex("Stress question ").findAll(documentText).count()
        )
        assertTrue(documentText.contains("Stress question 1000"))
        assertTrue(documentText.contains("<w:tbl>"))
        assertTrue(entries.keys.any { it.startsWith("word/media/") })

        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        assertEquals(
            "document",
            factory.newDocumentBuilder()
                .parse(ByteArrayInputStream(document)).documentElement.localName
        )
    }

    @Test
    fun htmlWriteMatchesBuildBytes() {
        listOf(content, richContent, mediaContent()).forEach { paper ->
            listOf(true, false).forEach { includeAnswers ->
                val fromBuild = HtmlExporter
                    .build(paper, includeAnswers).toByteArray(Charsets.UTF_8)
                val fromWrite = ByteArrayOutputStream().also {
                    HtmlExporter.write(it, paper, includeAnswers)
                }.toByteArray()

                assertArrayEquals(
                    "HTML bytes differ (includeAnswers=$includeAnswers)",
                    fromBuild,
                    fromWrite
                )
            }
        }
    }

    @Test
    fun jsonWriteMatchesBuildBytes() {
        listOf(content, richContent, mediaContent()).forEach { paper ->
            listOf(true, false).forEach { includeAnswers ->
                val fromBuild = JsonExporter
                    .build(paper, includeAnswers).toByteArray(Charsets.UTF_8)
                val fromWrite = ByteArrayOutputStream().also {
                    JsonExporter.write(it, paper, includeAnswers)
                }.toByteArray()

                assertArrayEquals(
                    "JSON bytes differ (includeAnswers=$includeAnswers)",
                    fromBuild,
                    fromWrite
                )
            }
        }
    }

    @Test
    fun docxStreamProducesStableWellFormedDocumentXml() {
        // DocxExporter has no public build()/String path (writeDocumentXml is
        // private and writeCancellable is only the suspend variant of write), so
        // there is no in-memory counterpart to byte-compare against. Lock the
        // streamed document.xml itself instead.
        fun streamedDocumentXml(): ByteArray = ByteArrayOutputStream().also {
            DocxExporter.write(content, includeAnswers = true, answersAtEnd = false, output = it)
        }.toByteArray().let { unzip(it)["word/document.xml"] ?: error("document.xml missing") }

        val first = streamedDocumentXml()
        val second = streamedDocumentXml()
        assertArrayEquals("streamed document.xml is not byte-stable", first, second)

        val text = first.toString(Charsets.UTF_8)
        assertTrue(text.contains("History &amp; Science &lt;Test&gt;"))
        assertTrue(text.contains("Explain the causes of World War I."))
        assertTrue(text.contains("Alliances, militarism &amp; nationalism."))
        assertTrue(text.contains("Answer:"))

        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        assertEquals(
            "document",
            factory.newDocumentBuilder()
                .parse(ByteArrayInputStream(first)).documentElement.localName
        )
    }

    private fun mediaContent(): PaperContent = PaperContent(
        paperName = "Media",
        categories = listOf(
            CategoryContent(
                categoryName = "Media",
                questions = listOf(
                    QuestionContent(
                        questionText = "Show the diagram.",
                        answerText = "See the answer diagram.",
                        questionImages = listOf(
                            ImageContent("q", "image/png", 2, 2, byteArrayOf(1, 2, 3, 4))
                        ),
                        answerImages = listOf(
                            ImageContent("a", "image/jpeg", 3, 2, byteArrayOf(5, 6, 7))
                        )
                    )
                )
            )
        )
    )

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
