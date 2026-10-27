package com.paperpractice.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextTest {

    @Test
    fun `formats chemical subscripts and reaction arrows`() {
        assertEquals(
            "2H₂ + O₂ → 2H₂O",
            RichText.format("2H_2 + O_2 -> 2H_2O")
        )
        assertEquals(
            "C₆H₁₂O₆",
            RichText.format("C_6H_12O_6")
        )
        assertEquals("CO₂ (gas)", RichText.format("CO_2 (gas)"))
        assertEquals("A ⇌ B", RichText.format("A <=> B"))
    }

    @Test
    fun `formats ionic charges`() {
        assertEquals("H⁺", RichText.format("H^+"))
        assertEquals("Cl⁻", RichText.format("Cl^-"))
        assertEquals("H₃O⁺", RichText.format("H_3O^+"))
        assertEquals("Fe³⁺", RichText.format("Fe^3+"))
        assertEquals("10⁻⁶ m", RichText.format("10^-6 m"))
        assertEquals("x²+3", RichText.format("x^2+3"))
    }

    @Test
    fun `formats superscripts and braces`() {
        assertEquals("x²", RichText.format("x^2"))
        assertEquals("xⁿ⁺¹", RichText.format("x^{n+1}"))
        assertEquals("(a + b)²", RichText.format("(a + b)^2"))
    }

    @Test
    fun `formats fractions square roots and greek commands`() {
        assertEquals("(a)/(b)", RichText.format("\\frac{a}{b}"))
        assertEquals("√(x + 1)", RichText.format("\\sqrt{x + 1}"))
        assertEquals(
            "Δ x ≤ α · β",
            RichText.format("\\Delta x \\le \\alpha \\cdot \\beta")
        )
    }

    @Test
    fun `formats long command names without prefix corruption`() {
        assertEquals("a ≤ b ≥ c", RichText.format("a \\leq b \\geq c"))
        assertEquals("A → B", RichText.format("A \\rightarrow B"))
        assertEquals("A ↔ B", RichText.format("A \\leftrightarrow B"))
        assertEquals("A ⇌ B", RichText.format("A \\rightleftharpoons B"))
        assertEquals("(x + 1)", RichText.format("\\left(x + 1\\right)"))
    }

    @Test
    fun `leaves ordinary prose untouched`() {
        val text = "Explain why the result changes at 25 C."
        assertEquals(text, RichText.format(text))
        assertTrue(RichText.format("").isEmpty())
    }

    @Test
    fun `parses all inline style markers`() {
        val document = RichText.parse(
            "A **bold** *italic* ++under++ ~~strike~~ ==mark== Z"
        )
        val paragraph = document.blocks.single() as RichBlock.Paragraph
        val runs = paragraph.inlines.filterIsInstance<RichInline.Text>()

        fun styleOf(word: String): RichInlineStyle = runs.first { it.text == word }.style

        assertEquals(RichInlineStyle(bold = true), styleOf("bold"))
        assertEquals(RichInlineStyle(italic = true), styleOf("italic"))
        assertEquals(RichInlineStyle(underline = true), styleOf("under"))
        assertEquals(RichInlineStyle(strikethrough = true), styleOf("strike"))
        assertEquals(RichInlineStyle(highlight = true), styleOf("mark"))
        assertEquals(RichInlineStyle(), styleOf(" Z"))
    }

    @Test
    fun `combines nested inline styles`() {
        val document = RichText.parse("**bold *and italic***")
        val runs = (document.blocks.single() as RichBlock.Paragraph)
            .inlines
            .filterIsInstance<RichInline.Text>()

        assertEquals("bold ", runs[0].text)
        assertEquals(RichInlineStyle(bold = true), runs[0].style)
        assertEquals("and italic", runs[1].text)
        assertEquals(
            RichInlineStyle(bold = true, italic = true),
            runs[1].style
        )
    }

    @Test
    fun `parses markdown pipe tables with alignment and styled cells`() {
        val document = RichText.parse(
            """
            Introduction

            | Name | Score |
            | :--- | ---: |
            | **Ada** | 10 |
            | Bob | 5 |
            """.trimIndent()
        )

        assertEquals(2, document.blocks.size)
        assertEquals("Introduction", RichText.plain(document.blocks[0]))
        val table = document.blocks[1] as RichBlock.Table
        assertEquals(2, table.headers.size)
        assertEquals(ColumnAlignment.LEFT, table.alignments[0])
        assertEquals(ColumnAlignment.RIGHT, table.alignments[1])
        assertEquals(2, table.rows.size)

        val name = table.rows[0][0].single() as RichInline.Text
        assertEquals("Ada", name.text)
        assertTrue(name.style.bold)
        assertEquals("Name | Score\nAda | 10\nBob | 5", RichText.plain(table))
    }

    @Test
    fun `parses nested fractions and radicals`() {
        val source = "\\frac{1 + \\frac{1}{2}}{3}"
        val document = RichText.parse(source)
        val outer = (document.blocks.single() as RichBlock.Paragraph)
            .inlines
            .filterIsInstance<RichInline.Fraction>()
            .single()

        assertTrue(outer.numerator.any { it is RichInline.Fraction })
        assertEquals("(1 + (1)/(2))/(3)", RichText.format(source))

        val quadratic = RichText.parse(
            "\\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}"
        )
        val formula = (quadratic.blocks.single() as RichBlock.Paragraph)
            .inlines
            .filterIsInstance<RichInline.Fraction>()
            .single()
        assertTrue(
            formula.numerator
                .filterIsInstance<RichInline.Text>()
                .any { it.text.contains("√(") }
        )
    }
}
