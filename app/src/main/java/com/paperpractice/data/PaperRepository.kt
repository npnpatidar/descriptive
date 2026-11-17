package com.paperpractice.data

import androidx.room.withTransaction
import com.paperpractice.domain.CategoryContent
import com.paperpractice.domain.ImageContent
import com.paperpractice.domain.PaperContent
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** NFC + lowercase identity shared by the tag library, importer and editor. */
internal fun normalizeTagKey(value: String): String =
    java.text.Normalizer.normalize(
        value.trim(),
        java.text.Normalizer.Form.NFC
    ).lowercase()

/** A question may carry at most this many distinct tags (matches the parser). */
internal const val MAX_QUESTION_TAGS = 20

/** The two image sets of one question, loaded on demand from the database. */
data class QuestionImageSet(
    val questionImages: List<ImageContent> = emptyList(),
    val answerImages: List<ImageContent> = emptyList()
)

class PaperRepository(private val database: AppDatabase) {

    private val dao = database.paperDao()

    fun observePapers(): Flow<List<PaperSummary>> = dao.observePaperSummaries()

    // ---- Global tag library -------------------------------------------------

    /** Live view of every tag, ordered by normalized name. */
    fun observeTags(): Flow<List<Tag>> = dao.observeTags().map { tags ->
        tags.map { it.toTag() }
    }

    suspend fun getTags(): List<Tag> = dao.findTags().map { it.toTag() }

    /**
     * Creates a tag, or returns null when the name is blank, over 40 chars,
     * contains a CR/LF, or duplicates an existing tag ignoring case.
     */
    suspend fun createTag(name: String): Tag? = database.withTransaction {
        val parts = validTagName(name) ?: return@withTransaction null
        if (dao.findTagByNormalizedName(parts.normalizedName) != null) {
            return@withTransaction null
        }
        val entity = TagEntity(
            id = UUID.randomUUID().toString(),
            name = parts.name,
            normalizedName = parts.normalizedName
        )
        dao.insertTag(entity)
        entity.toTag()
    }

    /** Returns false on an invalid/duplicate name or a missing tag. */
    suspend fun renameTag(tagId: String, newName: String): Boolean =
        database.withTransaction {
            val parts = validTagName(newName) ?: return@withTransaction false
            if (dao.findTagById(tagId) == null) return@withTransaction false
            val clash = dao.findTagByNormalizedName(parts.normalizedName)
            if (clash != null && clash.id != tagId) return@withTransaction false
            dao.updateTag(tagId, parts.name, parts.normalizedName) == 1
        }

    /** Returns true when a tag was actually removed. */
    suspend fun deleteTag(tagId: String): Boolean = database.withTransaction {
        dao.deleteTag(tagId) == 1
    }

    /**
     * Moves every link from the source tag to the target (dropping duplicates
     * first), then removes the source tag. Refuses a missing source/target or
     * a self-merge. Multi-step, so it runs in one transaction.
     */
    suspend fun mergeTags(sourceTagId: String, targetTagId: String): Boolean =
        database.withTransaction {
            if (sourceTagId == targetTagId) return@withTransaction false
            if (dao.findTagById(sourceTagId) == null) return@withTransaction false
            if (dao.findTagById(targetTagId) == null) return@withTransaction false
            dao.deleteDuplicateTagLinks(sourceTagId, targetTagId)
            dao.reassignTagLinks(sourceTagId, targetTagId)
            dao.deleteTag(sourceTagId)
            true
        }

    /** Deletes every tag with no question links and returns how many were removed. */
    suspend fun clearUnusedTags(): Int = database.withTransaction {
        dao.deleteUnusedTags()
    }

    /** Replaces a question's links with the distinct set of valid tag ids. */
    suspend fun setQuestionTags(questionId: String, tagIds: Set<String>) {
        database.withTransaction {
            replaceQuestionTagLinks(questionId, tagIds)
        }
    }

    /**
     * Copies a validated paper into the local database in one transaction.
     * A partially imported paper can therefore never be left behind.
     */
    suspend fun importPaper(
        content: PaperContent,
        mode: ImportMode = ImportMode.KEEP_BOTH
    ): ImportedPaper = database.withTransaction {
        importPaperInternal(content, mode)
    }

    suspend fun importPapers(
        contents: List<PaperContent>,
        mode: ImportMode
    ): List<ImportedPaper> = database.withTransaction {
        // Snapshot the papers that exist before the batch starts. A paper
        // inserted by an earlier iteration must never be mistaken for a
        // pre-existing name conflict (KEEP_BOTH duplicates in a backup).
        val existingByName = mutableMapOf<String, PaperEntity>()
        dao.findPapers().forEach { paper ->
            val key = normalize(paper.name)
            if (!existingByName.containsKey(key)) existingByName[key] = paper
        }
        // Names created by this batch are always inserted as new papers; only
        // the pre-batch snapshot may decide a MERGE/REPLACE conflict.
        val createdNames = mutableSetOf<String>()
        contents.map { content ->
            val result = importPaperInternal(content, mode, existingByName, createdNames)
            createdNames += normalize(content.paperName)
            result
        }
    }

