package com.paperpractice.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PaperHierarchyTest {

    private val grandchild = CategoryDetail(
        id = "grandchild",
        name = "Grandchild",
        questions = listOf(question("q3"))
    )
    private val child = CategoryDetail(
        id = "child",
        name = "Child",
        questions = listOf(question("q2")),
        children = listOf(grandchild)
    )
    private val parent = CategoryDetail(
        id = "parent",
        name = "Parent",
        questions = listOf(question("q1")),
        children = listOf(child)
    )
    private val sibling = CategoryDetail(
        id = "sibling",
        name = "Sibling",
        questions = emptyList()
    )
    private val paper = PaperDetail(
        id = "paper",
        name = "Paper",
        categories = listOf(parent, sibling)
    )

    @Test
    fun `all categories are visible when nothing is collapsed`() {
        assertEquals(
            listOf("parent", "child", "grandchild", "sibling"),
            paper.visibleCategories(emptySet()).map { it.category.id }
        )
    }

    @Test
    fun `collapsing a category hides all of its descendants`() {
        assertEquals(
            listOf("parent", "sibling"),
            paper.visibleCategories(setOf("parent")).map { it.category.id }
        )
    }

    @Test
    fun `collapsing a nested category hides only its descendants`() {
        assertEquals(
            listOf("parent", "child", "sibling"),
            paper.visibleCategories(setOf("child")).map { it.category.id }
        )
    }

    @Test
    fun `collapsing a leaf category leaves the list unchanged`() {
        assertEquals(
            listOf("parent", "child", "grandchild", "sibling"),
            paper.visibleCategories(setOf("sibling")).map { it.category.id }
        )
    }

    private val taggedGrandchild = CategoryDetail(
        id = "grandchild",
        name = "Grandchild",
        questions = listOf(taggedQuestion("q3", "Beta", "Deep"))
    )
    private val taggedChild = CategoryDetail(
        id = "child",
        name = "Child",
        questions = listOf(taggedQuestion("q2", "Alpha", "Beta")),
        children = listOf(taggedGrandchild)
    )
    private val taggedParent = CategoryDetail(
        id = "parent",
        name = "Parent",
        questions = listOf(taggedQuestion("q1", "Alpha")),
        children = listOf(taggedChild)
    )
    private val taggedSibling = CategoryDetail(
        id = "sibling",
        name = "Sibling",
        questions = listOf(taggedQuestion("q4", "Gamma"))
    )
    private val taggedPaper = PaperDetail(
        id = "tagged-paper",
        name = "Tagged paper",
        categories = listOf(taggedParent, taggedSibling)
    )

    @Test
    fun `subtree tags union tags across every depth`() {
        assertEquals(setOf("Alpha", "Beta", "Deep"), taggedParent.subtreeTags())
        assertEquals(setOf("Alpha", "Beta", "Deep"), taggedChild.subtreeTags())
        assertEquals(setOf("Beta", "Deep"), taggedGrandchild.subtreeTags())
        assertEquals(setOf("Gamma"), taggedSibling.subtreeTags())

        val untagged = CategoryDetail("empty", "Empty", listOf(question("plain")))
        assertEquals(emptySet<String>(), untagged.subtreeTags())
    }

    @Test
    fun `paper all tags unions every category subtree`() {
        assertEquals(setOf("Alpha", "Beta", "Deep", "Gamma"), taggedPaper.allTags())
    }

    @Test
    fun `removing categories preserves tags of the remaining questions`() {
        val remaining = taggedPaper.removeCategories(setOf("child"))

        assertEquals(setOf("Alpha", "Gamma"), remaining.allTags())
        assertEquals(
            listOf("parent", "sibling"),
            remaining.flattenCategories().map { it.category.id }
        )
    }

    @Test
    fun `updating a category preserves all subtree tags`() {
        val updated = taggedPaper.updateCategory("child") { it.copy(name = "Renamed child") }

        assertEquals("Renamed child", updated.findCategory("child")?.name)
        assertEquals(setOf("Alpha", "Beta", "Deep", "Gamma"), updated.allTags())
    }

    @Test
    fun `updating a question preserves its tags`() {
        val updated = taggedParent.updateQuestion("q2") { it.copy(answerText = "Updated answer") }

        assertEquals("Updated answer", updated.findQuestion("q2")?.answerText)
        assertEquals(listOf("Alpha", "Beta"), updated.findQuestion("q2")?.tags)
        assertEquals(setOf("Alpha", "Beta", "Deep"), updated.subtreeTags())
    }

    private fun question(id: String) = QuestionDetail(
        id = id,
        questionText = "Question $id",
        answerText = "Answer $id"
    )

    private fun taggedQuestion(id: String, vararg tags: String) = QuestionDetail(
        id = id,
        questionText = "Question $id",
        answerText = "Answer $id",
        tags = tags.toList()
    )
}
