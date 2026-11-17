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
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

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
                        // Keep the RichInline children so the fraction renderer
                        // can lay out nested fractions and styles recursively.
                        spans += SpanRange.FractionSpan(
                            start = start,
                            end = end,
                            style = style,
                            numerator = node.numerator,
                            denominator = node.denominator
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
            val numerator: List<RichInline>,
            val denominator: List<RichInline>
        ) : SpanRange
    }

    /**
     * Draws a stacked fraction from its child [RichInline] lists instead of two
     * flattened Strings. Each child is measured and drawn with its own style,
     * and nested [RichInline.Fraction] nodes recurse through the same renderer.
     */
    private class FractionSpan(
        private val numerator: List<RichInline>,
        private val denominator: List<RichInline>,
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
                val width = fractionWidth(paint, originalSize, originalTypeface, style.bold)
                fm?.let { metrics ->
                    // The fraction is drawn above and below the normal text
                    // baseline. Tell TextView about that extra extent so its
                    // measured line height includes the whole formula.
                    val extent = ceil(originalSize * 0.85f).toInt()
                    metrics.ascent = min(metrics.ascent, -extent)
                    metrics.top = min(metrics.top, -extent)
                    metrics.descent = max(metrics.descent, extent)
                    metrics.bottom = max(metrics.bottom, extent)
                }
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
                val barY = (top + bottom) / 2f
                val width = fractionWidth(paint, originalSize, originalTypeface, style.bold)
                if (style.highlight) {
                    paint.color = highlightColor
                    canvas.drawRect(x, top.toFloat(), x + width, bottom.toFloat(), paint)
                }
                paint.color = textColor
                drawFraction(
                    paint = paint,
                    canvas = canvas,
                    numerator = numerator,
                    denominator = denominator,
                    textSize = originalSize,
                    baseTypeface = originalTypeface,
                    x = x,
                    barY = barY,
                    inheritedBold = style.bold
                )
            } finally {
                paint.textSize = originalSize
                paint.typeface = originalTypeface
                paint.color = originalColor
            }
        }

        private fun fractionWidth(
            paint: Paint,
            textSize: Float,
            baseTypeface: Typeface?,
            inheritedBold: Boolean
        ): Float {
            val childSize = textSize * FRACTION_SCALE
            return max(
                measureInlines(paint, numerator, childSize, baseTypeface, inheritedBold),
                measureInlines(paint, denominator, childSize, baseTypeface, inheritedBold)
            ) + textSize * 0.45f
        }

        private fun measureInlines(
            paint: Paint,
            nodes: List<RichInline>,
            textSize: Float,
            baseTypeface: Typeface?,
            inheritedBold: Boolean
        ): Float {
            var width = 0f
            nodes.forEach { node ->
                when (node) {
                    is RichInline.Text -> {
                        if (node.text.isEmpty()) return@forEach
                        preparePaint(paint, textSize, baseTypeface, node.style, inheritedBold)
                        width += paint.measureText(node.text)
                    }
                    is RichInline.Fraction -> {
                        val childSize = textSize * FRACTION_SCALE
                        val bold = inheritedBold || node.style.bold
                        width += max(
                            measureInlines(paint, node.numerator, childSize, baseTypeface, bold),
                            measureInlines(paint, node.denominator, childSize, baseTypeface, bold)
                        ) + textSize * 0.45f
                    }
                }
            }
            return width
        }

        private fun drawInlines(
            paint: Paint,
            canvas: Canvas,
            nodes: List<RichInline>,
            textSize: Float,
            baseTypeface: Typeface?,
            x: Float,
            baseline: Float,
            inheritedBold: Boolean
        ): Float {
            var cursor = x
            nodes.forEach { node ->
                when (node) {
                    is RichInline.Text -> {
                        if (node.text.isEmpty()) return@forEach
                        preparePaint(paint, textSize, baseTypeface, node.style, inheritedBold)
                        val nodeWidth = paint.measureText(node.text)
                        if (node.style.highlight) {
                            paint.color = highlightColor
                            canvas.drawRect(
                                cursor,
                                baseline - textSize * 0.80f,
                                cursor + nodeWidth,
                                baseline + textSize * 0.20f,
                                paint
                            )
                            paint.color = textColor
                        }
                        canvas.drawText(node.text, cursor, baseline, paint)
                        if (node.style.underline) {
                            canvas.drawLine(
                                cursor, baseline + textSize * 0.10f,
                                cursor + nodeWidth, baseline + textSize * 0.10f, paint
                            )
                        }
                        if (node.style.strikethrough) {
                            canvas.drawLine(
                                cursor, baseline - textSize * 0.26f,
                                cursor + nodeWidth, baseline - textSize * 0.26f, paint
                            )
                        }
                        cursor += nodeWidth
                    }
                    is RichInline.Fraction -> {
                        val childSize = textSize * FRACTION_SCALE
                        val bold = inheritedBold || node.style.bold
                        val numWidth = measureInlines(
                            paint, node.numerator, childSize, baseTypeface, bold
                        )
                        val denWidth = measureInlines(
                            paint, node.denominator, childSize, baseTypeface, bold
                        )
                        val width = max(numWidth, denWidth) + textSize * 0.45f
                        val nestedX = cursor + (width - numWidth) / 2f
                        val nestedBarY = baseline - textSize * 0.18f
                        if (node.style.highlight) {
                            paint.color = highlightColor
                            canvas.drawRect(
                                cursor, nestedBarY - childSize,
                                cursor + width, nestedBarY + childSize, paint
                            )
                        }
                        paint.color = textColor
                        drawFraction(
                            paint = paint,
                            canvas = canvas,
                            numerator = node.numerator,
                            denominator = node.denominator,
                            textSize = textSize,
                            baseTypeface = baseTypeface,
                            x = nestedX,
                            barY = nestedBarY,
                            inheritedBold = bold
                        )
                        cursor += width
                    }
                }
            }
            return cursor
        }

        private fun drawFraction(
            paint: Paint,
            canvas: Canvas,
            numerator: List<RichInline>,
            denominator: List<RichInline>,
            textSize: Float,
            baseTypeface: Typeface?,
            x: Float,
            barY: Float,
            inheritedBold: Boolean
        ) {
            val childSize = textSize * FRACTION_SCALE
            val numWidth = measureInlines(paint, numerator, childSize, baseTypeface, inheritedBold)
            val denWidth = measureInlines(paint, denominator, childSize, baseTypeface, inheritedBold)
            val width = max(numWidth, denWidth) + textSize * 0.45f

            drawInlines(
                paint, canvas, numerator, childSize, baseTypeface,
                x + (width - numWidth) / 2f, barY - textSize * 0.16f, inheritedBold
            )
            paint.color = textColor
            canvas.drawLine(
                x + textSize * 0.12f, barY,
                x + width - textSize * 0.12f, barY, paint
            )
            drawInlines(
                paint, canvas, denominator, childSize, baseTypeface,
                x + (width - denWidth) / 2f, barY + textSize * 0.70f, inheritedBold
            )
        }

        private fun preparePaint(
            paint: Paint,
            textSize: Float,
            baseTypeface: Typeface?,
            style: RichInlineStyle,
            inheritedBold: Boolean
        ) {
            paint.textSize = textSize
            paint.color = textColor
            paint.typeface = typefaceFor(baseTypeface, style, inheritedBold)
        }

        private fun typefaceFor(
            base: Typeface?,
            style: RichInlineStyle,
            inheritedBold: Boolean
        ): Typeface {
            val bold = style.bold || inheritedBold
            val typefaceStyle = when {
                bold && style.italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                style.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            return Typeface.create(base ?: Typeface.DEFAULT, typefaceStyle)
        }
    }

    private const val FRACTION_SCALE = 0.72f
}
