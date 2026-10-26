package com.paperpractice.export

import com.paperpractice.data.PaperDetail
import com.paperpractice.domain.CategoryContent
import com.paperpractice.domain.PaperContent
import com.paperpractice.domain.QuestionContent
import com.paperpractice.domain.TestSession

enum class ExportScope(val label: String, val description: String) {
    CURRENT_TEST(
        "Current test",
        "Only the questions in the practice test you just generated."
    ),
    SELECTED_CATEGORIES(
        "Selected categories",
        "Every question in the categories selected on the configuration screen."
    ),
    ENTIRE_PAPER(
        "Entire paper",
        "All categories, questions, and answers in this paper."
    )
}

enum class ExportFormat(
    val label: String,
    val extension: String,
    val mimeType: String,
    val description: String
) {
    HTML(
        "HTML",
        "html",
        "text/html",
        "An interactive page with offline Show/Hide Answers buttons."
    ),
    DOCX(
        "DOCX",
        "docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "An editable Microsoft Word document."
    ),
    PDF(
        "PDF",
        "pdf",
        "application/pdf",
        "A paginated document ready to print."
    ),
    JSON(
        "JSON",
        "json",
        "application/json",
        "Structured data that can be imported back into the app."
    )
}

data class CreateDocumentRequest(
    val mimeType: String,
    val fileName: String
)

data class ExportFeedback(
    val isError: Boolean,
    val message: String
)

/**
 * Converts the selected scope into the same schema used by import. No exporter
 * has to understand Room entities or the current-tab state.
 */
object ExportContentBuilder {

    fun entirePaper(paper: PaperDetail): PaperContent = PaperContent(
        paperName = paper.name,
        categories = paper.categories.map { category ->
            CategoryContent(
                categoryName = category.name,
                questions = category.questions.map {
                    QuestionContent(it.questionText, it.answerText)
                }
            )
        }
    )

    fun selectedCategories(paper: PaperDetail, selectedCategoryIds: Set<String>): PaperContent =
        PaperContent(
            paperName = paper.name,
            categories = paper.categories
                .filter { it.id in selectedCategoryIds }
                .map { category ->
                    CategoryContent(
                        categoryName = category.name,
                        questions = category.questions.map {
                            QuestionContent(it.questionText, it.answerText)
                        }
                    )
                }
        )

    fun currentTest(paper: PaperDetail, session: TestSession): PaperContent {
        // Keep the random order that the learner saw. LinkedHashMap preserves
        // category first-appearance order.
        val byCategory = linkedMapOf<String, Pair<String, MutableList<QuestionContent>>>()
        session.questions.forEach { question ->
            val entry = byCategory.getOrPut(question.categoryId) {
                question.categoryName to mutableListOf()
            }
            entry.second += QuestionContent(question.questionText, question.answerText)
        }

        return PaperContent(
            paperName = paper.name,
            categories = byCategory.map { (_, entry) ->
                CategoryContent(
                    categoryName = entry.first,
                    questions = entry.second
                )
            }
        )
    }

    fun withoutAnswers(content: PaperContent): PaperContent = content.copy(
        categories = content.categories.map { category ->
            category.copy(
                questions = category.questions.map { it.copy(answerText = "") }
            )
        }
    )
}
