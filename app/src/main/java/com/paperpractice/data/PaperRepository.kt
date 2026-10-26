package com.paperpractice.data

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

        content.categories.forEachIndexed { categoryIndex, category ->
            val categoryId = UUID.randomUUID().toString()
            categories += CategoryEntity(
                id = categoryId,
                paperId = paperId,
                name = category.categoryName,
                position = categoryIndex
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
        }

        dao.insertPaperWithContent(
            paper = PaperEntity(paperId, content.paperName, importedAt),
            categories = categories,
            questions = questions
        )

        return requireNotNull(getPaper(paperId)) {
            "The imported paper could not be read back from the database."
        }
    }

    suspend fun getPaper(paperId: String): PaperDetail? {
        val paper = dao.findPaper(paperId) ?: return null
        val categories = dao.findCategories(paperId)
        val questionsByCategory = dao.findQuestions(paperId).groupBy { it.categoryId }

        return PaperDetail(
            id = paper.id,
            name = paper.name,
            categories = categories.map { category ->
                CategoryDetail(
                    id = category.id,
                    name = category.name,
                    questions = questionsByCategory[category.id]
                        .orEmpty()
                        .map { question ->
                            QuestionDetail(
                                id = question.id,
                                questionText = question.questionText,
                                answerText = question.answerText
                            )
                        }
                )
            }
        )
    }

    suspend fun deletePaper(paperId: String) {
        dao.deletePaper(paperId)
    }
}
