package com.paperpractice.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PaperDao {

    @Query(
        """
        SELECT p.id AS id,
               p.name AS name,
               p.importedAt AS importedAt,
               (SELECT COUNT(*) FROM categories c WHERE c.paperId = p.id) AS categoryCount,
               (SELECT COUNT(*)
                  FROM questions q
                  INNER JOIN categories c2 ON q.categoryId = c2.id
                 WHERE c2.paperId = p.id) AS questionCount
          FROM papers p
         ORDER BY p.position ASC, p.importedAt DESC, p.name COLLATE NOCASE ASC
        """
    )
    abstract fun observePaperSummaries(): Flow<List<PaperSummary>>

    @Query("SELECT * FROM papers WHERE id = :paperId")
    abstract suspend fun findPaper(paperId: String): PaperEntity?

    @Query(
        """
        SELECT p.id AS id,
               p.name AS name,
               p.importedAt AS importedAt,
               (SELECT COUNT(*) FROM categories c WHERE c.paperId = p.id) AS categoryCount,
               (SELECT COUNT(*)
                  FROM questions q
                  INNER JOIN categories c2 ON q.categoryId = c2.id
                 WHERE c2.paperId = p.id) AS questionCount
          FROM papers p
         WHERE p.id = :paperId
        """
    )
    abstract suspend fun findPaperSummary(paperId: String): PaperSummary?

    @Query(
        """
        SELECT *
          FROM papers
         ORDER BY position ASC, importedAt DESC, name COLLATE NOCASE ASC
        """
    )
    abstract suspend fun findPapers(): List<PaperEntity>

    @Query("SELECT * FROM papers WHERE lower(name) = lower(:name) LIMIT 1")
    abstract suspend fun findPaperByName(name: String): PaperEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM papers")
    abstract suspend fun nextPaperPosition(): Int

    @Query("UPDATE papers SET name = :name WHERE id = :paperId")
    abstract suspend fun updatePaperName(paperId: String, name: String): Int

    @Query("UPDATE papers SET position = :position WHERE id = :paperId")
    abstract suspend fun updatePaperPosition(paperId: String, position: Int): Int

    @Query("SELECT * FROM categories WHERE paperId = :paperId ORDER BY position ASC, id ASC")
    abstract suspend fun findCategories(paperId: String): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :categoryId")
    abstract suspend fun findCategory(categoryId: String): CategoryEntity?

    @Insert
    abstract suspend fun insertCategory(category: CategoryEntity)

    @Query("UPDATE categories SET name = :name WHERE id = :categoryId")
    abstract suspend fun updateCategoryName(categoryId: String, name: String): Int

    @Query("DELETE FROM categories WHERE id = :categoryId")
    abstract suspend fun deleteCategory(categoryId: String): Int

    @Query(
        """
        SELECT *
          FROM categories
         WHERE paperId = :paperId
           AND ((:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId)
         ORDER BY position ASC, id ASC
        """
    )
    abstract suspend fun findCategoriesByParent(
        paperId: String,
        parentId: String?
    ): List<CategoryEntity>

    @Query(
        """
        UPDATE categories
           SET paperId = :paperId,
               parentId = :parentId,
               position = :position
         WHERE id = :categoryId
        """
    )
    abstract suspend fun updateCategoryPlacement(
        categoryId: String,
        paperId: String,
        parentId: String?,
        position: Int
    ): Int

    @Query("UPDATE categories SET position = :position WHERE id = :categoryId")
    abstract suspend fun updateCategoryPosition(categoryId: String, position: Int): Int

    @Query("UPDATE categories SET paperId = :paperId WHERE id IN (:categoryIds)")
    abstract suspend fun updateCategoryPaperForIds(
        paperId: String,
        categoryIds: List<String>
    ): Int

    @Query(
        """
        SELECT COALESCE(MAX(position), -1) + 1
          FROM categories
         WHERE paperId = :paperId
           AND ((:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId)
        """
    )
    abstract suspend fun nextCategoryPosition(paperId: String, parentId: String?): Int

    @Query(
        """
        SELECT q.*
          FROM questions q
          INNER JOIN categories c ON q.categoryId = c.id
         WHERE c.paperId = :paperId
         ORDER BY c.position ASC, q.position ASC
        """
    )
    abstract suspend fun findQuestions(paperId: String): List<QuestionEntity>

    @Insert
    abstract suspend fun insertPaper(paper: PaperEntity)

    @Insert
    abstract suspend fun insertCategories(categories: List<CategoryEntity>)

    @Insert
    abstract suspend fun insertQuestions(questions: List<QuestionEntity>)

    @Insert
    abstract suspend fun insertQuestion(question: QuestionEntity)

    @Query(
        """
        UPDATE questions
           SET questionText = :questionText,
               answerText = :answerText
         WHERE id = :questionId
        """
    )
    abstract suspend fun updateQuestion(
        questionId: String,
        questionText: String,
        answerText: String
    ): Int

    @Query("DELETE FROM questions WHERE id = :questionId")
    abstract suspend fun deleteQuestion(questionId: String): Int

    @Query("DELETE FROM questions WHERE id IN (:questionIds)")
    abstract suspend fun deleteQuestions(questionIds: List<String>): Int

    @Query("SELECT * FROM questions WHERE id = :questionId")
    abstract suspend fun findQuestion(questionId: String): QuestionEntity?

    @Query(
        """
        SELECT *
          FROM questions
         WHERE categoryId = :categoryId
         ORDER BY position ASC, id ASC
        """
    )
    abstract suspend fun findCategoryQuestions(categoryId: String): List<QuestionEntity>

    @Query(
        """
        UPDATE questions
           SET categoryId = :categoryId,
               position = :position
         WHERE id = :questionId
        """
    )
    abstract suspend fun updateQuestionPlacement(
        questionId: String,
        categoryId: String,
        position: Int
    ): Int

    @Query("UPDATE questions SET position = :position WHERE id = :questionId")
    abstract suspend fun updateQuestionPosition(questionId: String, position: Int): Int

    @Query(
        """
        SELECT qi.*
          FROM question_images qi
          INNER JOIN questions q ON qi.questionId = q.id
          INNER JOIN categories c ON q.categoryId = c.id
         WHERE c.paperId = :paperId
         ORDER BY qi.position ASC, qi.id ASC
        """
    )
    abstract suspend fun findQuestionImages(paperId: String): List<QuestionImageEntity>

    @Insert
    abstract suspend fun insertQuestionImages(images: List<QuestionImageEntity>)

    @Query(
        """
        DELETE FROM question_images
         WHERE questionId = :questionId AND target = :target
        """
    )
    abstract suspend fun deleteQuestionImages(questionId: String, target: String): Int

    @Query(
        """
        SELECT COALESCE(MAX(position), -1) + 1
          FROM questions
         WHERE categoryId = :categoryId
        """
    )
    abstract suspend fun nextQuestionPosition(categoryId: String): Int

    @Query("DELETE FROM papers WHERE id = :paperId")
    abstract suspend fun deletePaper(paperId: String)

    @Transaction
    open suspend fun insertPaperWithContent(
        paper: PaperEntity,
        categories: List<CategoryEntity>,
        questions: List<QuestionEntity>
    ) {
        insertPaper(paper)
        if (categories.isNotEmpty()) insertCategories(categories)
        if (questions.isNotEmpty()) insertQuestions(questions)
    }
}
