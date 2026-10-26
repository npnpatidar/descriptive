package com.paperpractice.data

/** Lightweight row used by the home screen so large papers are never loaded. */
data class PaperSummary(
    val id: String,
    val name: String,
    val importedAt: Long,
    val categoryCount: Int,
    val questionCount: Int
)

data class PaperDetail(
    val id: String,
    val name: String,
    val categories: List<CategoryDetail>
) {
    val questionCount: Int
        get() = categories.sumOf { it.questions.size }
}

data class CategoryDetail(
    val id: String,
    val name: String,
    val questions: List<QuestionDetail>
)

data class QuestionDetail(
    val id: String,
    val questionText: String,
    val answerText: String
)
