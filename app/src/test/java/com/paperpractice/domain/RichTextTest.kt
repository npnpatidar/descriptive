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
        assertEquals("A ⇌ B", RichText.format("A <=> B"))
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
}
