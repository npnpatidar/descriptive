package com.paperpractice.domain

/**
 * The on-disk/import/export representation of a paper. It deliberately mirrors
 * the documented JSON schema so an exported file can be imported again without
 * any translation step.
 *
 * Categories may contain subcategories to any depth. Questions and
 * subcategories can coexist on the same category.
 */
data class PaperContent(
    val paperName: String,
    val categories: List<CategoryContent>
) {
    val questionCount: Int
        get() = categories.sumOf { it.questionCount }

    val categoryCount: Int
        get() = categories.sumOf { it.categoryCount }
}

data class CategoryContent(
    val categoryName: String,
    val questions: List<QuestionContent>,
    val categories: List<CategoryContent> = emptyList()
) {
    val questionCount: Int
        get() = questions.size + categories.sumOf { it.questionCount }

    val categoryCount: Int
        get() = 1 + categories.sumOf { it.categoryCount }
}

data class QuestionContent(
    val questionText: String,
    val answerText: String
)

/** A problem the user can fix in their JSON file. */
class PaperFormatException(message: String) : Exception(message)
