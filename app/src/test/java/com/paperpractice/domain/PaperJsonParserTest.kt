package com.paperpractice.domain

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PaperJsonParserTest {

    @Test
    fun parsesValidPaperAndPreservesOrderAndText() {
        val json = """
            {
              "paperName": "  Sample Paper  ",
              "categories": [
                {
                  "categoryName": "History",
                  "questions": [
                    {
                      "questionText": "Explain the causes of World War I.",
                      "answerText": "The major causes included..."
                    },
                    {
                      "questionText": "Second question?",
                      "answerText": ""
                    }
                  ]
                },
                {
                  "categoryName": "Empty category",
                  "questions": []
                }
              ],
              "ignoredTopLevelField": 42
            }
        """.trimIndent()

        val paper = PaperJsonParser.parse(json)

        assertEquals("Sample Paper", paper.paperName)
        assertEquals(2, paper.categories.size)
        assertEquals("History", paper.categories[0].categoryName)
        assertEquals(2, paper.categories[0].questions.size)
        assertEquals(
            "Explain the causes of World War I.",
            paper.categories[0].questions[0].questionText
        )
        assertEquals("The major causes included...", paper.categories[0].questions[0].answerText)
        assertEquals("", paper.categories[0].questions[1].answerText)
        assertTrue(paper.categories[1].questions.isEmpty())
    }

    @Test
    fun parsesRecursiveSubcategories() {
        val json = """
            {
              "paperName": "Nested",
              "categories": [
                {
                  "categoryName": "Science",
                  "questions": [],
                  "categories": [
                    {
                      "categoryName": "Physics",
                      "questions": [
                        {"questionText": "What is force?", "answerText": "Mass times acceleration."}
                      ],
                      "categories": [
                        {
                          "categoryName": "Mechanics",
                          "questions": [
                            {"questionText": "State inertia.", "answerText": ""}
                          ]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val paper = PaperJsonParser.parse(json)
        val science = paper.categories.single()
        val physics = science.categories.single()
        val mechanics = physics.categories.single()

        assertEquals("Science", science.categoryName)
        assertTrue(science.questions.isEmpty())
        assertEquals("Physics", physics.categoryName)
        assertEquals("What is force?", physics.questions.single().questionText)
        assertEquals("Mechanics", mechanics.categoryName)
        assertEquals(3, paper.categoryCount)
        assertEquals(2, paper.questionCount)
    }

    @Test
    fun pointsAtNestedQuestionFieldWithFullPath() {
        val message = failureMessage(
            """
            {
              "paperName": "P",
              "categories": [
                {
                  "categoryName": "Parent",
                  "questions": [],
                  "categories": [
                    {
                      "categoryName": "Child",
                      "questions": [
                        {"questionText": "Q"}
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )
        assertTrue(
            message.contains("categories[0].categories[0].questions[0].answerText")
        )
    }

    @Test
    fun acceptsUtf8Bom() {
        val paper = PaperJsonParser.parse(
            "\uFEFF{\"paperName\":\"P\",\"categories\":[]}"
        )
        assertEquals("P", paper.paperName)
    }

    @Test
    fun rejectsArrayRoot() {
        val message = failureMessage("[]")
        assertTrue(message.contains("top level", ignoreCase = true))
    }

    @Test
    fun pointsAtMissingPaperName() {
        val message = failureMessage("""{"categories":[]}""")
        assertTrue(message.contains("paperName"))
        assertTrue(message.contains("required"))
    }

    @Test
    fun rejectsBlankPaperName() {
        val message = failureMessage(
            """{"paperName":"   ","categories":[]}"""
        )
        assertTrue(message.contains("paperName"))
        assertTrue(message.contains("empty"))
    }

    @Test
    fun pointsAtMissingCategoryNameWithPath() {
        val message = failureMessage(
            """
            {
              "paperName": "P",
              "categories": [
                {"categoryName": "ok", "questions": []},
                {"questions": []}
              ]
            }
            """.trimIndent()
        )
        assertTrue(message.contains("categories[1].categoryName"))
    }

    @Test
    fun pointsAtMissingQuestionFieldWithPath() {
        val message = failureMessage(
            """
            {
              "paperName": "P",
              "categories": [
                {
                  "categoryName": "C",
                  "questions": [
                    {"questionText": "Q"},
                    {"questionText": "Q2", "answerText": "A2"}
                  ]
                }
              ]
            }
            """.trimIndent()
        )
        assertTrue(message.contains("categories[0].questions[0].answerText"))
    }

    @Test
    fun rejectsWrongFieldType() {
        val message = failureMessage(
            """{"paperName":42,"categories":[]}"""
        )
        assertTrue(message.contains("paperName"))
        assertTrue(message.contains("string"))
    }

    @Test
    fun rejectsBlankQuestionText() {
        val message = failureMessage(
            """
            {
              "paperName": "P",
              "categories": [
                {"categoryName": "C", "questions": [
                  {"questionText": "  ", "answerText": "A"}
                ]}
              ]
            }
            """.trimIndent()
        )
        assertTrue(message.contains("questionText"))
        assertTrue(message.contains("empty"))
    }

    @Test
    fun reportsMalformedJson() {
        val message = failureMessage("""{"paperName": "P", "categories": [}""")
        assertTrue(message.contains("not valid JSON", ignoreCase = true))
    }

    @Test
    fun rejectsEmptyFile() {
        val message = failureMessage("   ")
        assertTrue(message.contains("empty", ignoreCase = true))
    }

    @Test
    fun acceptsExactlySixtyFourNestedCategoryLevels() {
        val paper = PaperJsonParser.parse(nestedCategoryPaper(64))

        assertEquals(64, paper.categoryCount)
    }

    @Test
    fun rejectsSixtyFiveNestedCategoryLevels() {
        val message = failureMessage(nestedCategoryPaper(65))

        assertTrue(message.contains("nested more than 64 levels"))
    }

    @Test
    fun headerDimensionsWinOverConflictingDeclaredSize() {
        val image = parseImage(imageJson(pngHeader(640, 480), 11, 22))

        assertEquals(640, image.width)
        assertEquals(480, image.height)
    }

    @Test
    fun declaredSizeIsUsedOnlyWhenBothDimensionsAndNoHeaderExist() {
        val unknownBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

        val withBoth = parseImage(imageJson(unknownBytes, 11, 22))
        assertEquals(11, withBoth.width)
        assertEquals(22, withBoth.height)

        val withOnlyWidth = parseImage(imageJson(unknownBytes, 11, null))
        assertEquals(0, withOnlyWidth.width)
        assertEquals(0, withOnlyWidth.height)

        val withOnlyHeight = parseImage(imageJson(unknownBytes, null, 22))
        assertEquals(0, withOnlyHeight.width)
        assertEquals(0, withOnlyHeight.height)
    }

    @Test
    fun singleDeclaredDimensionDoesNotOverrideInferredHeader() {
        val image = parseImage(imageJson(pngHeader(640, 480), 11, null))

        assertEquals(640, image.width)
        assertEquals(480, image.height)
    }

    @Test
    fun duplicateKeysUseTheLastValueForValidation() {
        // kotlinx-serialization 1.13.0 parses objects into a LinkedHashMap via
        // `result[key] = element`, so a repeated key resolves to its last value.
        // https://github.com/Kotlin/kotlinx.serialization/blob/v1.13.0/formats/json/commonMain/src/kotlinx/serialization/json/internal/JsonTreeReader.kt
        val paper = PaperJsonParser.parse(
            """{"paperName":"First","paperName":"Final","categories":[]}"""
        )
        assertEquals("Final", paper.paperName)

        // Validation must run on the resolved value, not on the earlier duplicate.
        val message = failureMessage(
            """{"paperName":"Valid","paperName":"   ","categories":[]}"""
        )
        assertTrue(message.contains("paperName"))
        assertTrue(message.contains("empty"))
    }

    @Test
    fun decodesUnicodeEscapesInNamesAndAnswers() {
        // Build the backslash at runtime so the Kotlin lexer cannot consume the
        // JSON escape sequences in this source file before the parser sees them.
        val slash = "\\"
        val json = "{" +
            "\"paperName\":\"P${slash}u0061per ${slash}u00e9\"," +
            "\"categories\":[{" +
            "\"categoryName\":\"C${slash}u03b1\"," +
            "\"questions\":[{" +
            "\"questionText\":\"Q ${slash}u0031\"," +
            "\"answerText\":\"A ${slash}u2603\"" +
            "}]}]}"

        val paper = PaperJsonParser.parse(json)
        val question = paper.categories.single().questions.single()

        assertEquals("Paper é", paper.paperName)
        assertEquals("Cα", paper.categories.single().categoryName)
        assertEquals("Q 1", question.questionText)
        assertEquals("A ☃", question.answerText)
    }

    @Test
    fun acceptsVeryLongQuestionAndAnswerText() {
        val questionText = "Q".repeat(100_000)
        val answerText = "A".repeat(100_000)
        val json = "{\"paperName\":\"Long\",\"categories\":[{" +
            "\"categoryName\":\"C\",\"questions\":[{" +
            "\"questionText\":\"$questionText\",\"answerText\":\"$answerText\"" +
            "}]}]}"

        val question = PaperJsonParser.parse(json).categories.single().questions.single()

        assertEquals(questionText, question.questionText)
        assertEquals(answerText, question.answerText)
        assertEquals(100_000, question.questionText.length)
    }

    @Test
    fun rejectsImageDimensionsOutsideZeroTo100000() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5, 6)

        val tooWide = failureMessage(imageJson(bytes, 100_001, 10))
        assertTrue(tooWide.contains("questionImages[0].width must be between 0 and 100000"))

        val negativeWidth = failureMessage(imageJson(bytes, -1, 10))
        assertTrue(negativeWidth.contains("questionImages[0].width must be between 0 and 100000"))

        val tooTall = failureMessage(imageJson(bytes, 10, 100_001))
        assertTrue(tooTall.contains("questionImages[0].height must be between 0 and 100000"))
    }

    @Test
    fun acceptsImageDimensionsAtTheUpperBound() {
        val image = parseImage(imageJson(byteArrayOf(1, 2, 3, 4, 5, 6), 100_000, 100_000))

        assertEquals(100_000, image.width)
        assertEquals(100_000, image.height)
    }

    private fun nestedCategoryPaper(levels: Int): String {
        var category = """{"categoryName":"C","questions":[]}"""
        repeat(levels - 1) {
            category = """{"categoryName":"C","questions":[],"categories":[$category]}"""
        }
        return """{"paperName":"Deep","categories":[$category]}"""
    }

    private fun imageJson(
        bytes: ByteArray,
        declaredWidth: Int?,
        declaredHeight: Int?
    ): String {
        val encoded = Base64.getEncoder().encodeToString(bytes)
        val sizeFields = buildString {
            if (declaredWidth != null) append(",\"width\":$declaredWidth")
            if (declaredHeight != null) append(",\"height\":$declaredHeight")
        }
        return """
            {
              "paperName": "P",
              "categories": [
                {
                  "categoryName": "C",
                  "questions": [
                    {
                      "questionText": "Q",
                      "answerText": "",
                      "questionImages": [
                        {"mimeType": "image/png", "data": "$encoded"$sizeFields}
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
    }

    private fun parseImage(json: String): ImageContent =
        PaperJsonParser.parse(json)
            .categories.first()
            .questions.first()
            .questionImages.single()

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

    private fun failureMessage(json: String): String {
        return try {
            PaperJsonParser.parse(json)
            fail("Expected PaperFormatException")
            error("unreachable")
        } catch (e: PaperFormatException) {
            e.message.orEmpty()
        }
    }
}