    suspend fun existingPaperNames(names: Collection<String>): Set<String> {
        val papers = dao.findPapers()
        return names.mapNotNull { name ->
            papers.firstOrNull { normalize(it.name) == normalize(name) }?.name
        }.toSet()
    }

    suspend fun findPaperIdByName(name: String): String? =
        findMatchingPaper(name)?.id

    /**
     * Name identity is decided in Kotlin with [normalize] (Unicode-aware),
     * not with SQL lower(), which only folds ASCII characters.
     */
    private suspend fun findMatchingPaper(name: String): PaperEntity? =
        dao.findPapers().firstOrNull { normalize(it.name) == normalize(name) }

    private suspend fun importPaperInternal(
        content: PaperContent,
        mode: ImportMode,
        existingByName: Map<String, PaperEntity>? = null,
        createdNames: Set<String>? = null
    ): ImportedPaper {
        val normalizedName = normalize(content.paperName)
        val existing = when {
            createdNames?.contains(normalizedName) == true -> null
            existingByName != null -> existingByName[normalizedName]
            else -> findMatchingPaper(content.paperName)
        }
        val paperId = when {
            existing == null || mode == ImportMode.KEEP_BOTH -> insertNewPaper(content)

            mode == ImportMode.REPLACE -> {
                dao.deletePaper(existing.id)
                insertNewPaper(content)
            }

            else -> {
                mergeContentIntoPaper(existing.id, content)
                existing.id
            }
        }
        val summary = requireNotNull(dao.findPaperSummary(paperId)) {
            "The imported paper could not be read back from the database."
        }
        return ImportedPaper(
            id = summary.id,
            name = summary.name,
            questionCount = summary.questionCount
        )
    }

