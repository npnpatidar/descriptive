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
         ORDER BY p.importedAt DESC, p.name COLLATE NOCASE ASC
        """
    )
    abstract fun observePaperSummaries(): Flow<List<PaperSummary>>

    @Query("SELECT * FROM papers WHERE id = :paperId")
    abstract suspend fun findPaper(paperId: String): PaperEntity?

    @Query("SELECT * FROM categories WHERE paperId = :paperId ORDER BY position ASC")
    abstract suspend fun findCategories(paperId: String): List<CategoryEntity>

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
