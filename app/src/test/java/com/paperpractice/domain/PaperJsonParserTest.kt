package com.paperpractice.domain

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

        assertEquals("  Sample Paper  ", paper.paperName)
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
