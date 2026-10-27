package com.paperpractice.ui

import android.graphics.Typeface
import android.text.Layout
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.paperpractice.domain.ColumnAlignment
import com.paperpractice.domain.RichBlock
import com.paperpractice.domain.RichInline
import com.paperpractice.domain.RichText
import com.paperpractice.render.RichTextSpans

/**
 * Renders the rich text subset used by question and answer text.
 *
 * Paragraphs are hosted in a TextView so Android spans can do inline fractions
 * and the familiar text-selection behaviour. Tables stay native Compose layout
 * so they get real borders and do not scroll horizontally off small screens.
 */
@Composable
fun RichTextContent(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
    selectable: Boolean = true
) {
    val document = remember(text) { RichText.parse(text) }
    if (document.blocks.isEmpty()) return

    val density = LocalDensity.current
    val resolvedColor = if (color == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurface
    } else {
        color
    }
    val highlightColor = MaterialTheme.colorScheme.tertiaryContainer
    val requestedSize = if (style.fontSize.isSpecified) style.fontSize else 16.sp
    val textSizePx = with(density) { requestedSize.toPx() }
    val lineHeightPx = if (style.lineHeight.isSpecified) {
        with(density) { style.lineHeight.toPx() }
    } else {
        textSizePx * 1.25f
    }
    val typeface = remember(style.fontWeight, style.fontStyle) {
        val weight = (style.fontWeight ?: FontWeight.Normal).weight
        val italic = style.fontStyle == FontStyle.Italic
        val typefaceStyle = when {
            weight >= 600 && italic -> Typeface.BOLD_ITALIC
            weight >= 600 -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        Typeface.create("sans-serif", typefaceStyle)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        document.blocks.forEach { block ->
            when (block) {
                is RichBlock.Paragraph -> RichAndroidParagraph(
                    inlines = block.inlines,
                    textSizePx = textSizePx,
                    lineHeightPx = lineHeightPx,
                    color = resolvedColor,
                    highlightColor = highlightColor,
                    typeface = typeface,
                    selectable = selectable
                )

                is RichBlock.Table -> RichTable(
                    table = block,
                    textSizePx = textSizePx * TABLE_TEXT_SCALE,
                    lineHeightPx = lineHeightPx * TABLE_TEXT_SCALE,
                    color = resolvedColor,
                    highlightColor = highlightColor,
                    typeface = typeface
                )
            }
        }
    }
}

@Composable
private fun RichAndroidParagraph(
    inlines: List<RichInline>,
    textSizePx: Float,
    lineHeightPx: Float,
    color: Color,
    highlightColor: Color,
    typeface: Typeface,
    selectable: Boolean,
    modifier: Modifier = Modifier,
    forceBold: Boolean = false,
    alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
) {
    val density = LocalDensity.current
    val colorArgb = color.toArgb()
    val highlightArgb = highlightColor.toArgb()
    val spanned = remember(inlines, forceBold, colorArgb, highlightArgb) {
        RichTextSpans.build(
            inlines = inlines,
            color = colorArgb,
            highlightColor = highlightArgb,
            forceBold = forceBold
        )
    }
    val lineMultiplier = if (spanned.hasFractions) {
        maxOf(lineHeightPx / textSizePx, 1.58f)
    } else {
        maxOf(lineHeightPx / textSizePx, 1.12f)
    }

    AndroidView(
        // AndroidView normally measures its child with the incoming height
        // constraint. Inside a scrollable list that can be smaller than the
        // TextView's content, clipping the tail of long answers until the user
        // touches the card. Let the TextView measure its full height instead.
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(unbounded = true),
        factory = { context ->
            TextView(context).apply {
                setPadding(0, 0, 0, 0)
                includeFontPadding = false
                background = null
                setTextIsSelectable(selectable)
                setLineSpacing(0f, lineMultiplier)
            }
        },
        update = { view ->
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            view.typeface = typeface
            view.setTextColor(colorArgb)
            view.textAlignment = when (alignment) {
                Layout.Alignment.ALIGN_CENTER -> android.view.View.TEXT_ALIGNMENT_CENTER
                Layout.Alignment.ALIGN_OPPOSITE -> android.view.View.TEXT_ALIGNMENT_VIEW_END
                else -> android.view.View.TEXT_ALIGNMENT_VIEW_START
            }
            view.setLineSpacing(0f, lineMultiplier)
            view.setTextIsSelectable(selectable)
            view.text = spanned.text
            view.requestLayout()
        }
    )
}

@Composable
private fun RichTable(
    table: RichBlock.Table,
    textSizePx: Float,
    lineHeightPx: Float,
    color: Color,
    highlightColor: Color,
    typeface: Typeface
) {
    val border = MaterialTheme.colorScheme.outlineVariant
    val headerBackground = MaterialTheme.colorScheme.surfaceVariant
    val rowBackground = MaterialTheme.colorScheme.surface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, border)
    ) {
        RichTableRow(
            cells = table.headers,
            alignments = table.alignments,
            textSizePx = textSizePx,
            lineHeightPx = lineHeightPx,
            color = color,
            highlightColor = highlightColor,
            typeface = typeface,
            background = headerBackground,
            border = border,
            forceBold = true
        )
        table.rows.forEachIndexed { index, row ->
            HorizontalDivider(color = border, thickness = 1.dp)
            RichTableRow(
                cells = row,
                alignments = table.alignments,
                textSizePx = textSizePx,
                lineHeightPx = lineHeightPx,
                color = color,
                highlightColor = highlightColor,
                typeface = typeface,
                background = if (index % 2 == 1) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                } else {
                    rowBackground
                },
                border = border,
                forceBold = false
            )
        }
    }
}

@Composable
private fun RichTableRow(
    cells: List<List<RichInline>>,
    alignments: List<ColumnAlignment>,
    textSizePx: Float,
    lineHeightPx: Float,
    color: Color,
    highlightColor: Color,
    typeface: Typeface,
    background: Color,
    border: Color,
    forceBold: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .drawBehind {
                if (cells.size > 1) {
                    val stroke = 1.dp.toPx()
                    for (index in 1 until cells.size) {
                        val x = size.width * index / cells.size
                        drawLine(
                            color = border,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = stroke
                        )
                    }
                }
            }
    ) {
        cells.forEachIndexed { index, cell ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 7.dp)
            ) {
                if (cell.isEmpty()) {
                    Text("", style = MaterialTheme.typography.bodySmall)
                } else {
                    RichAndroidParagraph(
                        inlines = cell,
                        textSizePx = textSizePx,
                        lineHeightPx = lineHeightPx,
                        color = color,
                        highlightColor = highlightColor,
                        typeface = typeface,
                        selectable = false,
                        forceBold = forceBold,
                        alignment = when (alignments.getOrNull(index)) {
                            ColumnAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
                            ColumnAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                            else -> Layout.Alignment.ALIGN_NORMAL
                        }
                    )
                }
            }
        }
    }
}

private const val TABLE_TEXT_SCALE = 0.92f
