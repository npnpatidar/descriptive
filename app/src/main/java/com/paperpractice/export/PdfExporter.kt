package com.paperpractice.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.StyleSpan
import com.paperpractice.domain.CategoryContent
import com.paperpractice.domain.ColumnAlignment
import com.paperpractice.domain.ImageContent
import com.paperpractice.domain.PaperContent
import com.paperpractice.domain.RichBlock
import com.paperpractice.domain.RichInline
import com.paperpractice.domain.RichText
import com.paperpractice.render.RichTextSpans
import java.io.OutputStream
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a paginated PDF with the platform's PdfDocument. Using Android's text
 * rendering means imported Unicode text is honoured by the device's fonts, and
 * the manual line packer can continue a single long answer across pages
 * without ever dropping content.
 */
object PdfExporter {

    /** Non-suspending entry point kept for plain callers. */
    fun write(
        content: PaperContent,
        includeAnswers: Boolean,
        answersAtEnd: Boolean,
        output: OutputStream
    ) {
        writeImpl(content, includeAnswers, answersAtEnd, output, cancellationContext = null)
    }

    /**
     * Cancellation-aware entry point used by the UI. [currentCoroutineContext]
     * is resolved here (a suspend call) and passed into the renderer so its long
     * loops can call [CoroutineContext.ensureActive] promptly.
     */
    suspend fun writeCancellable(
        content: PaperContent,
        includeAnswers: Boolean,
        answersAtEnd: Boolean,
        output: OutputStream
    ) {
        writeImpl(content, includeAnswers, answersAtEnd, output, currentCoroutineContext())
    }

