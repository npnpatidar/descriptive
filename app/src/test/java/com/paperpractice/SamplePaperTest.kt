package com.paperpractice

import com.paperpractice.domain.CategoryContent
import com.paperpractice.domain.PaperContent
import com.paperpractice.domain.PaperJsonParser
import com.paperpractice.domain.QuestionContent
import com.paperpractice.domain.RichBlock
import com.paperpractice.domain.RichText
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Keeps the bundled example in sync with the documented schema. The sample
 * intentionally contains questions and subcategories at the same level,
 * branches several categories deep, and only keeps questions that demonstrate
 * at least one display or content feature.
 */
class SamplePaperTest {

    @Test
    fun samplePaperIsValidAndComprehensive() {
        val candidates = listOf(
            File("app/src/main/assets/sample-paper.json"),
            File("src/main/assets/sample-paper.json"),
            File("../app/src/main/assets/sample-paper.json")
        )
        val file = candidates.firstOrNull { it.isFile }
        if (file == null) {
            fail("Could not find sample-paper.json from ${File(".").absolutePath}")
            return
        }

        val paper = PaperJsonParser.parse(file.readText())

        assertEquals("Comprehensive Sample Paper", paper.paperName)
        assertEquals(5, paper.categories.size)
        assertEquals(20, paper.categoryCount)
        assertEquals(30, paper.questionCount)
        assertEquals(4, maxDepth(paper))

        assertEquals(
            mapOf(
                "Science" to 15,
                "History" to 1,
                "Mathematics" to 10,
                "Computer Science" to 3,
                "General Knowledge" to 1
            ),
            paper.categories.associate { it.categoryName to it.questionCount }
        )

        val questions = mutableListOf<QuestionContent>()
        collectQuestions(paper.categories, questions)
        assertEquals(30, questions.size)
        assertTrue(questions.all { it.answerText.isNotBlank() })
        assertEquals(
            questions.size,
            questions.map { it.questionText }.toSet().size
        )
        assertTrue(questions.all(::demonstratesFeature))
        assertEquals(6, questions.sumOf { it.questionImages.size + it.answerImages.size })
        assertTrue(questions.any { it.answerText.contains("->") })
        assertTrue(questions.any { it.answerText.contains("\\frac") })
        assertTrue(questions.any { it.questionImages.isNotEmpty() })
        assertTrue(questions.any { it.answerText.contains("**") })
        assertTrue(questions.any { it.answerText.contains("++") })
        assertTrue(questions.any { it.answerText.contains("~~") })
        assertTrue(questions.any { it.answerText.contains("==") })
        assertEquals(
            4,
            questions.sumOf { question ->
                RichText.parse(question.answerText).blocks.count { it is RichBlock.Table }
            }
        )
    }

    private fun demonstratesFeature(question: QuestionContent): Boolean {
        if (question.questionImages.isNotEmpty() || question.answerImages.isNotEmpty()) {
            return true
        }
        val text = question.questionText + "\n" + question.answerText
        return question.answerText.contains("\n") || listOf(
            "**", "++", "~~", "==", "\\frac", "\\sqrt", "->", "| ---", "_", "^"
        ).any(text::contains)
    }

    private fun maxDepth(paper: PaperContent): Int {
        fun depth(category: CategoryContent): Int =
            1 + (category.categories.maxOfOrNull(::depth) ?: 0)

        return paper.categories.maxOf(::depth)
    }

    private fun collectQuestions(
        categories: List<CategoryContent>,
        output: MutableList<QuestionContent>
    ) {
        categories.forEach { category ->
            output += category.questions
            collectQuestions(category.categories, output)
        }
    }
}
