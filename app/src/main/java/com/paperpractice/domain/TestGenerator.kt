package com.paperpractice.domain

import com.paperpractice.data.PaperDetail
import java.text.Normalizer
import kotlin.random.Random

data class TestSession(
    val paperId: String,
    val paperName: String,
    val createdAt: Long,
    val questions: List<SessionQuestion>
) {
    val categoryCount: Int
        get() = questions.map { it.categoryId }.distinct().size
}

data class SessionQuestion(
    val number: Int,
    val questionId: String,
    val categoryId: String,
    val categoryName: String,
    val questionText: String,
    val answerText: String,
    val categoryPath: List<String> = emptyList(),
    val questionImages: List<ImageContent> = emptyList(),
    val answerImages: List<ImageContent> = emptyList(),
    val tags: List<String> = emptyList()
)

/**
 * Builds a test by sampling without replacement inside each category. Sampling
 * per category matters: it guarantees the requested amount from every selected
 * category instead of letting one large category dominate a global shuffle.
 */
object TestGenerator {

    private fun normalizeTag(value: String): String =
        Normalizer.normalize(value.trim(), Normalizer.Form.NFC).lowercase()

    /**
     * Returns true when [tags] satisfies the include-only [normalizedFilter]:
     * an empty filter accepts everything, otherwise at least one tag must match
     * one of the selected display names (OR), compared case-insensitively using
     * a trim + NFC + lowercase normalization. Original tag spelling is left untouched
     * so callers can preserve it in their output.
     */
    private fun matchesTagFilter(tags: List<String>, normalizedFilter: Set<String>): Boolean {
        if (normalizedFilter.isEmpty()) return true
        return tags.any { normalizeTag(it) in normalizedFilter }
    }

    /**
     * Builds a test by sampling without replacement inside each category.
     *
     * [tagFilter] is an optional include-only filter of tag display names (not
     * ids): a question is eligible when it carries at least one of the selected
     * names (OR), compared case-insensitively via trim + NFC + lowercase. An empty
     * filter keeps today's behaviour. The same predicate is applied to both the
     * availability count used for validation and the pool that is shuffled and
     * sampled from, so the two always agree.
     */
    fun generate(
        paper: PaperDetail,
        selectedCategoryIds: Set<String>,
        requestedCounts: Map<String, Int>,
        random: Random = Random.Default,
        tagFilter: Set<String> = emptySet()
    ): TestSession {
        require(selectedCategoryIds.isNotEmpty()) { "Select at least one category." }

        val allCategories = paper.flattenCategories()
        val categoriesById = allCategories.associateBy { it.category.id }
        val selectedCategories = selectedCategoryIds.mapNotNull { categoriesById[it] }
        require(selectedCategories.isNotEmpty()) { "Select at least one category." }

        // The UI never allows a category and one of its ancestors/descendants
        // to be selected together. Enforce the same rule here so an overlapping
        // set cannot double-count questions: keep the ancestor, drop the
        // descendant (and with it, its requested count).
        val effectiveCategories = selectedCategories.filter { node ->
            selectedCategories.none { other ->
                other.category.id != node.category.id && other.contains(node)
            }
        }

        val selectedQuestions = mutableListOf<SessionQuestion>()
        val usedQuestionIds = mutableSetOf<String>()
        // Ids actually drawn by earlier, non-overlapping selected subtrees. A
        // corrupt hand-built paper can give distinct questions in sibling
        // subtrees the same id; such an id is not available a second time.
        val committedIds = mutableSetOf<String>()
        val normalizedFilter = tagFilter.map { normalizeTag(it) }.toSet()

        effectiveCategories.forEach { node ->
            // MERGE imports can duplicate question ids, so count distinct ids
            // rather than rows, minus ids an earlier category already took.
            val subtreeIds = allCategories
                .filter { node.contains(it) }
                .flatMap { it.category.questions }
                .filter { matchesTagFilter(it.tags, normalizedFilter) }
                .map { it.id }
                .toSet()
            val available = (subtreeIds - committedIds).size
            val requested = requestedCounts[node.category.id] ?: 0
            require(requested >= 0) {
                "The question count for \"${node.displayName}\" cannot be negative."
            }
            require(requested <= available) {
                "The question count for \"${node.displayName}\" exceeds the " +
                    "$available available questions."
            }
            if (requested == 0) return@forEach

            val pool = allCategories
                .filter { node.contains(it) }
                .flatMap { owner ->
                    owner.category.questions.map { question -> owner to question }
                }
                .filter { (_, question) -> matchesTagFilter(question.tags, normalizedFilter) }

            val firstDrawn = selectedQuestions.size
            pool
                .shuffled(random)
                .filter { (_, question) -> usedQuestionIds.add(question.id) }
                .take(requested)
                .forEach { (owner, question) ->
                    selectedQuestions += SessionQuestion(
                        number = selectedQuestions.size + 1,
                        questionId = question.id,
                        categoryId = owner.category.id,
                        categoryName = owner.displayName,
                        questionText = question.questionText,
                        answerText = question.answerText,
                        categoryPath = owner.path,
                        questionImages = question.questionImages,
                        answerImages = question.answerImages,
                        tags = question.tags
                    )
                }
            committedIds += selectedQuestions
                .subList(firstDrawn, selectedQuestions.size)
                .map { it.questionId }
        }

        require(selectedQuestions.isNotEmpty()) {
            "Choose at least one question to generate a test."
        }

        return TestSession(
            paperId = paper.id,
            paperName = paper.name,
            createdAt = System.currentTimeMillis(),
            questions = selectedQuestions
        )
    }
}
