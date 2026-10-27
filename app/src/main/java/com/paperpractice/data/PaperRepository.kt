package com.paperpractice.data

import androidx.room.withTransaction
import com.paperpractice.domain.CategoryContent
import com.paperpractice.domain.ImageContent
import com.paperpractice.domain.PaperContent
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow

class PaperRepository(private val database: AppDatabase) {

    private val dao = database.paperDao()

    fun observePapers(): Flow<List<PaperSummary>> = dao.observePaperSummaries()

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
        contents.map { importPaperInternal(it, mode) }
    }

    suspend fun existingPaperNames(names: Collection<String>): Set<String> =
        names.mapNotNull { dao.findPaperByName(it)?.name }.toSet()

    private suspend fun importPaperInternal(
        content: PaperContent,
        mode: ImportMode
    ): ImportedPaper {
        val existing = dao.findPaperByName(content.paperName)
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

        return paperId
    }

    /**
     * Merges into an existing paper by category path and question text. New
     * questions/categories are added; matching questions have their answer
     * replaced when the incoming answer is not blank.
     */
    private suspend fun mergeContentIntoPaper(paperId: String, content: PaperContent) {
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
                    val images = question.questionImages.toImageEntities(
                        questionId,
                        TARGET_QUESTION
                    ) + question.answerImages.toImageEntities(questionId, TARGET_ANSWER)
                    if (images.isNotEmpty()) dao.insertQuestionImages(images)
                } else {
                    if (question.answerText.isNotBlank() &&
                        match.answerText != question.answerText
                    ) {
                        dao.updateQuestion(match.id, question.questionText, question.answerText)
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
    }

    suspend fun createPaper(name: String): PaperDetail {
        val paperId = UUID.randomUUID().toString()
        dao.insertPaper(
            PaperEntity(
                id = paperId,
                name = name,
                importedAt = System.currentTimeMillis(),
                position = dao.nextPaperPosition()
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

    suspend fun getPaper(
        paperId: String,
        includeImages: Boolean = true
    ): PaperDetail? {
        val paper = dao.findPaper(paperId) ?: return null
        val categories = dao.findCategories(paperId)
        val questionsByCategory = dao.findQuestions(paperId).groupBy { it.categoryId }
        val categoriesByParent = categories.groupBy { it.parentId }
        val imagesByQuestion = if (includeImages) {
            dao.findQuestionImages(paperId).groupBy { it.questionId }
        } else {
            emptyMap()
        }

        fun imageContent(image: QuestionImageEntity): ImageContent = ImageContent(
            id = image.id,
            mimeType = image.mimeType,
            width = image.width,
            height = image.height,
            bytes = image.data
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
                            .map(::imageContent),
                        answerImages = images
                            .filter { it.target == TARGET_ANSWER }
                            .map(::imageContent)
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

    suspend fun getAllPapers(): List<PaperDetail> = database.withTransaction {
        dao.findPapers().mapNotNull { getPaper(it.id) }
    }

    suspend fun search(rawQuery: String, limit: Int = 80): List<SearchHit> {
        val query = rawQuery.trim()
        if (query.isEmpty()) return emptyList()

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
                    if (question.questionText.contains(query, ignoreCase = true) ||
                        question.answerText.contains(query, ignoreCase = true)
                    ) {
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
        answerImages: List<ImageContent>? = null
    ): String = database.withTransaction {
        val updated = dao.updateQuestion(questionId, questionText, answerText)
        check(updated == 1) { "The question could not be found. It may have been deleted." }
        if (questionImages != null) {
            replaceImages(questionId, TARGET_QUESTION, questionImages)
        }
        if (answerImages != null) {
            replaceImages(questionId, TARGET_ANSWER, answerImages)
        }
        questionId
    }

    suspend fun addQuestion(
        categoryId: String,
        questionText: String,
        answerText: String,
        questionImages: List<ImageContent> = emptyList(),
        answerImages: List<ImageContent> = emptyList()
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

    private companion object {
        const val TARGET_QUESTION = "QUESTION"
        const val TARGET_ANSWER = "ANSWER"
    }
}
