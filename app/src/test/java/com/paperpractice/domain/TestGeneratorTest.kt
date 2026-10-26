package com.paperpractice.domain

import com.paperpractice.data.CategoryDetail
import com.paperpractice.data.PaperDetail
import com.paperpractice.data.QuestionDetail
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TestGeneratorTest {

    private val paper = PaperDetail(
        id = "paper-1",
        name = "Sample",
        categories = listOf(
            CategoryDetail(
                id = "cat-a",
                name = "History",
                questions = listOf(
                    QuestionDetail("a1", "History one?", "History answer one"),
                    QuestionDetail("a2", "History two?", "History answer two"),
                    QuestionDetail("a3", "History three?", "History answer three"),
                    QuestionDetail("a4", "History four?", "History answer four")
                )
            ),
            CategoryDetail(
                id = "cat-b",
                name = "Science",
                questions = listOf(
                    QuestionDetail("b1", "Science one?", "Science answer one"),
                    QuestionDetail("b2", "Science two?", "Science answer two")
                )
            )
        )
    )

    @Test
    fun selectsRequestedNumberFromEachCategory() {
        val session = TestGenerator.generate(
            paper = paper,
            selectedCategoryIds = setOf("cat-a", "cat-b"),
            requestedCounts = mapOf("cat-a" to 2, "cat-b" to 1),
            random = Random(7)
        )

        assertEquals(3, session.questions.size)
        assertEquals(2, session.questions.count { it.categoryId == "cat-a" })
        assertEquals(1, session.questions.count { it.categoryId == "cat-b" })
        assertEquals(2, session.categoryCount)
    }

    @Test
    fun neverSelectsTheSameQuestionTwice() {
        val session = TestGenerator.generate(
            paper = paper,
            selectedCategoryIds = setOf("cat-a", "cat-b"),
            requestedCounts = mapOf("cat-a" to 4, "cat-b" to 2),
            random = Random(99)
        )

        assertEquals(6, session.questions.size)
        assertEquals(6, session.questions.map { it.questionId }.distinct().size)
    }

    @Test
    fun keepsCategoryAndAnswerAssociatedWithEveryQuestion() {
        val session = TestGenerator.generate(
            paper = paper,
            selectedCategoryIds = setOf("cat-a", "cat-b"),
            requestedCounts = mapOf("cat-a" to 3, "cat-b" to 2),
            random = Random(3)
        )

        session.questions.forEach { question ->
            if (question.categoryId == "cat-a") {
                assertEquals("History", question.categoryName)
                assertTrue(question.answerText.startsWith("History answer"))
            } else {
                assertEquals("Science", question.categoryName)
                assertTrue(question.answerText.startsWith("Science answer"))
            }
        }
    }

    @Test
    fun numbersQuestionsSequentiallyInPresentationOrder() {
        val session = TestGenerator.generate(
            paper = paper,
            selectedCategoryIds = setOf("cat-a", "cat-b"),
            requestedCounts = mapOf("cat-a" to 2, "cat-b" to 2)
        )

        assertEquals(listOf(1, 2, 3, 4), session.questions.map { it.number })
    }

    @Test
    fun skipsCategoriesWithZeroRequestedQuestions() {
        val session = TestGenerator.generate(
            paper = paper,
            selectedCategoryIds = setOf("cat-a", "cat-b"),
            requestedCounts = mapOf("cat-a" to 0, "cat-b" to 2)
        )

        assertEquals(2, session.questions.size)
        assertTrue(session.questions.all { it.categoryId == "cat-b" })
    }

    @Test
    fun rejectsCountAboveAvailable() {
        expectFailure {
            TestGenerator.generate(
                paper = paper,
                selectedCategoryIds = setOf("cat-a"),
                requestedCounts = mapOf("cat-a" to 5)
            )
        }
    }

    @Test
    fun rejectsNegativeCount() {
        expectFailure {
            TestGenerator.generate(
                paper = paper,
                selectedCategoryIds = setOf("cat-a"),
                requestedCounts = mapOf("cat-a" to -1)
            )
        }
    }

    @Test
    fun rejectsEmptyCategorySelection() {
        expectFailure {
            TestGenerator.generate(
                paper = paper,
                selectedCategoryIds = emptySet(),
                requestedCounts = emptyMap()
            )
        }
    }

    @Test
    fun rejectsTestWithNoQuestions() {
        expectFailure {
            TestGenerator.generate(
                paper = paper,
                selectedCategoryIds = setOf("cat-a"),
                requestedCounts = mapOf("cat-a" to 0)
            )
        }
    }

    @Test
    fun differentSeedsCanProduceDifferentOrder() {
        val first = TestGenerator.generate(
            paper = paper,
            selectedCategoryIds = setOf("cat-a"),
            requestedCounts = mapOf("cat-a" to 3),
            random = Random(1)
        )
        val second = TestGenerator.generate(
            paper = paper,
            selectedCategoryIds = setOf("cat-a"),
            requestedCounts = mapOf("cat-a" to 3),
            random = Random(2)
        )

        assertNotEquals(
            first.questions.map { it.questionId },
            second.questions.map { it.questionId }
        )
    }

    private fun expectFailure(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().isNotBlank())
        }
    }
}
