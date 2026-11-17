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

    @Test
    fun nestedCategoryKeepsHierarchyInSessionLabels() {
        val nestedPaper = PaperDetail(
            id = "paper-nested",
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
                            questions = listOf(
                                QuestionDetail("n1", "What is force?", "F = ma")
                            )
                        )
                    )
                )
            )
        )

        val session = TestGenerator.generate(
            paper = nestedPaper,
            selectedCategoryIds = setOf("physics"),
            requestedCounts = mapOf("physics" to 1),
            random = Random(5)
        )

        val question = session.questions.single()
        assertEquals("Science / Physics", question.categoryName)
        assertEquals(listOf("Science", "Physics"), question.categoryPath)
    }

    @Test
    fun parentCategoryCountIncludesSubcategoryQuestions() {
        val nestedPaper = PaperDetail(
            id = "paper-nested-parent",
            name = "Nested",
            categories = listOf(
                CategoryDetail(
                    id = "science",
                    name = "Science",
                    questions = listOf(QuestionDetail("s1", "Parent question?", "A")),
                    children = listOf(
                        CategoryDetail(
                            id = "physics",
                            name = "Physics",
                            questions = listOf(QuestionDetail("p1", "Child question?", "B"))
                        )
                    )
                )
            )
        )

        val session = TestGenerator.generate(
            paper = nestedPaper,
            selectedCategoryIds = setOf("science"),
            requestedCounts = mapOf("science" to 2),
            random = Random(11)
        )

        assertEquals(2, session.questions.size)
        assertEquals(
            setOf("s1", "p1"),
            session.questions.map { it.questionId }.toSet()
        )
        assertEquals(
            setOf("Science", "Science / Physics"),
            session.questions.map { it.categoryName }.toSet()
        )
    }

    @Test
    fun overlappingAncestorAndDescendantSelectionsKeepAncestorAndDoNotDoubleCount() {
        // Selecting both a parent and its child must keep only the parent. The
        // child's requested count is ignored entirely (it would be impossible
        // for the child alone), and questions in the shared subtree are counted
        // once rather than twice.
        val nestedPaper = PaperDetail(
            id = "paper-overlap",
            name = "Overlap",
            categories = listOf(
                CategoryDetail(
                    id = "science",
                    name = "Science",
                    questions = listOf(QuestionDetail("s1", "Parent?", "A")),
                    children = listOf(
                        CategoryDetail(
                            id = "physics",
                            name = "Physics",
                            questions = listOf(
                                QuestionDetail("p1", "Child one?", "B"),
                                QuestionDetail("p2", "Child two?", "C")
                            )
                        )
                    )
                )
            )
        )

        val session = TestGenerator.generate(
            paper = nestedPaper,
            selectedCategoryIds = setOf("science", "physics"),
            requestedCounts = mapOf("science" to 3, "physics" to 999),
            random = Random(7)
        )

        assertEquals(3, session.questions.size)
        assertEquals(
            setOf("s1", "p1", "p2"),
            session.questions.map { it.questionId }.toSet()
        )
        // Each distinct question appears exactly once: no double counting.
        assertEquals(3, session.questions.map { it.questionId }.distinct().size)

        // Selecting the ancestor alone yields exactly the same set of questions.
        val ancestorOnly = TestGenerator.generate(
            paper = nestedPaper,
            selectedCategoryIds = setOf("science"),
            requestedCounts = mapOf("science" to 3),
            random = Random(7)
        )
        assertEquals(
            ancestorOnly.questions.map { it.questionId }.toSet(),
            session.questions.map { it.questionId }.toSet()
        )
    }

    @Test
    fun siblingSubtreesSharingDuplicateIdsEachReachRequestedCount() {
        // "shared" appears in both sibling categories. The global used-id set
        // must still let each sibling reach its requested count where possible,
        // with the shared id consumed only once.
        val sharedPaper = PaperDetail(
            id = "paper-shared",
            name = "Shared",
            categories = listOf(
                CategoryDetail(
                    id = "a",
                    name = "A",
                    questions = listOf(
                        QuestionDetail("shared", "A shared?", "a-shared"),
                        QuestionDetail("a2", "A extra?", "a-extra")
                    )
                ),
                CategoryDetail(
                    id = "b",
                    name = "B",
                    questions = listOf(
                        QuestionDetail("shared", "B shared?", "b-shared"),
                        QuestionDetail("b2", "B extra?", "b-extra")
                    )
                )
            )
        )

        val session = TestGenerator.generate(
            paper = sharedPaper,
            selectedCategoryIds = setOf("a", "b"),
            requestedCounts = mapOf("a" to 2, "b" to 1),
            random = Random(7)
        )

        assertEquals(3, session.questions.size)
        assertEquals(2, session.questions.count { it.categoryId == "a" })
        assertEquals(1, session.questions.count { it.categoryId == "b" })
        assertEquals(3, session.questions.map { it.questionId }.distinct().size)
        assertEquals(1, session.questions.count { it.questionId == "shared" })
    }

    @Test
    fun duplicateQuestionRowsDoNotInflateAvailableCount() {
        // Validation counts distinct ids, not rows. Three rows with only two
        // distinct ids cannot satisfy a request of three: sampling would filter
        // the duplicate and silently under-fill, so it is rejected up front.
        val duplicatePaper = PaperDetail(
            id = "paper-dupes",
            name = "Dupes",
            categories = listOf(
                CategoryDetail(
                    id = "a",
                    name = "A",
                    questions = listOf(
                        QuestionDetail("dup", "First?", "A1"),
                        QuestionDetail("dup", "Duplicate row?", "A2"),
                        QuestionDetail("other", "Other?", "A3")
                    )
                )
            )
        )

        expectFailure {
            TestGenerator.generate(
                paper = duplicatePaper,
                selectedCategoryIds = setOf("a"),
                requestedCounts = mapOf("a" to 3)
            )
        }
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
