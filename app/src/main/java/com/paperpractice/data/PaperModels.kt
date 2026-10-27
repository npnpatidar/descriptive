package com.paperpractice.data

/** Lightweight row used by the home screen so large papers are never loaded. */
data class PaperSummary(
    val id: String,
    val name: String,
    val importedAt: Long,
    val categoryCount: Int,
    val questionCount: Int
)

/** Lightweight result returned after an import or restore. */
data class ImportedPaper(
    val id: String,
    val name: String,
    val questionCount: Int
)

enum class ImportMode {
    KEEP_BOTH,
    MERGE,
    REPLACE
}

data class ImportConflict(
    val paperNames: List<String>,
    val isBackup: Boolean
) {
    val title: String
        get() = if (isBackup) {
            "Some papers are already on this device"
        } else {
            "This paper is already on this device"
        }

    val detail: String
        get() {
            val names = paperNames.take(3).joinToString(", ") { "\"$it\"" }
            val suffix = if (paperNames.size > 3) {
                " and ${paperNames.size - 3} more"
            } else {
                ""
            }
            return "$names$suffix already exist. Choose how to import them."
        }
}

enum class SearchHitKind { PAPER, CATEGORY, QUESTION }

data class SearchHit(
    val kind: SearchHitKind,
    val paperId: String,
    val paperName: String,
    val categoryId: String?,
    val questionId: String?,
    val categoryPath: String,
    val title: String,
    val preview: String
)

data class PaperDetail(
    val id: String,
    val name: String,
    val categories: List<CategoryDetail>
) {
    /** Includes categories at every nesting level. */
    val categoryCount: Int
        get() = categories.sumOf { it.totalCategoryCount }

    val questionCount: Int
        get() = categories.sumOf { it.totalQuestionCount }

    /** Depth-first order, matching the order shown in the app. */
    fun flattenCategories(): List<CategoryNode> {
        val result = mutableListOf<CategoryNode>()

        fun visit(
            categories: List<CategoryDetail>,
            depth: Int,
            parentPath: List<String>,
            parentIds: List<String>
        ) {
            categories.forEach { category ->
                val path = parentPath + category.name
                val idPath = parentIds + category.id
                result += CategoryNode(category, depth, path, idPath)
                visit(category.children, depth + 1, path, idPath)
            }
        }

        visit(categories, 0, emptyList(), emptyList())
        return result
    }

    /**
     * Categories that cannot be selected together with [categoryId]: its
     * ancestors and descendants. Selecting a category counts all questions in
     * its whole subtree, so overlapping selections would double-count.
     */
    fun selectionConflicts(categoryId: String): Set<String> {
        val lineage = categoryLineage(categoryId).orEmpty().map { it.id }
        val descendants = findCategory(categoryId)?.subtreeCategoryIds().orEmpty()
        return (lineage + descendants).toSet()
    }

    fun categoryLineage(categoryId: String): List<CategoryDetail>? {
        fun search(
            categories: List<CategoryDetail>,
            path: List<CategoryDetail>
        ): List<CategoryDetail>? {
            categories.forEach { category ->
                val currentPath = path + category
                if (category.id == categoryId) return currentPath
                search(category.children, currentPath)?.let { return it }
            }
            return null
        }

        return search(categories, emptyList())
    }

    fun findCategory(categoryId: String): CategoryDetail? =
        flattenCategories().firstOrNull { it.category.id == categoryId }?.category

    fun findQuestion(questionId: String): QuestionDetail? =
        flattenCategories().firstNotNullOfOrNull { it.category.findQuestion(questionId) }

    fun allCategoryIds(): Set<String> =
        flattenCategories().mapTo(linkedSetOf()) { it.category.id }

    /** Rebuilds the tree without the given categories and their descendants. */
    fun removeCategories(categoryIds: Set<String>): PaperDetail = copy(
        categories = categories.mapNotNull { it.removeCategories(categoryIds) }
    )

    /** Renames/replaces one category while keeping its descendants attached. */
    fun updateCategory(
        categoryId: String,
        transform: (CategoryDetail) -> CategoryDetail
    ): PaperDetail = copy(
        categories = categories.map { it.updateCategory(categoryId, transform) }
    )

    /**
     * Full display names keyed by category id. Nested categories are shown as
     * "Parent / Child" so a practice test never loses the hierarchy.
     */
    fun categoryDisplayNames(): Map<String, String> = flattenCategories().associate {
        it.category.id to it.displayName
    }
}

data class CategoryNode(
    val category: CategoryDetail,
    val depth: Int,
    val path: List<String>,
    val idPath: List<String>
) {
    val displayName: String
        get() = path.joinToString(" / ")

    fun contains(node: CategoryNode): Boolean =
        node.idPath.take(idPath.size) == idPath
}

data class CategoryDetail(
    val id: String,
    val name: String,
    val questions: List<QuestionDetail>,
    val children: List<CategoryDetail> = emptyList()
) {
    val totalQuestionCount: Int
        get() = questions.size + children.sumOf { it.totalQuestionCount }

    fun allQuestions(): List<QuestionDetail> =
        questions + children.flatMap { it.allQuestions() }

    val totalCategoryCount: Int
        get() = 1 + children.sumOf { it.totalCategoryCount }

    fun subtreeCategoryIds(): Set<String> = buildSet {
        add(id)
        children.forEach { addAll(it.subtreeCategoryIds()) }
    }

    fun findQuestion(questionId: String): QuestionDetail? =
        questions.firstOrNull { it.id == questionId }
            ?: children.firstNotNullOfOrNull { it.findQuestion(questionId) }

    fun removeCategories(categoryIds: Set<String>): CategoryDetail? {
        if (id in categoryIds) return null
        return copy(children = children.mapNotNull { it.removeCategories(categoryIds) })
    }

    fun updateCategory(
        categoryId: String,
        transform: (CategoryDetail) -> CategoryDetail
    ): CategoryDetail {
        val updated = if (id == categoryId) transform(this) else this
        return updated.copy(
            children = updated.children.map { it.updateCategory(categoryId, transform) }
        )
    }

    fun updateQuestion(
        questionId: String,
        transform: (QuestionDetail) -> QuestionDetail
    ): CategoryDetail = copy(
        questions = questions.map { question ->
            if (question.id == questionId) transform(question) else question
        },
        children = children.map { it.updateQuestion(questionId, transform) }
    )
}

data class QuestionDetail(
    val id: String,
    val questionText: String,
    val answerText: String,
    val questionImages: List<com.paperpractice.domain.ImageContent> = emptyList(),
    val answerImages: List<com.paperpractice.domain.ImageContent> = emptyList()
)
