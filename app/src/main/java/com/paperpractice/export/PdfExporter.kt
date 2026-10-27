package com.paperpractice.export

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.paperpractice.domain.CategoryContent
import com.paperpractice.domain.ImageContent
import com.paperpractice.domain.PaperContent
import com.paperpractice.domain.RichText
import java.io.OutputStream
import kotlin.math.min

/**
 * Draws a paginated PDF with the platform's PdfDocument. Using Android's text
 * rendering means imported Unicode text is honoured by the device's fonts, and
 * the manual line packer can continue a single long answer across pages
 * without ever dropping content.
 */
object PdfExporter {

    fun write(
        content: PaperContent,
        includeAnswers: Boolean,
        answersAtEnd: Boolean,
        output: OutputStream
    ) {
        val document = PdfDocument()
        try {
            val writer = PageWriter(document, content.paperName)
            writer.drawTitle(content)
            if (content.categories.isEmpty()) {
                writer.drawBody("This paper contains no categories.", spacingAfter = 12f)
            }
            var number = 1
            val answers = mutableListOf<AnswerEntry>()

            fun appendCategory(
                category: CategoryContent,
                parentPath: List<String>
            ) {
                val path = parentPath + category.categoryName
                if (category.questions.isNotEmpty()) {
                    writer.drawCategory(categoryExportName(parentPath, category.categoryName))
                    category.questions.forEach { question ->
                        writer.drawQuestion(
                            number,
                            RichText.format(question.questionText),
                            includeAnswers && !answersAtEnd
                        )
                        writer.drawImages(question.questionImages)
                        if (includeAnswers) {
                            val answer = RichText.format(
                                question.answerText.ifBlank { "(No answer provided)" }
                            )
                            if (answersAtEnd) {
                                answers += AnswerEntry(
                                    number = number,
                                    text = answer,
                                    images = question.answerImages
                                )
                            } else {
                                writer.drawAnswer(answer, question.answerImages)
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
                writer.startAnswerSection()
                answers.forEach { answer ->
                    writer.drawAnswerEntry(
                        answer.number,
                        answer.text,
                        answer.images
                    )
                }
            }
            writer.finish()
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    private data class AnswerEntry(
        val number: Int,
        val text: String,
        val images: List<ImageContent>
    )

    private class PageWriter(
        private val document: PdfDocument,
        private val runningTitle: String
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

        fun drawQuestion(number: Int, text: String, includeAnswers: Boolean) {
            drawBlock(
                text = "$number. $text",
                paint = bodyPaint,
                lineHeight = 16f,
                spacingAfter = if (includeAnswers) 5f else 14f
            )
        }

        fun startAnswerSection() {
            if (page != null) newPage()
            drawCategory("Answers")
        }

        fun drawAnswerEntry(
            number: Int,
            text: String,
            images: List<ImageContent> = emptyList()
        ) {
            drawBlock(
                text = "$number. $text",
                paint = bodyPaint,
                lineHeight = 16f,
                spacingAfter = if (images.isEmpty()) 12f else 4f
            )
            drawImages(images)
            if (images.isNotEmpty()) y += 8f
        }

        fun drawAnswer(text: String, images: List<ImageContent> = emptyList()) {
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
            drawBlock(
                text = "Answer: $text",
                paint = answerPaint,
                lineHeight = 15f,
                spacingAfter = if (images.isEmpty()) 14f else 4f,
                leftIndent = 12f
            )
            drawImages(images)
            if (images.isNotEmpty()) y += 8f
        }

        fun drawImages(images: List<ImageContent>) {
            images.forEach { image ->
                val bitmap = try {
                    BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
                } catch (_: Exception) {
                    null
                } ?: return@forEach

                if (bitmap.width <= 0 || bitmap.height <= 0) return@forEach
                val maxWidth = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT
                val maxHeight = min(300f, CONTENT_BOTTOM - MARGIN_TOP)
                val scale = min(
                    1f,
                    min(
                        maxWidth / bitmap.width.toFloat(),
                        maxHeight / bitmap.height.toFloat()
                    )
                )
                val drawWidth = bitmap.width * scale
                val drawHeight = bitmap.height * scale

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
                drawCanvas.drawText(runningTitle, MARGIN_LEFT, MARGIN_TOP - 12f, metaPaint)
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
}
