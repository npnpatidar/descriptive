package com.paperpractice.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    // Kept as a lookup helper; name identity for imports is decided in Kotlin
    // (PaperRepository.normalize) because SQLite lower() is ASCII-only.
    @Query("SELECT * FROM papers WHERE lower(name) = lower(:name) ORDER BY position ASC, id ASC LIMIT 1")
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
           AND parentId IS :parentId
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
           AND parentId IS :parentId
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

    @Query("UPDATE questions SET answerText = :answerText WHERE id = :questionId")
    abstract suspend fun updateQuestionAnswer(questionId: String, answerText: String): Int

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

    /**
     * Loads the image rows for an explicit set of questions in one bounded
     * query. This is the on-demand counterpart of [findQuestionImages]: callers
     * holding an image-free tree can fetch just the bytes they need, without a
     * whole-paper scan (and without an N+1 query per question).
     */
    @Query(
        """
        SELECT qi.*
          FROM question_images qi
         WHERE qi.questionId IN (:questionIds)
         ORDER BY qi.questionId ASC, qi.position ASC, qi.id ASC
        """
    )
    abstract suspend fun findImagesByQuestionIds(
        questionIds: List<String>
    ): List<QuestionImageEntity>

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

    // ---- Global tag library -------------------------------------------------

    @Query(
        """
        SELECT * FROM tags
         ORDER BY normalizedName ASC, name ASC, id ASC
        """
    )
    abstract fun observeTags(): Flow<List<TagEntity>>

    @Query(
        """
        SELECT * FROM tags
         ORDER BY normalizedName ASC, name ASC, id ASC
        """
    )
    abstract suspend fun findTags(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE id = :tagId")
    abstract suspend fun findTagById(tagId: String): TagEntity?

    @Query("SELECT * FROM tags WHERE normalizedName = :normalizedName LIMIT 1")
    abstract suspend fun findTagByNormalizedName(normalizedName: String): TagEntity?

    @Query("SELECT * FROM tags WHERE id IN (:tagIds)")
    abstract suspend fun findTagsByIds(tagIds: List<String>): List<TagEntity>

    @Insert
    abstract suspend fun insertTag(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTags(tags: List<TagEntity>)

    @Query("UPDATE tags SET name = :name, normalizedName = :normalizedName WHERE id = :tagId")
    abstract suspend fun updateTag(
        tagId: String,
        name: String,
        normalizedName: String
    ): Int

    @Query("DELETE FROM tags WHERE id = :tagId")
    abstract suspend fun deleteTag(tagId: String): Int

    /** Removes only tags with no question links; tagId is NOT NULL, so NOT IN is safe. */
    @Query("DELETE FROM tags WHERE id NOT IN (SELECT DISTINCT tagId FROM question_tags)")
    abstract suspend fun deleteUnusedTags(): Int

    /**
     * Rows for an explicit set of questions in one bounded query. Grouped and
     * sorted by the repository so there is no per-question lookup.
     */
    @Query(
        """
        SELECT qt.questionId AS questionId,
               t.id AS tagId,
               t.name AS name,
               t.normalizedName AS normalizedName
          FROM question_tags qt
          INNER JOIN tags t ON qt.tagId = t.id
         WHERE qt.questionId IN (:questionIds)
         ORDER BY qt.questionId ASC, t.name COLLATE NOCASE ASC, t.id ASC
        """
    )
    abstract suspend fun findTagRowsByQuestionIds(
        questionIds: List<String>
    ): List<QuestionTagRow>

    @Query("SELECT questionId FROM question_tags WHERE tagId = :tagId ORDER BY questionId ASC")
    abstract suspend fun findQuestionIdsByTagId(tagId: String): List<String>

    @Query("SELECT COUNT(*) FROM question_tags WHERE tagId = :tagId")
    abstract suspend fun countTagLinks(tagId: String): Int

    /**
     * Drops source links that would collide with an existing target link, so
     * the remaining source links can be reassigned without a primary-key
     * conflict. Pair with [reassignTagLinks] inside one transaction.
     */
    @Query(
        """
        DELETE FROM question_tags
         WHERE tagId = :sourceTagId
           AND questionId IN (
               SELECT questionId FROM question_tags WHERE tagId = :targetTagId
           )
        """
    )
    abstract suspend fun deleteDuplicateTagLinks(
        sourceTagId: String,
        targetTagId: String
    ): Int

    @Query("UPDATE question_tags SET tagId = :targetTagId WHERE tagId = :sourceTagId")
    abstract suspend fun reassignTagLinks(sourceTagId: String, targetTagId: String): Int

    @Query("DELETE FROM question_tags WHERE questionId = :questionId")
    abstract suspend fun deleteQuestionTags(questionId: String): Int

    /** IGNORE keeps existing links intact, which gives MERGE its UNION semantics. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertQuestionTags(links: List<QuestionTagEntity>)
}