    private fun writeImpl(
        content: PaperContent,
        includeAnswers: Boolean,
        answersAtEnd: Boolean,
        output: OutputStream,
        cancellationContext: CoroutineContext?
    ) {
        val checkActive: () -> Unit = { cancellationContext?.ensureActive() }
        val document = PdfDocument()
        // Bitmaps decoded while drawing pages are kept alive until the PDF has
        // been fully written: SkPDF may retain a reference to their pixels on
        // some API levels, so recycling them at draw time is unsafe.
        val decodedBitmaps = mutableListOf<Bitmap>()
        // Nullable alias used only by the cleanup in finally, so a still-open
        // page can be finished before document.close().
        var cleanupWriter: PageWriter? = null
        try {
            // Non-null val for every drawing call. A local function
            // (appendCategory) captures it, and a captured mutable variable is
            // not reliably smart-cast by Kotlin; a val needs no smart cast.
            val pageWriter = PageWriter(document, content.paperName, decodedBitmaps, checkActive)
            cleanupWriter = pageWriter
            pageWriter.drawTitle(content)
            if (content.categories.isEmpty()) {
                pageWriter.drawBody("This paper contains no categories.", spacingAfter = 12f)
            }
            var number = 1
            val answers = mutableListOf<AnswerEntry>()

            fun appendCategory(
                category: CategoryContent,
                parentPath: List<String>
            ) {
                checkActive()
                val path = parentPath + category.categoryName
                if (category.questions.isNotEmpty()) {
                    pageWriter.drawCategory(categoryExportName(parentPath, category.categoryName))
                    category.questions.forEach { question ->
                        checkActive()
                        pageWriter.drawQuestion(
                            number,
                            RichText.parse(question.questionText).blocks,
                            includeAnswers && !answersAtEnd
                        )
                        pageWriter.drawImages(question.questionImages)
                        if (includeAnswers) {
                            val answer = RichText.parse(
                                question.answerText.ifBlank { "(No answer provided)" }
                            ).blocks
                            if (answersAtEnd) {
                                answers += AnswerEntry(
                                    number = number,
                                    blocks = answer,
                                    images = question.answerImages
                                )
                            } else {
                                pageWriter.drawAnswer(answer, question.answerImages)
                            }
                        }
                        number++
                    }
                }
                category.categories.forEach { child ->
                    appendCategory(child, path)
                }
            }

            content.categories.forEach { category ->
                appendCategory(category, emptyList())
            }

            if (includeAnswers && answersAtEnd && answers.isNotEmpty()) {
                pageWriter.startAnswerSection()
                answers.forEach { answer ->
                    checkActive()
                    pageWriter.drawAnswerEntry(
                        answer.number,
                        answer.blocks,
                        answer.images
                    )
                }
            }
            pageWriter.finish()
            document.writeTo(output)
        } finally {
            // checkActive() can throw from any of the drawing loops, leaving a
            // page started but never finished. PdfDocument.close() must not be
            // called while a page is open, so finish it here first. The helper
            // is null-safe: finish() already finished the page on the normal
            // path, so this is a no-op there (no double finish).
            cleanupWriter?.finishCurrentPageIfNeeded()
            document.close()
            // Safe now that finishPage/writeTo (and close) are done. Runs on
            // failure paths too, so a failed export never leaks native pixels.
            decodedBitmaps.forEach { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    private data class AnswerEntry(
        val number: Int,
        val blocks: List<RichBlock>,
        val images: List<ImageContent>
    )

    private class PageWriter(
        private val document: PdfDocument,
        private val runningTitle: String,
        private val decodedBitmaps: MutableList<Bitmap>,
        private val checkActive: () -> Unit
    ) {
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 36, 51)
            textSize = 11.5f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 36, 51)
            textSize = 20f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        private val categoryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(61, 77, 135)
            textSize = 14.5f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        private val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 107, 124)
            textSize = 9.5f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        private val answerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(59, 65, 82)
            textSize = 10.5f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(125, 130, 142)
            textSize = 8.5f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(216, 211, 198)
            strokeWidth = 0.8f
            style = Paint.Style.STROKE
        }
        private val answerRulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(190, 198, 224)
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        private val tableBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(191, 197, 212)
            strokeWidth = 0.8f
            style = Paint.Style.STROKE
        }
        private val tableHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(238, 241, 250)
            style = Paint.Style.FILL
        }
        private val tableAlternatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(247, 248, 251)
            style = Paint.Style.FILL
        }
        private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var pageNumber = 0
        private var y = MARGIN_TOP

        fun drawTitle(content: PaperContent) {
            drawBlock(
                text = content.paperName,
                paint = titlePaint,
                lineHeight = 25f,
                spacingAfter = 4f,
                requiredSpaceAfter = 46f
            )
            val total = if (content.questionCount == 1) "1 question" else {
                "${content.questionCount} questions"
            }
            val categories = if (content.categoryCount == 1) "1 category" else {
                "${content.categoryCount} categories"
            }
            drawBlock(
                text = "$categories  \u00b7  $total",
                paint = metaPaint,
                lineHeight = 14f,
                spacingAfter = 8f
            )
            val targetCanvas = requireNotNull(canvas)
            targetCanvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, rulePaint)
            y += 14f
        }

        fun drawCategory(name: String) {
            if (page != null) y += 8f
            drawBlock(
                text = name,
                paint = categoryPaint,
                lineHeight = 20f,
                spacingAfter = 7f,
                requiredSpaceAfter = 46f
            )
        }

        fun drawQuestion(number: Int, blocks: List<RichBlock>, includeAnswers: Boolean) {
            drawRichBlocks(
                blocks = blocks,
                paint = bodyPaint,
                prefix = "$number. ",
                spacingAfter = if (includeAnswers) 5f else 14f
            )
        }

        fun startAnswerSection() {
            if (page != null) newPage()
            drawCategory("Answers")
        }

        fun drawAnswerEntry(
            number: Int,
            blocks: List<RichBlock>,
            images: List<ImageContent> = emptyList()
        ) {
            drawRichBlocks(
                blocks = blocks,
                paint = bodyPaint,
                prefix = "$number. ",
                spacingAfter = if (images.isEmpty()) 12f else 4f
            )
            drawImages(images)
            if (images.isNotEmpty()) y += 8f
        }

        fun drawAnswer(blocks: List<RichBlock>, images: List<ImageContent> = emptyList()) {
            val lineX = MARGIN_LEFT + 12f
            if (y + 30f > CONTENT_BOTTOM) newPage()
            val targetCanvas = requireNotNull(canvas)
            targetCanvas.drawLine(
                lineX - 7f,
                y + 2f,
                lineX - 7f,
                y + 24f,
                answerRulePaint
            )
            drawRichBlocks(
                blocks = blocks,
                paint = answerPaint,
                prefix = "Answer: ",
                spacingAfter = if (images.isEmpty()) 14f else 4f,
                leftIndent = 12f
            )
            drawImages(images)
            if (images.isNotEmpty()) y += 8f
        }

        fun drawImages(images: List<ImageContent>) {
            images.forEach { image ->
                checkActive()
                // Read only the dimensions first so the sample size can be
                // chosen for the rectangle the image will actually occupy
                // (content width is ~499pt) instead of decoding every stored
                // byte at full resolution.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                try {
                    BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size, bounds)
                } catch (_: Exception) {
                    return@forEach
                }
                val sourceWidth = bounds.outWidth
                val sourceHeight = bounds.outHeight
                if (sourceWidth <= 0 || sourceHeight <= 0) return@forEach

                val maxWidth = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT
                val maxHeight = min(300f, CONTENT_BOTTOM - MARGIN_TOP)
                // Never upscale: the drawn rectangle is bounded by the source
                // dimensions, exactly as before.
                val scale = min(
                    1f,
                    min(
                        maxWidth / sourceWidth.toFloat(),
                        maxHeight / sourceHeight.toFloat()
                    )
                )
                val drawWidth = sourceWidth * scale
                val drawHeight = sourceHeight * scale

                // Keep roughly two decoded pixels per page point (about 144
                // dpi at 72 pt/in) so print stays legible, plus a hard pixel
                // budget as a safety net for pathological sources.
                val targetWidth = drawWidth.times(IMAGE_PIXELS_PER_POINT).toInt().coerceAtLeast(1)
                val targetHeight = drawHeight.times(IMAGE_PIXELS_PER_POINT).toInt().coerceAtLeast(1)
                var sampleSize = 1
                while (
                    sourceWidth / (sampleSize * 2) >= targetWidth &&
                    sourceHeight / (sampleSize * 2) >= targetHeight
                ) {
                    sampleSize *= 2
                }
                while (
                    (sourceWidth / sampleSize).toLong() * (sourceHeight / sampleSize) >
                        IMAGE_PIXEL_BUDGET
                ) {
                    sampleSize *= 2
                }

                val bitmap = try {
                    BitmapFactory.decodeByteArray(
                        image.bytes,
                        0,
                        image.bytes.size,
                        BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    )
                } catch (_: Exception) {
                    null
                } ?: return@forEach

                // Keep it alive until the whole PDF is written; the export
                // method recycles every collected bitmap in its finally block.
                decodedBitmaps += bitmap
                if (bitmap.width <= 0 || bitmap.height <= 0) return@forEach

                if (y + drawHeight + 10f > CONTENT_BOTTOM) newPage()
                ensurePage()
                val targetCanvas = requireNotNull(canvas)
                targetCanvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(
                        MARGIN_LEFT,
                        y,
                        MARGIN_LEFT + drawWidth,
                        y + drawHeight
                    ),
                    imagePaint
                )
                y += drawHeight + 8f
            }
        }

        private fun drawRichBlocks(
            blocks: List<RichBlock>,
            paint: Paint,
            prefix: String?,
            spacingAfter: Float,
            leftIndent: Float = 0f
        ) {
            if (blocks.isEmpty()) {
                if (prefix != null) {
                    drawBlock(
                        text = prefix.trimEnd(),
                        paint = paint,
                        lineHeight = paint.textSize * 1.35f,
                        spacingAfter = spacingAfter,
                        leftIndent = leftIndent
                    )
                }
                return
            }

            var pendingPrefix = prefix
            blocks.forEachIndexed { index, block ->
                checkActive()
                when (block) {
                    is RichBlock.Paragraph -> {
                        drawRichParagraph(
                            inlines = block.inlines,
                            paint = paint,
                            prefix = pendingPrefix,
                            spacingAfter = if (index == blocks.lastIndex) {
                                spacingAfter
                            } else {
                                6f
                            },
                            leftIndent = leftIndent
                        )
                        pendingPrefix = null
                    }

                    is RichBlock.Table -> {
                        if (pendingPrefix != null) {
                            drawBlock(
                                text = pendingPrefix.trimEnd(),
                                paint = paint,
                                lineHeight = paint.textSize * 1.35f,
                                spacingAfter = 4f,
                                leftIndent = leftIndent
                            )
                            pendingPrefix = null
                        }
                        drawTable(block, leftIndent)
                        y += if (index == blocks.lastIndex) spacingAfter else 8f
                    }
                }
            }
            if (pendingPrefix != null) {
                drawBlock(
                    text = pendingPrefix.trimEnd(),
                    paint = paint,
                    lineHeight = paint.textSize * 1.35f,
                    spacingAfter = spacingAfter,
                    leftIndent = leftIndent
                )
            }
        }

        private fun drawRichParagraph(
            inlines: List<RichInline>,
            paint: Paint,
            prefix: String?,
            spacingAfter: Float,
            leftIndent: Float
        ) {
            val spanned = RichTextSpans.build(
                inlines = inlines,
                color = paint.color,
                highlightColor = HIGHLIGHT_COLOR
            )
            val builder = SpannableStringBuilder()
            if (prefix != null) {
                val prefixText = SpannableString(prefix)
                prefixText.setSpan(
                    StyleSpan(Typeface.BOLD),
                    0,
                    prefixText.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.append(prefixText)
            }
            builder.append(spanned.text)

            val textPaint = TextPaint(paint)
            val width = (PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT - leftIndent)
                .toInt()
                .coerceAtLeast(1)
            val layout = StaticLayout.Builder.obtain(
                builder,
                0,
                builder.length,
                textPaint,
                width
            )
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(
                    0f,
                    if (spanned.hasFractions) 1.58f else 1.15f
                )
                .setIncludePad(false)
                .build()
            drawStaticLayout(layout, leftIndent, spacingAfter)
        }

        private fun drawStaticLayout(
            layout: StaticLayout,
            leftIndent: Float,
            spacingAfter: Float
        ) {
            if (layout.height <= 0) {
                y += spacingAfter
                return
            }
            if (y + layout.height + spacingAfter <= CONTENT_BOTTOM) {
                val targetCanvas = ensurePage()
                targetCanvas.save()
                targetCanvas.translate(MARGIN_LEFT + leftIndent, y)
                layout.draw(targetCanvas)
                targetCanvas.restore()
                y += layout.height + spacingAfter
                return
            }

            var offset = 0
            while (offset < layout.height) {
                checkActive()
                ensurePage()
                val available = (CONTENT_BOTTOM - y).toInt()
                if (available <= 2) {
                    newPage()
                    continue
                }
                val startLine = layout.getLineForVertical(offset)
                val lineTop = layout.getLineTop(startLine)
                val lineHeight = layout.getLineBottom(startLine) - lineTop
                if (lineHeight > available && y > MARGIN_TOP + 1f) {
                    newPage()
                    continue
                }
                var lastLine = startLine
                while (lastLine + 1 < layout.lineCount &&
                    layout.getLineBottom(lastLine + 1) - lineTop <= available
                ) {
                    lastLine++
                }
                val endOffset = layout.getLineBottom(lastLine)
                if (endOffset <= offset) {
                    newPage()
                    continue
                }
                val targetCanvas = requireNotNull(canvas)
                targetCanvas.save()
                targetCanvas.clipRect(
                    MARGIN_LEFT + leftIndent,
                    y,
                    PAGE_WIDTH - MARGIN_RIGHT,
                    CONTENT_BOTTOM
                )
                targetCanvas.translate(MARGIN_LEFT + leftIndent, y - lineTop)
                layout.draw(targetCanvas)
                targetCanvas.restore()
                y += (endOffset - lineTop).toFloat()
                offset = endOffset
                if (offset < layout.height) newPage()
            }
            y += spacingAfter
        }

        private fun drawTable(table: RichBlock.Table, leftIndent: Float) {
            val columnCount = max(
                table.headers.size,
                table.rows.maxOfOrNull { it.size } ?: 0
            ).coerceAtLeast(1)
            val tableWidth = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT - leftIndent
            val cellWidth = tableWidth / columnCount
            val cellPadding = 4f
            val startX = MARGIN_LEFT + leftIndent
            val tablePaint = TextPaint(bodyPaint).apply {
                textSize = 9.8f
            }
            // Continuation pages carry the running header, so their writable
            // area starts a little lower than the first page's does.
            val continuationTop = MARGIN_TOP + 18f
            val freshPageUsable = CONTENT_BOTTOM - continuationTop

            fun cellLayout(
                cell: List<RichInline>,
                forceBold: Boolean,
                alignment: Layout.Alignment
            ): StaticLayout {
                val spanned = RichTextSpans.build(
                    inlines = cell,
                    color = tablePaint.color,
                    highlightColor = HIGHLIGHT_COLOR,
                    forceBold = forceBold
                )
                val width = (cellWidth - cellPadding * 2).toInt().coerceAtLeast(1)
                return StaticLayout.Builder.obtain(
                    spanned.text,
                    0,
                    spanned.text.length,
                    TextPaint(tablePaint),
                    width
                )
                    .setAlignment(alignment)
                    .setLineSpacing(0f, if (spanned.hasFractions) 1.5f else 1.08f)
                    .setIncludePad(false)
                    .build()
            }

            fun layoutsFor(
                cells: List<List<RichInline>>,
                header: Boolean
            ): List<StaticLayout> = (0 until columnCount).map { index ->
                val cell = cells.getOrNull(index).orEmpty()
                val alignment = when (table.alignments.getOrNull(index)) {
                    ColumnAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
                    ColumnAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                    else -> Layout.Alignment.ALIGN_NORMAL
                }
                cellLayout(cell, header, alignment)
            }

            fun rowHeightOf(layouts: List<StaticLayout>): Float =
                (layouts.maxOfOrNull { it.height } ?: 0) + cellPadding * 2

            // Draws a whole, already-laid-out row at the current y. The caller
            // guarantees there is enough room for the entire row.
            fun drawWholeRow(
                layouts: List<StaticLayout>,
                header: Boolean,
                alternate: Boolean
            ) {
                val rowHeight = rowHeightOf(layouts)
                val targetCanvas = ensurePage()
                val rowTop = y
                layouts.forEachIndexed { index, cellLayoutValue ->
                    val cellX = startX + index * cellWidth
                    val backgroundPaint = when {
                        header -> tableHeaderPaint
                        alternate -> tableAlternatePaint
                        else -> null
                    }
                    if (backgroundPaint != null) {
                        targetCanvas.drawRect(
                            cellX + 0.5f,
                            rowTop + 0.5f,
                            cellX + cellWidth - 0.5f,
                            rowTop + rowHeight - 0.5f,
                            backgroundPaint
                        )
                    }
                    targetCanvas.save()
                    targetCanvas.clipRect(
                        cellX + cellPadding,
                        rowTop + cellPadding,
                        cellX + cellWidth - cellPadding,
                        rowTop + rowHeight - cellPadding
                    )
                    targetCanvas.translate(
                        cellX + cellPadding,
                        rowTop + cellPadding
                    )
                    cellLayoutValue.draw(targetCanvas)
                    targetCanvas.restore()
                    targetCanvas.drawRect(
                        cellX,
                        rowTop,
                        cellX + cellWidth,
                        rowTop + rowHeight,
                        tableBorderPaint
                    )
                }
                y += rowHeight
            }

            // Draws one vertical slice of an over-long row into the band
            // [bandTop, bandBottom) in page coordinates. `rowTop` is where the
            // row conceptually starts; cell text is translated by
            // rowTop + cellPadding and clipped to the band, so exactly the
            // lines that fall inside this slice are painted and nothing is
            // silently dropped. startLine/getLineBottom guard against drawing
            // a cell that has no lines left in this slice.
            fun drawRowSlice(
                layouts: List<StaticLayout>,
                rowTop: Float,
                bandTop: Float,
                bandBottom: Float,
                header: Boolean,
                alternate: Boolean
            ) {
                val targetCanvas = ensurePage()
                val textStart = rowTop + cellPadding
                val maxTextHeight = layouts.maxOfOrNull { it.height } ?: 0
                val fromVertical = (bandTop - textStart).toInt().coerceAtLeast(0)
                val toVertical = (bandBottom - textStart).toInt()
                    .coerceAtLeast(0).coerceAtMost(maxTextHeight)
                val backgroundPaint = when {
                    header -> tableHeaderPaint
                    alternate -> tableAlternatePaint
                    else -> null
                }
                layouts.forEachIndexed { index, cellLayoutValue ->
                    val cellX = startX + index * cellWidth
                    if (backgroundPaint != null) {
                        targetCanvas.drawRect(
                            cellX + 0.5f,
                            bandTop + 0.5f,
                            cellX + cellWidth - 0.5f,
                            bandBottom - 0.5f,
                            backgroundPaint
                        )
                    }
                    if (fromVertical < toVertical && toVertical > 0) {
                        val startLine = cellLayoutValue.getLineForVertical(fromVertical)
                        if (startLine < cellLayoutValue.lineCount &&
                            cellLayoutValue.getLineBottom(startLine) > fromVertical
                        ) {
                            targetCanvas.save()
                            targetCanvas.clipRect(
                                cellX + cellPadding,
                                bandTop,
                                cellX + cellWidth - cellPadding,
                                bandBottom
                            )
                            targetCanvas.translate(cellX + cellPadding, textStart)
                            cellLayoutValue.draw(targetCanvas)
                            targetCanvas.restore()
                        }
                    }
                    targetCanvas.drawRect(
                        cellX,
                        bandTop,
                        cellX + cellWidth,
                        bandBottom,
                        tableBorderPaint
                    )
                }
            }

            fun drawRow(
                cells: List<List<RichInline>>,
                header: Boolean,
                alternate: Boolean
            ) {
                val layouts = layoutsFor(cells, header)
                val rowHeight = rowHeightOf(layouts)
                if (y + rowHeight <= CONTENT_BOTTOM) {
                    // Normal row: unchanged behaviour.
                    drawWholeRow(layouts, header, alternate)
                    return
                }
                if (rowHeight <= freshPageUsable) {
                    // Fits on a fresh page: keep the old "move the whole row"
                    // behaviour instead of splitting it.
                    newPage()
                    drawWholeRow(layouts, header, alternate)
                    return
                }

                // The row is taller than a page. Draw it slice by slice,
                // repeating the table header at the top of every continuation
                // page so the column meanings stay visible.
                val headerLayouts = layoutsFor(table.headers, header = true)
                val headerHeight = rowHeightOf(headerLayouts)
                val repeatHeader = headerHeight < freshPageUsable - 4f
                var offset = 0f
                var needHeader = false
                while (offset < rowHeight) {
                    checkActive()
                    if (needHeader) {
                        newPage()
                        if (repeatHeader) {
                            drawWholeRow(headerLayouts, header = true, alternate = false)
                        }
                        needHeader = false
                    }
                    val available = CONTENT_BOTTOM - y
                    if (available <= 1f) {
                        needHeader = true
                        continue
                    }
                    val sliceHeight = min(available, rowHeight - offset)
                    val rowTop = y - offset
                    drawRowSlice(
                        layouts = layouts,
                        rowTop = rowTop,
                        bandTop = y,
                        bandBottom = y + sliceHeight,
                        header = header,
                        alternate = alternate
                    )
                    y += sliceHeight
                    offset += sliceHeight
                    if (offset < rowHeight) needHeader = true
                }
            }

            if (y + 70f > CONTENT_BOTTOM) newPage()
            drawRow(table.headers, header = true, alternate = false)
            table.rows.forEachIndexed { index, row ->
                drawRow(row, header = false, alternate = index % 2 == 1)
            }
            y += 4f
        }

        fun drawBody(text: String, spacingAfter: Float) {
            drawBlock(
                text = text,
                paint = bodyPaint,
                lineHeight = 16f,
                spacingAfter = spacingAfter
            )
        }

        private fun drawBlock(
            text: String,
            paint: Paint,
            lineHeight: Float,
            spacingAfter: Float,
            leftIndent: Float = 0f,
            requiredSpaceAfter: Float = 0f
        ) {
            val lines = wrapText(text, paint, PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT - leftIndent)
            ensurePage()

            val usableHeight = CONTENT_BOTTOM - MARGIN_TOP
            val blockHeight = lines.size * lineHeight
            val fitsOnOnePage = blockHeight <= usableHeight
            val needed = if (fitsOnOnePage) {
                blockHeight + spacingAfter + requiredSpaceAfter
            } else {
                lineHeight
            }
            if (y + needed > CONTENT_BOTTOM) {
                newPage()
            }

            var targetCanvas = requireNotNull(canvas)
            val metrics = paint.fontMetrics
            lines.forEach { line ->
                if (y + lineHeight > CONTENT_BOTTOM) {
                    newPage()
                    targetCanvas = requireNotNull(canvas)
                }
                if (line.isNotEmpty()) {
                    val baseline = y +
                        (lineHeight - (metrics.descent - metrics.ascent)) / 2f -
                        metrics.ascent
                    targetCanvas.drawText(line, MARGIN_LEFT + leftIndent, baseline, paint)
                }
                y += lineHeight
            }
            y += spacingAfter
        }

        fun finish() {
            if (page == null) beginPage()
            finishPage()
        }

        /**
         * Cleanup for failure/cancellation paths only. If a page is still open,
         * finish it via the normal [finishPage] logic so [document].close() is
         * never called with an unfinished page. Null-safe by design: [finish]
         * sets `page` to null after finishing it, so a successful export does
         * not finish the same page twice. Any cleanup failure is swallowed so
         * it cannot mask the exception that triggered this path.
         */
        fun finishCurrentPageIfNeeded() {
            if (page == null) return
            try {
                finishPage()
            } catch (_: Throwable) {
                // Best-effort cleanup only; never replace the original error.
            }
        }

        private fun ensurePage(): Canvas {
            if (page == null) beginPage()
            return requireNotNull(canvas)
        }

        private fun beginPage() {
            val info = PdfDocument.PageInfo.Builder(
                PAGE_WIDTH.toInt(),
                PAGE_HEIGHT.toInt(),
                pageNumber + 1
            ).create()
            val newPage = document.startPage(info)
            page = newPage
            canvas = newPage.canvas
            pageNumber++
            y = MARGIN_TOP

            if (pageNumber > 1) {
                val drawCanvas = requireNotNull(canvas)
                // Keep the running header on one line. Long paper names are
                // trimmed with an ellipsis so they never run off the right edge.
                val availableWidth = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT
                val fittedTitle = if (metaPaint.measureText(runningTitle) <= availableWidth) {
                    runningTitle
                } else {
                    val ellipsis = "\u2026"
                    val ellipsisWidth = metaPaint.measureText(ellipsis)
                    var end = runningTitle.length
                    while (end > 0 &&
                        metaPaint.measureText(runningTitle.substring(0, end)) + ellipsisWidth > availableWidth
                    ) {
                        end--
                    }
                    runningTitle.substring(0, end) + ellipsis
                }
                drawCanvas.drawText(fittedTitle, MARGIN_LEFT, MARGIN_TOP - 12f, metaPaint)
                drawCanvas.drawLine(
                    MARGIN_LEFT,
                    MARGIN_TOP,
                    PAGE_WIDTH - MARGIN_RIGHT,
                    MARGIN_TOP,
                    rulePaint
                )
                y = MARGIN_TOP + 18f
            }
        }

        private fun newPage() {
            finishPage()
            beginPage()
        }

        private fun finishPage() {
            val currentPage = page ?: return
            val currentCanvas = requireNotNull(canvas)
            val label = "Page $pageNumber"
            val width = footerPaint.measureText(label)
            currentCanvas.drawText(
                label,
                (PAGE_WIDTH - width) / 2f,
                PAGE_HEIGHT - 28f,
                footerPaint
            )
            document.finishPage(currentPage)
            page = null
            canvas = null
        }

        private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
            if (text.isEmpty()) return listOf("")

            val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
            val lines = mutableListOf<String>()

            // Keep a trailing space on wrapped lines. It is invisible on the
            // page but prevents words running together when a PDF viewer
            // extracts or copies the text.
            fun addLine(value: String) {
                lines += if (value.isEmpty()) "" else "$value "
            }

            normalized.split('\n').forEach { paragraph ->
                if (paragraph.isEmpty()) {
                    lines += ""
                    return@forEach
                }

                var line = StringBuilder()
                paragraph.split(Regex("\\s+"))
                    .filter { it.isNotEmpty() }
                    .forEach { word ->
                        val candidate = if (line.isEmpty()) word else "$line $word"
                        if (paint.measureText(candidate) <= maxWidth) {
                            line = StringBuilder(candidate)
                        } else {
                            if (line.isNotEmpty()) {
                                addLine(line.toString())
                                line = StringBuilder()
                            }
                            if (paint.measureText(word) <= maxWidth) {
                                line.append(word)
                            } else {
                                // Break a single unbroken token (URL, long word)
                                // so it can never run off the right edge.
                                word.forEach { character ->
                                    val attempt = line.toString() + character
                                    if (paint.measureText(attempt) > maxWidth && line.isNotEmpty()) {
                                        addLine(line.toString())
                                        line = StringBuilder()
                                    }
                                    line.append(character)
                                }
                            }
                        }
                    }
                if (line.isNotEmpty()) addLine(line.toString())
            }

            return if (lines.isEmpty()) listOf("") else lines
        }
    }

    private const val PAGE_WIDTH = 595f
    private const val PAGE_HEIGHT = 842f
    private const val MARGIN_LEFT = 48f
    private const val MARGIN_RIGHT = 48f
    private const val MARGIN_TOP = 46f
    private const val CONTENT_BOTTOM = PAGE_HEIGHT - 48f
    // Downsampling targets: ~2 px per point keeps embedded images legible in
    // print; the budget caps any single decoded bitmap at ~4 megapixels.
    private const val IMAGE_PIXELS_PER_POINT = 2f
    private const val IMAGE_PIXEL_BUDGET = 4_000_000
    private val HIGHLIGHT_COLOR = Color.rgb(255, 240, 168)
}
