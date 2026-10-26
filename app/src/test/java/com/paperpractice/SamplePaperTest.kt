package com.paperpractice

import com.paperpractice.domain.CategoryContent
import com.paperpractice.domain.PaperContent
import com.paperpractice.domain.PaperJsonParser
import com.paperpractice.domain.QuestionContent
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Keeps the checked-in example in sync with the documented schema. The sample
 * intentionally contains questions and subcategories at the same level and
 * branches several categories deep.
 */
class SamplePaperTest {

    @Test
    fun samplePaperIsValidAndComprehensive() {
        val candidates = listOf(
            File("sample-paper.json"),
            File("../sample-paper.json"),
            File("../../sample-paper.json")
        )
        val file = candidates.firstOrNull { it.isFile }
        if (file == null) {
            fail("Could not find sample-paper.json from ${File(".").absolutePath}")
            return
        }

        val paper = PaperJsonParser.parse(file.readText())

        assertEquals("Comprehensive Sample Paper", paper.paperName)
        assertEquals(5, paper.categories.size)
        assertEquals(23, paper.categoryCount)
        assertEquals(53, paper.questionCount)
        assertEquals(4, maxDepth(paper))

        assertEquals(
            mapOf(
                "Science" to 16,
                "History" to 11,
                "Mathematics" to 8,
                "Computer Science" to 11,
                "General Knowledge" to 7
            ),
            paper.categories.associate { it.categoryName to it.questionCount }
        )

        val questions = mutableListOf<QuestionContent>()
        collectQuestions(paper.categories, questions)
        assertEquals(53, questions.size)
        assertTrue(questions.all { it.answerText.isNotBlank() })
        assertEquals(
            questions.size,
            questions.map { it.questionText }.toSet().size
        )
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
