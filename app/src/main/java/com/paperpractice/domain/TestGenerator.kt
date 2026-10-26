package com.paperpractice.domain

import com.paperpractice.data.PaperDetail
import kotlin.random.Random

data class TestSession(
    val paperId: String,
    val paperName: String,
    val createdAt: Long,
    val questions: List<SessionQuestion>
) {
    val categoryCount: Int
        get() = questions.map { it.categoryId }.distinct().size
}

data class SessionQuestion(
    val number: Int,
    val questionId: String,
    val categoryId: String,
    val categoryName: String,
    val questionText: String,
    val answerText: String
)

/**
 * Builds a test by sampling without replacement inside each category. Sampling
 * per category matters: it guarantees the requested amount from every selected
 * category instead of letting one large category dominate a global shuffle.
 */
object TestGenerator {

    fun generate(
        paper: PaperDetail,
        selectedCategoryIds: Set<String>,
        requestedCounts: Map<String, Int>,
        random: Random = Random.Default
    ): TestSession {
        require(selectedCategoryIds.isNotEmpty()) { "Select at least one category." }

        val selectedCategories = paper.categories.filter { it.id in selectedCategoryIds }
        require(selectedCategories.isNotEmpty()) { "Select at least one category." }

        selectedCategories.forEach { category ->
            val requested = requestedCounts[category.id] ?: 0
            require(requested >= 0) {
                "The question count for \"${category.name}\" cannot be negative."
            }
            require(requested <= category.questions.size) {
                "The question count for \"${category.name}\" exceeds the " +
                    "${category.questions.size} available questions."
            }
        }

        val selectedQuestions = mutableListOf<SessionQuestion>()
        val usedQuestionIds = mutableSetOf<String>()

        selectedCategories.forEach { category ->
            val requested = requestedCounts[category.id] ?: 0
            if (requested == 0) return@forEach

            category.questions
                .shuffled(random)
                .filter { usedQuestionIds.add(it.id) }
                .take(requested)
                .forEach { question ->
                    selectedQuestions += SessionQuestion(
                        number = selectedQuestions.size + 1,
                        questionId = question.id,
                        categoryId = category.id,
                        categoryName = category.name,
                        questionText = question.questionText,
                        answerText = question.answerText
                    )
                }
        }

        require(selectedQuestions.isNotEmpty()) {
            "Choose at least one question to generate a test."
        }

        return TestSession(
            paperId = paper.id,
            paperName = paper.name,
            createdAt = System.currentTimeMillis(),
            questions = selectedQuestions
        )
    }
}
