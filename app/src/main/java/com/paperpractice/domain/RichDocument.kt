package com.paperpractice.domain

/**
 * A small, renderer-neutral document model for the lightweight rich text
 * understood by Paper Practice.
 *
 * The original markup stays in the question/answer text so JSON exports remain
 * lossless. Parsing only happens for display and rendered exports.
 */
data class RichDocument(
    val blocks: List<RichBlock>
)

sealed interface RichBlock {
    data class Paragraph(val inlines: List<RichInline>) : RichBlock

    data class Table(
        val headers: List<List<RichInline>>,
        val rows: List<List<List<RichInline>>>,
        val alignments: List<ColumnAlignment>
    ) : RichBlock
}

enum class ColumnAlignment {
    LEFT,
    CENTER,
    RIGHT
}

sealed interface RichInline {
    data class Text(
        val text: String,
        val style: RichInlineStyle = RichInlineStyle()
    ) : RichInline

    data class Fraction(
        val numerator: List<RichInline>,
        val denominator: List<RichInline>,
        val style: RichInlineStyle = RichInlineStyle()
    ) : RichInline
}

data class RichInlineStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val highlight: Boolean = false
) {
    fun toggle(marker: String): RichInlineStyle = when (marker) {
        "**" -> copy(bold = !bold)
        "*" -> copy(italic = !italic)
        "++" -> copy(underline = !underline)
        "~~" -> copy(strikethrough = !strikethrough)
        "==" -> copy(highlight = !highlight)
        else -> this
    }
}