    private suspend fun insertNewPaper(content: PaperContent): String {
        val paperId = UUID.randomUUID().toString()
        val importedAt = System.currentTimeMillis()
        val categories = mutableListOf<CategoryEntity>()
        val questions = mutableListOf<QuestionEntity>()
        val images = mutableListOf<QuestionImageEntity>()
        val tagsByQuestion = mutableMapOf<String, List<String>>()

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
                    val questionId = UUID.randomUUID().toString()
                    questions += QuestionEntity(
                        id = questionId,
                        categoryId = categoryId,
                        questionText = question.questionText,
                        answerText = question.answerText,
                        position = questionIndex
                    )
                    tagsByQuestion[questionId] = question.tags
                    images += question.questionImages.toImageEntities(
                        questionId,
                        TARGET_QUESTION
                    )
                    images += question.answerImages.toImageEntities(
                        questionId,
                        TARGET_ANSWER
                    )
                }
                addCategories(category.categories, categoryId)
            }
        }

        addCategories(content.categories, null)

        dao.insertPaper(
            PaperEntity(
                id = paperId,
                name = content.paperName,
                importedAt = importedAt,
                position = dao.nextPaperPosition()
            )
        )
        if (categories.isNotEmpty()) dao.insertCategories(categories)
        if (questions.isNotEmpty()) dao.insertQuestions(questions)
        if (images.isNotEmpty()) dao.insertQuestionImages(images)
        linkTagsForQuestions(tagsByQuestion)

        return paperId
    }

    /**
     * Merges into an existing paper by category path and question text. New
     * questions/categories are added; matching questions have their answer
     * replaced when the incoming answer is not blank.
     */
    private suspend fun mergeContentIntoPaper(paperId: String, content: PaperContent) {
        val tagsByQuestion = mutableMapOf<String, List<String>>()

        suspend fun mergeCategory(source: CategoryContent, parentId: String?) {
            val siblings = dao.findCategoriesByParent(paperId, parentId)
            val existing = siblings.firstOrNull {
                normalize(it.name) == normalize(source.categoryName)
            }
            val categoryId = existing?.id ?: UUID.randomUUID().toString().also { id ->
                dao.insertCategory(
                    CategoryEntity(
                        id = id,
                        paperId = paperId,
                        name = source.categoryName,
                        position = dao.nextCategoryPosition(paperId, parentId),
                        parentId = parentId
                    )
                )
            }

            val existingQuestions = dao.findCategoryQuestions(categoryId).toMutableList()
            source.questions.forEach { question ->
                val match = existingQuestions.firstOrNull {
                    normalize(it.questionText) == normalize(question.questionText)
                }
                if (match == null) {
                    val questionId = UUID.randomUUID().toString()
                    val entity = QuestionEntity(
                        id = questionId,
                        categoryId = categoryId,
                        questionText = question.questionText,
                        answerText = question.answerText,
                        position = dao.nextQuestionPosition(categoryId)
                    )
                    dao.insertQuestion(entity)
                    existingQuestions += entity
                    tagsByQuestion[questionId] = question.tags
                    val images = question.questionImages.toImageEntities(
                        questionId,
                        TARGET_QUESTION
                    ) + question.answerImages.toImageEntities(questionId, TARGET_ANSWER)
                    if (images.isNotEmpty()) dao.insertQuestionImages(images)
                } else {
                    // MERGE never removes existing links: inserting with IGNORE
                    // unions the incoming names with the matched question's tags.
                    val mergedNames =
                        tagsByQuestion.getOrPut(match.id) { emptyList() } + question.tags
                    val distinctKeys = mergedNames.mapNotNull {
                        validTagName(it)?.normalizedName
                    }.toSet()
                    if (distinctKeys.size > MAX_QUESTION_TAGS) {
                        throw IllegalArgumentException("A question can have at most 20 tags.")
                    }
                    tagsByQuestion[match.id] = mergedNames
                    if (question.answerText.isNotBlank() &&
                        match.answerText != question.answerText
                    ) {
                        dao.updateQuestionAnswer(match.id, question.answerText)
                    }
                    if (question.questionImages.isNotEmpty()) {
                        replaceImages(match.id, TARGET_QUESTION, question.questionImages)
                    }
                    if (question.answerImages.isNotEmpty()) {
                        replaceImages(match.id, TARGET_ANSWER, question.answerImages)
                    }
                }
            }

            source.categories.forEach { child ->
                mergeCategory(child, categoryId)
            }
        }

        content.categories.forEach { mergeCategory(it, null) }
        // A MERGE unions incoming names with the links a matched question
        // already has. Validate that true post-merge result before writing any
        // link, so a question never ends up with more tags than the parser
        // accepts (which would make its own JSON/backups unreadable).
        validateMergedTagCounts(tagsByQuestion)
        linkTagsForQuestions(tagsByQuestion)
    }

    suspend fun createPaper(name: String): PaperDetail = database.withTransaction {
        val paperId = UUID.randomUUID().toString()
        dao.insertPaper(
            PaperEntity(
                id = paperId,
                name = name,
                importedAt = System.currentTimeMillis(),
                position = dao.nextPaperPosition()
            )
        )
        requireNotNull(getPaper(paperId)) {
            "The new paper could not be read back from the database."
        }
    }

    suspend fun renamePaper(paperId: String, name: String) {
        val updated = dao.updatePaperName(paperId, name)
        check(updated == 1) { "The paper could not be found." }
    }

    suspend fun addCategory(paperId: String, name: String, parentId: String? = null) {
        database.withTransaction {
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

    suspend fun getPaper(
        paperId: String,
        includeImages: Boolean = true
    ): PaperDetail? = database.withTransaction {
        val paper = dao.findPaper(paperId) ?: return@withTransaction null
        val categories = dao.findCategories(paperId)
        val questionsByCategory = dao.findQuestions(paperId).groupBy { it.categoryId }
        val categoriesByParent = categories.groupBy { it.parentId }
        val imagesByQuestion = if (includeImages) {
            dao.findQuestionImages(paperId).groupBy { it.questionId }
        } else {
            emptyMap()
        }
        // One batched query for the whole paper, never one query per question.
        val tagsByQuestion = findTagNamesByQuestionIds(
            questionsByCategory.values.flatten().map { it.id }
        )

        fun buildCategory(entity: CategoryEntity): CategoryDetail = CategoryDetail(
            id = entity.id,
            name = entity.name,
            questions = questionsByCategory[entity.id]
                .orEmpty()
                .map { question ->
                    val images = imagesByQuestion[question.id].orEmpty()
                    QuestionDetail(
                        id = question.id,
                        questionText = question.questionText,
                        answerText = question.answerText,
                        questionImages = images
                            .filter { it.target == TARGET_QUESTION }
                            .map { it.toImageContent() },
                        answerImages = images
                            .filter { it.target == TARGET_ANSWER }
                            .map { it.toImageContent() },
                        tags = tagsByQuestion[question.id].orEmpty()
                    )
                },
            children = categoriesByParent[entity.id]
                .orEmpty()
                .map(::buildCategory)
        )

        PaperDetail(
            id = paper.id,
            name = paper.name,
            categories = categoriesByParent[null].orEmpty().map(::buildCategory)
        )
    }

    /**
     * Loads only the paper's structure (categories, question text and answers),
     * without reading any image BLOBs. Use this for refreshes and other places
     * that do not display or export image bytes; hydrate images on demand with
     * [findImagesByQuestionIds] when they are actually needed.
     */
    suspend fun getPaperTree(paperId: String): PaperDetail? =
        getPaper(paperId, includeImages = false)

    /**
     * Reads image bytes for an explicit set of questions, grouped by question
     * id and target. The result can be merged into a tree loaded with
     * [getPaperTree] to hydrate only the questions being viewed.
     *
     * Room expands a collection parameter into one host parameter per element,
     * and SQLite on older supported devices caps those at 999. Ids are queried
     * in bounded batches and merged in input order.
     */
    suspend fun findImagesByQuestionIds(
        questionIds: Collection<String>
    ): Map<String, QuestionImageSet> {
        val ids = questionIds.distinct()
        if (ids.isEmpty()) return emptyMap()
        val result = linkedMapOf<String, QuestionImageSet>()
        ids.chunked(ID_QUERY_BATCH_SIZE).forEach { batch ->
            dao.findImagesByQuestionIds(batch)
                .groupBy { it.questionId }
                .forEach { (questionId, images) ->
                    // Ids are distinct, so every question's rows come from a
                    // single batch and no per-question merge is necessary.
                    result[questionId] = QuestionImageSet(
                        questionImages = images
                            .filter { it.target == TARGET_QUESTION }
                            .map { it.toImageContent() },
                        answerImages = images
                            .filter { it.target == TARGET_ANSWER }
                            .map { it.toImageContent() }
                    )
                }
        }
        return result
    }

    /**
     * Tag display names for an explicit set of questions, grouped by question
     * and sorted case-insensitively. Uses the same bounded batching as the
     * image lookup so a large paper never triggers an N+1 query per question.
     */
    suspend fun findTagNamesByQuestionIds(
        questionIds: Collection<String>
    ): Map<String, List<String>> {
        val ids = questionIds.distinct()
        if (ids.isEmpty()) return emptyMap()
        val result = linkedMapOf<String, List<String>>()
        ids.chunked(ID_QUERY_BATCH_SIZE).forEach { batch ->
            dao.findTagRowsByQuestionIds(batch)
                .groupBy { it.questionId }
                .forEach { (questionId, rows) ->
                    result[questionId] = rows
                        .map { it.name }
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
                }
        }
        return result
    }

    private fun QuestionImageEntity.toImageContent(): ImageContent = ImageContent(
        id = id,
        mimeType = mimeType,
        width = width,
        height = height,
        bytes = data
    )

    suspend fun getAllPapers(): List<PaperDetail> = database.withTransaction {
        dao.findPapers().mapNotNull { getPaper(it.id) }
    }

    suspend fun search(
        rawQuery: String,
        limit: Int = 80,
        tagIds: Set<String> = emptySet()
    ): List<SearchHit> {
        val query = rawQuery.trim()
        if (query.isEmpty()) return emptyList()

        // QuestionDetail carries tag display names, so resolve the selected ids
        // to normalized names once and compare those. Tag ids are an
        // include-only filter on QUESTION hits: paper/category hits are left
        // untouched, and a question must carry at least one selected tag.
        val selectedTagNames = findTagsByIds(tagIds).mapTo(linkedSetOf<String>()) {
            it.name.tagKey()
        }
        val tagFilterActive = tagIds.isNotEmpty()

        val hits = mutableListOf<SearchHit>()
        dao.findPapers().forEach { paperEntity ->
            currentCoroutineContext().ensureActive()
            val paper = getPaper(paperEntity.id, includeImages = false)
                ?: return@forEach

            if (paper.name.contains(query, ignoreCase = true)) {
                hits += SearchHit(
                    kind = SearchHitKind.PAPER,
                    paperId = paper.id,
                    paperName = paper.name,
                    categoryId = null,
                    questionId = null,
                    categoryPath = "",
                    title = paper.name,
                    preview = "${paper.categoryCount} categories · " +
                        "${paper.questionCount} questions"
                )
            }

            paper.flattenCategories().forEach { node ->
                val path = node.displayName
                if (node.category.name.contains(query, ignoreCase = true) ||
                    path.contains(query, ignoreCase = true)
                ) {
                    hits += SearchHit(
                        kind = SearchHitKind.CATEGORY,
                        paperId = paper.id,
                        paperName = paper.name,
                        categoryId = node.category.id,
                        questionId = null,
                        categoryPath = path,
                        title = node.category.name,
                        preview = "Category · $path"
                    )
                }

                node.category.questions.forEach { question ->
                    val textMatches = question.questionText.contains(query, ignoreCase = true) ||
                        question.answerText.contains(query, ignoreCase = true)
                    val tagNameMatches = question.tags.any {
                        it.contains(query, ignoreCase = true)
                    }
                    val carriesSelected = !tagFilterActive ||
                        question.tags.any { it.tagKey() in selectedTagNames }
                    if ((textMatches || tagNameMatches) && carriesSelected) {
                        hits += SearchHit(
                            kind = SearchHitKind.QUESTION,
                            paperId = paper.id,
                            paperName = paper.name,
                            categoryId = node.category.id,
                            questionId = question.id,
                            categoryPath = path,
                            title = question.questionText,
                            preview = buildString {
                                append(path)
                                if (question.answerText.isNotBlank()) {
                                    append(" · Answer: ")
                                    append(question.answerText.take(160))
                                }
                            }
                        )
                    }
                }
            }
        }

        val lowered = query.lowercase()
        return hits
            .sortedWith(
                compareBy<SearchHit>(
                    { !it.title.lowercase().startsWith(lowered) },
                    { it.title.lowercase().indexOf(lowered).let { i -> if (i < 0) 999 else i } },
                    { it.paperName.lowercase() },
                    { it.categoryPath.lowercase() }
                )
            )
            .take(limit)
    }

    suspend fun updateQuestion(
        questionId: String,
        questionText: String,
        answerText: String,
        questionImages: List<ImageContent>? = null,
        answerImages: List<ImageContent>? = null,
        tagIds: Set<String> = emptySet()
    ): String = database.withTransaction {
        val updated = dao.updateQuestion(questionId, questionText, answerText)
        check(updated == 1) { "The question could not be found. It may have been deleted." }
        if (questionImages != null) {
            replaceImages(questionId, TARGET_QUESTION, questionImages)
        }
        if (answerImages != null) {
            replaceImages(questionId, TARGET_ANSWER, answerImages)
        }
        // An empty set means "not supplied" here, so an existing-tag-free edit
        // never wipes tags. Use setQuestionTags(id, emptySet()) to clear them.
        if (tagIds.isNotEmpty()) {
            replaceQuestionTagLinks(questionId, tagIds)
        }
        questionId
    }

    suspend fun addQuestion(
        categoryId: String,
        questionText: String,
        answerText: String,
        questionImages: List<ImageContent> = emptyList(),
        answerImages: List<ImageContent> = emptyList(),
        tagIds: Set<String> = emptySet()
    ): String = database.withTransaction {
        val questionId = UUID.randomUUID().toString()
        dao.insertQuestion(
            QuestionEntity(
                id = questionId,
                categoryId = categoryId,
                questionText = questionText,
                answerText = answerText,
                position = dao.nextQuestionPosition(categoryId)
            )
        )
        val images = questionImages.toImageEntities(questionId, TARGET_QUESTION) +
            answerImages.toImageEntities(questionId, TARGET_ANSWER)
        if (images.isNotEmpty()) dao.insertQuestionImages(images)
        replaceQuestionTagLinks(questionId, tagIds)
        questionId
    }

    private suspend fun replaceImages(
        questionId: String,
        target: String,
        images: List<ImageContent>
    ) {
        dao.deleteQuestionImages(questionId, target)
        val entities = images.toImageEntities(questionId, target)
        if (entities.isNotEmpty()) dao.insertQuestionImages(entities)
    }

    private fun List<ImageContent>.toImageEntities(
        questionId: String,
        target: String
    ): List<QuestionImageEntity> = mapIndexed { index, image ->
        QuestionImageEntity(
            id = image.id.ifBlank { UUID.randomUUID().toString() },
            questionId = questionId,
            target = target,
            mimeType = image.mimeType,
            width = image.width,
            height = image.height,
            position = index,
            data = image.bytes
        )
    }

    suspend fun deleteQuestion(questionId: String) {
        val deleted = dao.deleteQuestion(questionId)
        check(deleted == 1) { "The question could not be found. It may have been deleted." }
    }

    suspend fun deleteQuestions(questionIds: Collection<String>) {
        if (questionIds.isEmpty()) return
        dao.deleteQuestions(questionIds.toList())
    }

    suspend fun moveQuestions(
        questionIds: Collection<String>,
        targetCategoryId: String
    ) {
        if (questionIds.isEmpty()) return
        database.withTransaction {
            questionIds.forEach { questionId ->
                moveQuestionInternal(questionId, targetCategoryId, null)
            }
        }
    }

    suspend fun moveQuestion(
        questionId: String,
        targetCategoryId: String,
        targetIndex: Int? = null
    ) {
        database.withTransaction {
            moveQuestionInternal(questionId, targetCategoryId, targetIndex)
        }
    }

    private suspend fun moveQuestionInternal(
        questionId: String,
        targetCategoryId: String,
        targetIndex: Int?
    ) {
        val question = dao.findQuestion(questionId)
            ?: error("The question could not be found.")
        val targetCategory = dao.findCategory(targetCategoryId)
            ?: error("The destination category could not be found.")

        val sameCategory = question.categoryId == targetCategory.id
        val sourceSiblings = dao.findCategoryQuestions(question.categoryId)
            .filterNot { it.id == question.id }
        val targetSiblings = if (sameCategory) {
            sourceSiblings
        } else {
            dao.findCategoryQuestions(targetCategory.id).filterNot { it.id == question.id }
        }
        val insertAt = (targetIndex ?: targetSiblings.size)
            .coerceIn(0, targetSiblings.size)

        if (!sameCategory) {
            sourceSiblings.forEachIndexed { index, sibling ->
                dao.updateQuestionPosition(sibling.id, index)
            }
        }

        val reordered = targetSiblings.toMutableList().apply {
            add(insertAt, question)
        }
        reordered.forEachIndexed { index, item ->
            if (item.id == question.id) {
                dao.updateQuestionPlacement(question.id, targetCategory.id, index)
            } else {
                dao.updateQuestionPosition(item.id, index)
            }
        }
    }

    suspend fun reorderQuestion(questionId: String, delta: Int) {
        if (delta == 0) return
        val question = dao.findQuestion(questionId) ?: return
        val siblings = dao.findCategoryQuestions(question.categoryId)
        val index = siblings.indexOfFirst { it.id == questionId }
        if (index < 0) return
        val target = (index + delta).coerceIn(0, siblings.lastIndex)
        if (target == index) return
        moveQuestion(questionId, question.categoryId, target)
    }

    /**
     * Moves a category (with its whole subtree) to a new parent/paper. When
     * moving between papers the moved root becomes top-level unless a target
     * parent is supplied.
     */
    suspend fun moveCategory(
        categoryId: String,
        targetPaperId: String,
        targetParentId: String? = null,
        targetIndex: Int? = null
    ) {
        database.withTransaction {
            val category = dao.findCategory(categoryId)
                ?: error("The category could not be found.")
            dao.findPaper(targetPaperId)
                ?: error("The destination paper could not be found.")

            if (targetParentId != null) {
                val parent = dao.findCategory(targetParentId)
                    ?: error("The destination parent category could not be found.")
                check(parent.paperId == targetPaperId) {
                    "The destination parent belongs to a different paper."
                }
            }

            val sourcePaperCategories = dao.findCategories(category.paperId)
            val subtreeIds = collectSubtreeIds(sourcePaperCategories, category.id)
            if (targetParentId != null && targetParentId in subtreeIds) {
                error("A category cannot be moved inside itself.")
            }

            val samePlacement = category.paperId == targetPaperId &&
                category.parentId == targetParentId
            val sourceSiblings = dao.findCategoriesByParent(
                category.paperId,
                category.parentId
            ).filterNot { it.id == category.id }
            val targetSiblings = if (samePlacement) {
                sourceSiblings
            } else {
                dao.findCategoriesByParent(targetPaperId, targetParentId)
                    .filterNot { it.id == category.id }
            }
            val insertAt = (targetIndex ?: targetSiblings.size)
                .coerceIn(0, targetSiblings.size)

            if (category.paperId != targetPaperId) {
                dao.updateCategoryPaperForIds(targetPaperId, subtreeIds.toList())
            }

            if (!samePlacement) {
                sourceSiblings.forEachIndexed { index, sibling ->
                    dao.updateCategoryPosition(sibling.id, index)
                }
            }

            val reordered = targetSiblings.toMutableList().apply {
                add(insertAt, category)
            }
            reordered.forEachIndexed { index, item ->
                if (item.id == category.id) {
                    dao.updateCategoryPlacement(
                        category.id,
                        targetPaperId,
                        targetParentId,
                        index
                    )
                } else {
                    dao.updateCategoryPosition(item.id, index)
                }
            }
        }
    }

    suspend fun reorderCategory(categoryId: String, delta: Int) {
        if (delta == 0) return
        val category = dao.findCategory(categoryId) ?: return
        val siblings = dao.findCategoriesByParent(category.paperId, category.parentId)
        val index = siblings.indexOfFirst { it.id == categoryId }
        if (index < 0) return
        val target = (index + delta).coerceIn(0, siblings.lastIndex)
        if (target == index) return
        moveCategory(categoryId, category.paperId, category.parentId, target)
    }

    suspend fun mergeCategories(sourceId: String, targetId: String) {
        if (sourceId == targetId) return
        database.withTransaction {
            mergeCategoriesInternal(sourceId, targetId)
        }
    }

    /**
     * Merges every sibling category that shares the same normalized name,
     * recursively, into the first one. Returns the number of merged categories.
     */
    suspend fun mergeDuplicateCategories(paperId: String): Int = database.withTransaction {
        var merged = 0
        while (true) {
            val categories = dao.findCategories(paperId)
            val duplicateGroup = categories
                .groupBy { it.parentId }
                .values
                .asSequence()
                .flatMap { siblings ->
                    siblings.groupBy { normalize(it.name) }.values.asSequence()
                }
                .firstOrNull { it.size > 1 }
                ?: break

            val keep = duplicateGroup.minByOrNull { it.position } ?: break
            duplicateGroup
                .filterNot { it.id == keep.id }
                .forEach { source ->
                    mergeCategoriesInternal(source.id, keep.id)
                    merged++
                }
        }
        merged
    }

    private suspend fun mergeCategoriesInternal(sourceId: String, targetId: String) {
        val source = dao.findCategory(sourceId)
            ?: error("The source category could not be found.")
        val target = dao.findCategory(targetId)
            ?: error("The destination category could not be found.")
        check(source.paperId == target.paperId) {
            "Categories from different papers cannot be merged."
        }
        val categories = dao.findCategories(source.paperId)
        if (targetId in collectSubtreeIds(categories, sourceId)) {
            error("A category cannot be merged into one of its own subcategories.")
        }

        val targetQuestions = dao.findCategoryQuestions(targetId)
        val questionStart = (targetQuestions.maxOfOrNull { it.position } ?: -1) + 1
        dao.findCategoryQuestions(sourceId).forEachIndexed { index, question ->
            dao.updateQuestionPlacement(question.id, targetId, questionStart + index)
        }

        val targetChildren = dao.findCategoriesByParent(source.paperId, targetId)
        val childStart = (targetChildren.maxOfOrNull { it.position } ?: -1) + 1
        dao.findCategoriesByParent(source.paperId, sourceId).forEachIndexed { index, child ->
            dao.updateCategoryPlacement(child.id, source.paperId, targetId, childStart + index)
        }

        dao.deleteCategory(sourceId)

        val remainingTargetChildren = dao.findCategoriesByParent(
            source.paperId,
            targetId
        )
        remainingTargetChildren.forEachIndexed { index, child ->
            dao.updateCategoryPosition(child.id, index)
        }

        val remainingSourceSiblings = dao.findCategoriesByParent(
            source.paperId,
            source.parentId
        )
        if (source.parentId != targetId) {
            remainingSourceSiblings.forEachIndexed { index, sibling ->
                dao.updateCategoryPosition(sibling.id, index)
            }
        }
    }

    suspend fun reorderPaper(paperId: String, delta: Int) {
        if (delta == 0) return
        database.withTransaction {
            val papers = dao.findPapers()
            val index = papers.indexOfFirst { it.id == paperId }
            if (index < 0) return@withTransaction
            val target = (index + delta).coerceIn(0, papers.lastIndex)
            if (target == index) return@withTransaction
            val reordered = papers.toMutableList().apply {
                add(target, removeAt(index))
            }
            reordered.forEachIndexed { position, paper ->
                dao.updatePaperPosition(paper.id, position)
            }
        }
    }

    suspend fun deletePaper(paperId: String) {
        dao.deletePaper(paperId)
    }

    private fun collectSubtreeIds(
        categories: List<CategoryEntity>,
        rootId: String
    ): Set<String> {
        val childrenByParent = categories.groupBy { it.parentId }
        val result = linkedSetOf<String>()

        fun visit(id: String) {
            if (!result.add(id)) return
            childrenByParent[id].orEmpty().forEach { visit(it.id) }
        }

        visit(rootId)
        return result
    }

    private fun normalize(value: String): String = value.trim().lowercase()

    // ---- Tag helpers --------------------------------------------------------

    private data class ValidTagName(val name: String, val normalizedName: String)

    /** NFC first, then lowercase: the same identity the tags table enforces. */
    private fun String.tagKey(): String = normalizeTagKey(this)

    private fun validTagName(value: String): ValidTagName? {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_TAG_NAME_LENGTH) return null
        if (trimmed.any { it == '\n' || it == '\r' }) return null
        return ValidTagName(trimmed, trimmed.tagKey())
    }

    private fun TagEntity.toTag(): Tag = Tag(id, name, normalizedName)

    /** Bounded lookup of tag rows by id (Room expands IN into host parameters). */
    private suspend fun findTagsByIds(ids: Collection<String>): List<TagEntity> {
        val distinct = ids.distinct()
        if (distinct.isEmpty()) return emptyList()
        val result = mutableListOf<TagEntity>()
        distinct.chunked(ID_QUERY_BATCH_SIZE).forEach { batch ->
            result += dao.findTagsByIds(batch)
        }
        return result
    }

    /** Deletes then re-inserts the distinct, existing tag ids for a question. */
    private suspend fun replaceQuestionTagLinks(
        questionId: String,
        tagIds: Set<String>
    ) {
        val tags = findTagsByIds(tagIds)
        val distinctKeys = tags.mapTo(linkedSetOf<String>()) { it.normalizedName }
        if (distinctKeys.size > MAX_QUESTION_TAGS) {
            throw IllegalArgumentException("A question can have at most 20 tags.")
        }
        dao.deleteQuestionTags(questionId)
        val validIds = tags.map { it.id }
        if (validIds.isEmpty()) return
        dao.insertQuestionTags(validIds.map { QuestionTagEntity(questionId, it) })
    }

    /** Resolves display names to tag rows, creating missing tags once, globally. */
    private suspend fun resolveTagNames(names: Collection<String>): Map<String, TagEntity> {
        val normalizedToName = linkedMapOf<String, String>()
        names.forEach { raw ->
            val parts = validTagName(raw) ?: return@forEach
            if (!normalizedToName.containsKey(parts.normalizedName)) {
                normalizedToName[parts.normalizedName] = parts.name
            }
        }
        if (normalizedToName.isEmpty()) return emptyMap()

        val existing = dao.findTags().associateBy { it.normalizedName }
        val resolved = linkedMapOf<String, TagEntity>()
        val missing = mutableListOf<TagEntity>()
        normalizedToName.forEach { (normalized, displayName) ->
            val tag = existing[normalized] ?: TagEntity(
                id = UUID.randomUUID().toString(),
                name = displayName,
                normalizedName = normalized
            ).also { missing += it }
            resolved[normalized] = tag
        }
        if (missing.isNotEmpty()) dao.insertTags(missing)
        return resolved
    }

    /**
     * Validates the TRUE post-merge tag count for every question touched by a
     * MERGE. [linkTagsForQuestions] inserts with IGNORE and never deletes, so a
     * matched question keeps its existing links and the result is the UNION of
     * those links and the incoming names — not just the incoming names. Load the
     * existing normalized keys in bounded batches, combine them with the
     * incoming names, and reject any question over the cap before a single link
     * is written. The caller's transaction rolls the whole import back on throw.
     */
    private suspend fun validateMergedTagCounts(
        tagsByQuestion: Map<String, List<String>>
    ) {
        if (tagsByQuestion.isEmpty()) return
        val existingKeysByQuestion = linkedMapOf<String, Set<String>>()
        tagsByQuestion.keys.distinct().chunked(ID_QUERY_BATCH_SIZE).forEach { batch ->
            dao.findTagRowsByQuestionIds(batch)
                .groupBy { it.questionId }
                .forEach { (questionId, rows) ->
                    existingKeysByQuestion[questionId] = rows
                        .mapTo(linkedSetOf<String>()) { it.normalizedName }
                }
        }
        tagsByQuestion.forEach { (questionId, names) ->
            val unionKeys = existingKeysByQuestion[questionId].orEmpty() +
                names.mapNotNull { validTagName(it)?.normalizedName }
            if (unionKeys.size > MAX_QUESTION_TAGS) {
                throw IllegalArgumentException("A question can have at most 20 tags.")
            }
        }
    }

    /**
     * Resolves all incoming tag names in one pass and writes every link in a
     * single insert. The IGNORE conflict strategy makes this additive for MERGE:
     * links a matched question already had are preserved (UNION, never removal).
     */
    private suspend fun linkTagsForQuestions(
        tagsByQuestion: Map<String, List<String>>
    ) {
        val allNames = tagsByQuestion.values.flatten()
        if (allNames.isEmpty()) return
        val resolved = resolveTagNames(allNames)
        val links = mutableListOf<QuestionTagEntity>()
        tagsByQuestion.forEach { (questionId, names) ->
            val distinctKeys = names.mapNotNull {
                validTagName(it)?.normalizedName
            }.toSet()
            if (distinctKeys.size > MAX_QUESTION_TAGS) {
                throw IllegalArgumentException("A question can have at most 20 tags.")
            }
            names.forEach { raw ->
                val parts = validTagName(raw) ?: return@forEach
                resolved[parts.normalizedName]?.let { tag ->
                    links += QuestionTagEntity(questionId, tag.id)
                }
            }
        }
        if (links.isNotEmpty()) dao.insertQuestionTags(links.distinct())
    }

    private companion object {
        const val TARGET_QUESTION = "QUESTION"
        const val TARGET_ANSWER = "ANSWER"
        // Below the 999 host-parameter limit of older SQLite versions.
        const val ID_QUERY_BATCH_SIZE = 900
        const val MAX_TAG_NAME_LENGTH = 40
    }
}
