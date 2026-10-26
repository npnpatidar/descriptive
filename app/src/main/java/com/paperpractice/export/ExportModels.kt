package com.paperpractice.export

import com.paperpractice.data.CategoryDetail
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
    val description: String,
    val supportsAnswersAtEnd: Boolean = false
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
        "An editable Microsoft Word document.",
        supportsAnswersAtEnd = true
    ),
    PDF(
        "PDF",
        "pdf",
        "application/pdf",
        "A paginated document ready to print.",
        supportsAnswersAtEnd = true
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
 * Visible category labels in rendered documents use the full hierarchy, for
 * example "Science/Physics/Mechanics". JSON keeps the nested category tree and
 * leaf names so an exported file can be imported again without any translation.
 */
internal fun categoryExportName(
    parentPath: List<String>,
    categoryName: String
): String = (parentPath + categoryName).joinToString("/")

/**
 * Converts the selected scope into the same schema used by import. No exporter
 * has to understand Room entities or the current-tab state.
 */
object ExportContentBuilder {

    fun entirePaper(paper: PaperDetail): PaperContent = PaperContent(
        paperName = paper.name,
        categories = paper.categories.map(::categoryContent)
    )

    fun selectedCategories(paper: PaperDetail, selectedCategoryIds: Set<String>): PaperContent =
        PaperContent(
            paperName = paper.name,
            categories = paper.categories.mapNotNull { category ->
                selectedCategoryContent(category, selectedCategoryIds)
            }
        )

    fun currentTest(paper: PaperDetail, session: TestSession): PaperContent {
        // Keep the random order that the learner saw, while rebuilding the
        // nested category path for every question.
        val roots = linkedMapOf<String, ExportNode>()

        session.questions.forEach { question ->
            val path = question.categoryPath.ifEmpty { listOf(question.categoryName) }
            var children: MutableMap<String, ExportNode> = roots
            var node: ExportNode? = null

            path.forEach { categoryName ->
                node = children.getOrPut(categoryName) { ExportNode(categoryName) }
                children = node!!.children
            }

            node?.questions?.add(QuestionContent(question.questionText, question.answerText))
        }

        return PaperContent(
            paperName = paper.name,
            categories = roots.values.map { it.toContent() }
        )
    }

    fun withoutAnswers(content: PaperContent): PaperContent = content.copy(
        categories = content.categories.map { it.withoutAnswers() }
    )

    private fun categoryContent(category: CategoryDetail): CategoryContent = CategoryContent(
        categoryName = category.name,
        questions = category.questions.map {
            QuestionContent(it.questionText, it.answerText)
        },
        categories = category.children.map(::categoryContent)
    )

    private fun selectedCategoryContent(
        category: CategoryDetail,
        selectedCategoryIds: Set<String>
    ): CategoryContent? {
        if (category.id in selectedCategoryIds) {
            // Selecting a category means its whole subtree, matching the test
            // configuration screen's available question count.
            return categoryContent(category)
        }

        val children = category.children.mapNotNull {
            selectedCategoryContent(it, selectedCategoryIds)
        }
        if (children.isEmpty()) return null

        return CategoryContent(
            categoryName = category.name,
            questions = emptyList(),
            categories = children
        )
    }

    private fun CategoryContent.withoutAnswers(): CategoryContent = copy(
        questions = questions.map { it.copy(answerText = "") },
        categories = categories.map { it.withoutAnswers() }
    )

    private data class ExportNode(
        val name: String,
        val questions: MutableList<QuestionContent> = mutableListOf(),
        val children: MutableMap<String, ExportNode> = linkedMapOf()
    ) {
        fun toContent(): CategoryContent = CategoryContent(
            categoryName = name,
            questions = questions.toList(),
            categories = children.values.map { it.toContent() }
        )
    }
}
