package com.paperpractice.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ReplacementSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import com.paperpractice.domain.RichInline
import com.paperpractice.domain.RichInlineStyle
import com.paperpractice.domain.RichText
import kotlin.math.ceil
import kotlin.math.max

data class SpannedRichText(
    val text: SpannableString,
    val hasFractions: Boolean
)

/**
 * Converts the renderer-neutral rich text model into Android spans. This is
 * shared by the Compose TextView bridge and the PDF exporter so both use the
 * same bold/italic/underline/strike/highlight and stacked-fraction rendering.
 */
object RichTextSpans {

    fun build(
        inlines: List<RichInline>,
        color: Int,
        highlightColor: Int,
        forceBold: Boolean = false
    ): SpannedRichText {
        val text = StringBuilder()
        val spans = mutableListOf<SpanRange>()
        var hasFractions = false

        fun append(nodes: List<RichInline>, inheritedBold: Boolean = false) {
            nodes.forEach { node ->
                when (node) {
                    is RichInline.Text -> {
                        if (node.text.isEmpty()) return@forEach
                        val start = text.length
                        text.append(node.text)
                        val end = text.length
                        val style = if (forceBold || inheritedBold) {
                            node.style.copy(bold = true)
                        } else {
                            node.style
                        }
                        spans += SpanRange.TextSpan(start, end, style)
                    }

                    is RichInline.Fraction -> {
                        val start = text.length
                        text.append('\uFFFC')
                        val end = text.length
                        val style = if (forceBold || inheritedBold) {
                            node.style.copy(bold = true)
                        } else {
                            node.style
                        }
                        spans += SpanRange.FractionSpan(
                            start = start,
                            end = end,
                            style = style,
                            numerator = RichText.plain(node.numerator),
                            denominator = RichText.plain(node.denominator)
                        )
                        hasFractions = true
                    }
                }
            }
        }

        append(inlines)
        val spannable = SpannableString(text.toString())
        spans.forEach { range ->
            when (range) {
                is SpanRange.TextSpan -> {
                    applyTextStyle(spannable, range, highlightColor)
                }

                is SpanRange.FractionSpan -> {
                    spannable.setSpan(
                        FractionSpan(
                            numerator = range.numerator,
                            denominator = range.denominator,
                            style = range.style,
                            textColor = color,
                            highlightColor = highlightColor
                        ),
                        range.start,
                        range.end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }
        return SpannedRichText(spannable, hasFractions)
    }

    private fun applyTextStyle(
        text: SpannableString,
        range: SpanRange.TextSpan,
        highlightColor: Int
    ) {
        val style = range.style
        val typefaceStyle = when {
            style.bold && style.italic -> Typeface.BOLD_ITALIC
            style.bold -> Typeface.BOLD
            style.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        if (typefaceStyle != Typeface.NORMAL) {
            text.setSpan(
                StyleSpan(typefaceStyle),
                range.start,
                range.end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        if (style.underline) {
            text.setSpan(
                UnderlineSpan(),
                range.start,
                range.end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        if (style.strikethrough) {
            text.setSpan(
                StrikethroughSpan(),
                range.start,
                range.end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        if (style.highlight) {
            text.setSpan(
                BackgroundColorSpan(highlightColor),
                range.start,
                range.end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private sealed interface SpanRange {
        val start: Int
        val end: Int

        data class TextSpan(
            override val start: Int,
            override val end: Int,
            val style: RichInlineStyle
        ) : SpanRange

        data class FractionSpan(
            override val start: Int,
            override val end: Int,
            val style: RichInlineStyle,
            val numerator: String,
            val denominator: String
        ) : SpanRange
    }

    private class FractionSpan(
        private val numerator: String,
        private val denominator: String,
        private val style: RichInlineStyle,
        private val textColor: Int,
        private val highlightColor: Int
    ) : ReplacementSpan() {

        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            val originalSize = paint.textSize
            val originalTypeface = paint.typeface
            val originalColor = paint.color
            try {
                paint.textSize = originalSize * FRACTION_SCALE
                paint.typeface = typefaceFor(originalTypeface, style)
                val width = max(
                    paint.measureText(numerator),
                    paint.measureText(denominator)
                ) + originalSize * 0.45f
                return ceil(width).toInt()
            } finally {
                paint.textSize = originalSize
                paint.typeface = originalTypeface
                paint.color = originalColor
            }
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            baseline: Int,
            bottom: Int,
            paint: Paint
        ) {
            val originalSize = paint.textSize
            val originalTypeface = paint.typeface
            val originalColor = paint.color
            try {
                paint.textSize = originalSize * FRACTION_SCALE
                paint.typeface = typefaceFor(originalTypeface, style)
                val width = max(
                    paint.measureText(numerator),
                    paint.measureText(denominator)
                ) + originalSize * 0.45f
                val barY = (top + bottom) / 2f
                if (style.highlight) {
                    paint.color = highlightColor
                    canvas.drawRect(x, top.toFloat(), x + width, bottom.toFloat(), paint)
                }
                paint.color = textColor
                val numeratorWidth = paint.measureText(numerator)
                val denominatorWidth = paint.measureText(denominator)
                canvas.drawText(
                    numerator,
                    x + (width - numeratorWidth) / 2f,
                    barY - originalSize * 0.16f,
                    paint
                )
                canvas.drawLine(
                    x + originalSize * 0.12f,
                    barY,
                    x + width - originalSize * 0.12f,
                    barY,
                    paint
                )
                canvas.drawText(
                    denominator,
                    x + (width - denominatorWidth) / 2f,
                    barY + originalSize * 0.70f,
                    paint
                )
            } finally {
                paint.textSize = originalSize
                paint.typeface = originalTypeface
                paint.color = originalColor
            }
        }

        private fun typefaceFor(base: Typeface?, style: RichInlineStyle): Typeface {
            val typefaceStyle = when {
                style.bold && style.italic -> Typeface.BOLD_ITALIC
                style.bold -> Typeface.BOLD
                style.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            return Typeface.create(base ?: Typeface.DEFAULT, typefaceStyle)
        }
    }

    private const val FRACTION_SCALE = 0.72f
}
