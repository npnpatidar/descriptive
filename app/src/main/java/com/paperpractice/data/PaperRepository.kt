package com.paperpractice.data

import com.paperpractice.domain.CategoryContent
import com.paperpractice.domain.PaperContent
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class PaperRepository(private val dao: PaperDao) {

    fun observePapers(): Flow<List<PaperSummary>> = dao.observePaperSummaries()

    /**
     * Copies a validated paper into the local database in one transaction.
     * A partially imported paper can therefore never be left behind.
     */
    suspend fun importPaper(content: PaperContent): PaperDetail {
        val paperId = UUID.randomUUID().toString()
        val importedAt = System.currentTimeMillis()
        val categories = mutableListOf<CategoryEntity>()
        val questions = mutableListOf<QuestionEntity>()

        fun addCategories(
            source: List<CategoryContent>,
            parentId: String?
        ) {
            source.forEachIndexed { categoryIndex, category ->
                val categoryId = UUID.randomUUID().toString()
                categories += CategoryEntity(
                    id = categoryId,
                    paperId = paperId,
                    name = category.categoryName,
                    position = categoryIndex,
                    parentId = parentId
                )
                category.questions.forEachIndexed { questionIndex, question ->
                    questions += QuestionEntity(
                        id = UUID.randomUUID().toString(),
                        categoryId = categoryId,
                        questionText = question.questionText,
                        answerText = question.answerText,
                        position = questionIndex
                    )
                }
                addCategories(category.categories, categoryId)
            }
        }

        addCategories(content.categories, null)

        dao.insertPaperWithContent(
            paper = PaperEntity(paperId, content.paperName, importedAt),
            categories = categories,
            questions = questions
        )

        return requireNotNull(getPaper(paperId)) {
            "The imported paper could not be read back from the database."
        }
    }

    suspend fun createPaper(name: String): PaperDetail {
        val paperId = UUID.randomUUID().toString()
        dao.insertPaper(
            PaperEntity(
                id = paperId,
                name = name,
                importedAt = System.currentTimeMillis()
            )
        )
        return requireNotNull(getPaper(paperId)) {
            "The new paper could not be read back from the database."
        }
    }

    suspend fun renamePaper(paperId: String, name: String) {
        val updated = dao.updatePaperName(paperId, name)
        check(updated == 1) { "The paper could not be found." }
    }

    suspend fun addCategory(paperId: String, name: String, parentId: String? = null) {
        if (parentId != null) {
            val parent = dao.findCategory(parentId)
                ?: error("The parent category could not be found.")
            check(parent.paperId == paperId) {
                "The parent category belongs to a different paper."
            }
        }

        dao.insertCategory(
            CategoryEntity(
                id = UUID.randomUUID().toString(),
                paperId = paperId,
                name = name,
                position = dao.nextCategoryPosition(paperId, parentId),
                parentId = parentId
            )
        )
    }

    suspend fun renameCategory(categoryId: String, name: String) {
        val updated = dao.updateCategoryName(categoryId, name)
        check(updated == 1) { "The category could not be found." }
    }

    /**
     * The database's self-referential cascade removes every nested
     * subcategory, and each subcategory's questions, when the root is deleted.
     */
    suspend fun deleteCategory(categoryId: String) {
        val deleted = dao.deleteCategory(categoryId)
        check(deleted == 1) { "The category could not be found." }
    }

    suspend fun getPaper(paperId: String): PaperDetail? {
        val paper = dao.findPaper(paperId) ?: return null
        val categories = dao.findCategories(paperId)
        val questionsByCategory = dao.findQuestions(paperId).groupBy { it.categoryId }
        val categoriesByParent = categories.groupBy { it.parentId }

        fun buildCategory(entity: CategoryEntity): CategoryDetail = CategoryDetail(
            id = entity.id,
            name = entity.name,
            questions = questionsByCategory[entity.id]
                .orEmpty()
                .map { question ->
                    QuestionDetail(
                        id = question.id,
                        questionText = question.questionText,
                        answerText = question.answerText
                    )
                },
            children = categoriesByParent[entity.id]
                .orEmpty()
                .map(::buildCategory)
        )

        return PaperDetail(
            id = paper.id,
            name = paper.name,
            categories = categoriesByParent[null].orEmpty().map(::buildCategory)
        )
    }

    suspend fun updateQuestion(questionId: String, questionText: String, answerText: String) {
        val updated = dao.updateQuestion(questionId, questionText, answerText)
        check(updated == 1) { "The question could not be found. It may have been deleted." }
    }

    suspend fun addQuestion(categoryId: String, questionText: String, answerText: String) {
        dao.insertQuestion(
            QuestionEntity(
                id = UUID.randomUUID().toString(),
                categoryId = categoryId,
                questionText = questionText,
                answerText = answerText,
                position = dao.nextQuestionPosition(categoryId)
            )
        )
    }

    suspend fun deleteQuestion(questionId: String) {
        val deleted = dao.deleteQuestion(questionId)
        check(deleted == 1) { "The question could not be found. It may have been deleted." }
    }

    suspend fun deletePaper(paperId: String) {
        dao.deletePaper(paperId)
    }
}
