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
    val answerText: String,
    val categoryPath: List<String> = emptyList()
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

        val allCategories = paper.flattenCategories()
        val categoriesById = allCategories.associateBy { it.category.id }
        val selectedCategories = selectedCategoryIds.mapNotNull { categoriesById[it] }
        require(selectedCategories.isNotEmpty()) { "Select at least one category." }

        selectedCategories.forEach { node ->
            val available = allCategories
                .filter { node.contains(it) }
                .sumOf { it.category.questions.size }
            val requested = requestedCounts[node.category.id] ?: 0
            require(requested >= 0) {
                "The question count for \"${node.displayName}\" cannot be negative."
            }
            require(requested <= available) {
                "The question count for \"${node.displayName}\" exceeds the " +
                    "$available available questions."
            }
        }

        val selectedQuestions = mutableListOf<SessionQuestion>()
        val usedQuestionIds = mutableSetOf<String>()

        selectedCategories.forEach { node ->
            val requested = requestedCounts[node.category.id] ?: 0
            if (requested == 0) return@forEach

            val pool = allCategories
                .filter { node.contains(it) }
                .flatMap { owner ->
                    owner.category.questions.map { question -> owner to question }
                }

            pool
                .shuffled(random)
                .filter { (_, question) -> usedQuestionIds.add(question.id) }
                .take(requested)
                .forEach { (owner, question) ->
                    selectedQuestions += SessionQuestion(
                        number = selectedQuestions.size + 1,
                        questionId = question.id,
                        categoryId = owner.category.id,
                        categoryName = owner.displayName,
                        questionText = question.questionText,
                        answerText = question.answerText,
                        categoryPath = owner.path
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
