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

    private fun question(id: String) = QuestionDetail(
        id = id,
        questionText = "Question $id",
        answerText = "Answer $id"
    )
}
