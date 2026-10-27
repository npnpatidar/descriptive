package com.paperpractice.export

import com.paperpractice.domain.CategoryContent
import com.paperpractice.domain.ImageContent
import com.paperpractice.domain.ImageMetadata
import com.paperpractice.domain.PaperContent
import com.paperpractice.domain.RichBlock
import com.paperpractice.domain.RichInline
import com.paperpractice.domain.RichInlineStyle
import com.paperpractice.domain.RichText
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * A small, dependency-free OOXML writer. Producing WordprocessingML directly
 * keeps the app offline and avoids shipping a full document library for what
 * is ultimately a sequence of styled paragraphs. Word, LibreOffice, and Google
 * Docs all paginate the result and keep long questions intact.
 */
object DocxExporter {

    fun write(
        content: PaperContent,
        includeAnswers: Boolean,
        answersAtEnd: Boolean,
        output: OutputStream
    ) {
        val images = collectImages(content, includeAnswers)
        val zip = ZipOutputStream(output)
        zip.putNextEntry(ZipEntry("[Content_Types].xml"))
        zip.write(CONTENT_TYPES.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("_rels/.rels"))
        zip.write(ROOT_RELS.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
        zip.write(documentRelationships(images).toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("word/document.xml"))
        zip.write(
            documentXml(content, includeAnswers, answersAtEnd, images)
                .toByteArray(Charsets.UTF_8)
        )
        zip.closeEntry()

        images.forEach { asset ->
            zip.putNextEntry(ZipEntry(asset.partName))
            zip.write(asset.image.bytes)
            zip.closeEntry()
        }

        zip.finish()
        zip.flush()
    }

    private fun collectImages(
        content: PaperContent,
        includeAnswers: Boolean
    ): List<DocxImageAsset> {
        val assets = mutableListOf<DocxImageAsset>()
        val seen = mutableSetOf<String>()

        fun add(images: List<ImageContent>) {
            images.forEach { image ->
                if (!seen.add(image.id)) return@forEach
                val extension = when (image.mimeType.lowercase()) {
                    "image/png" -> "png"
                    "image/gif" -> "gif"
                    "image/webp" -> "webp"
                    "image/bmp" -> "bmp"
                    else -> "jpeg"
                }
                val index = assets.size + 1
                assets += DocxImageAsset(
                    image = image,
                    index = index,
                    relationshipId = "rId${index + 1}",
                    partName = "word/media/image$index.$extension"
                )
            }
        }

        fun visit(category: CategoryContent) {
            category.questions.forEach { question ->
                add(question.questionImages)
                if (includeAnswers) add(question.answerImages)
            }
            category.categories.forEach(::visit)
        }

        content.categories.forEach(::visit)
        return assets
    }

    private fun documentRelationships(images: List<DocxImageAsset>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append(
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
        )
        images.forEach { asset ->
            append(
                "<Relationship Id=\"${asset.relationshipId}\" " +
                    "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" " +
                    "Target=\"media/${asset.partName.substringAfterLast('/')}\"/>"
            )
        }
        append("</Relationships>")
    }

    private fun documentXml(
        content: PaperContent,
        includeAnswers: Boolean,
        answersAtEnd: Boolean,
        images: List<DocxImageAsset>
    ): String {
        val imagesById = images.associateBy { it.image.id }
        val totalLabel = if (content.questionCount == 1) "1 question" else {
            "${content.questionCount} questions"
        }
        val categoryCount = content.categoryCount
        val categoryLabel = if (categoryCount == 1) {
            "1 category"
        } else {
            "$categoryCount categories"
        }

        val body = StringBuilder()
        body.append(
            paragraph(
                runs = listOf(
                    Run(
                        text = content.paperName,
                        bold = true,
                        sizeHalfPoints = 40,
                        color = "1E2433"
                    )
                ),
                spacingAfter = 80
            )
        )
        body.append(
            paragraph(
                runs = listOf(
                    Run(
                        text = "$categoryLabel  \u00b7  $totalLabel",
                        sizeHalfPoints = 19,
                        color = "646B7C"
                    )
                ),
                spacingAfter = 240,
                borderBottom = true
            )
        )

        var number = 1
        val answers = mutableListOf<DocxAnswerEntry>()

        fun appendCategory(
            category: CategoryContent,
            parentPath: List<String>,
            headingDepth: Int
        ) {
            val path = parentPath + category.categoryName
            if (category.questions.isNotEmpty()) {
                body.append(
                    paragraph(
                        runs = listOf(
                            Run(
                                text = categoryExportName(parentPath, category.categoryName),
                                bold = true,
                                sizeHalfPoints = (28 - headingDepth * 2).coerceAtLeast(22),
                                color = "3D4D87"
                            )
                        ),
                        spacingBefore = if (headingDepth == 0) 320 else 220,
                        spacingAfter = 120,
                        keepNext = true
                    )
                )

                category.questions.forEach { question ->
                    appendRichBlocks(
                        body = body,
                        blocks = RichText.parse(question.questionText).blocks,
                        prefix = listOf(
                            Run(
                                text = "$number. ",
                                bold = true,
                                sizeHalfPoints = 23,
                                color = "3D4D87"
                            )
                        ),
                        sizeHalfPoints = 23,
                        color = "1E2433",
                        spacingAfter = if (includeAnswers && !answersAtEnd) 100 else 220
                    )

                    question.questionImages.forEach { image ->
                        imagesById[image.id]?.let { asset ->
                            body.append(imageParagraph(asset))
                        }
                    }

                    if (includeAnswers) {
                        val blocks = RichText.parse(
                            question.answerText.ifBlank { "(No answer provided)" }
                        ).blocks
                        if (answersAtEnd) {
                            answers += DocxAnswerEntry(
                                number = number,
                                blocks = blocks,
                                images = question.answerImages
                            )
                        } else {
                            appendRichBlocks(
                                body = body,
                                blocks = blocks,
                                prefix = listOf(
                                    Run(
                                        text = "Answer: ",
                                        bold = true,
                                        sizeHalfPoints = 20,
                                        color = "646B7C"
                                    )
                                ),
                                sizeHalfPoints = 20,
                                color = "3B4152",
                                spacingAfter = if (question.answerImages.isEmpty()) {
                                    220
                                } else {
                                    100
                                },
                                indentLeft = 360
                            )
                            question.answerImages.forEach { image ->
                                imagesById[image.id]?.let { asset ->
                                    body.append(imageParagraph(asset, indentLeft = 360))
                                }
                            }
                        }
                    }
                    number++
                }
            }

            val childHeadingDepth = if (category.questions.isNotEmpty()) {
                headingDepth + 1
            } else {
                headingDepth
            }
            category.categories.forEach { child ->
                appendCategory(child, path, childHeadingDepth)
            }
        }

        content.categories.forEach { category ->
            appendCategory(category, emptyList(), 0)
        }

        if (includeAnswers && answersAtEnd && answers.isNotEmpty()) {
            body.append(
                paragraph(
                    runs = listOf(
                        Run(
                            text = "Answers",
                            bold = true,
                            sizeHalfPoints = 32,
                            color = "3D4D87"
                        )
                    ),
                    spacingAfter = 180,
                    keepNext = true,
                    borderBottom = true,
                    pageBreakBefore = true
                )
            )
            answers.forEach { answer ->
                appendRichBlocks(
                    body = body,
                    blocks = answer.blocks,
                    prefix = listOf(
                        Run(
                            text = "${answer.number}. ",
                            bold = true,
                            sizeHalfPoints = 21,
                            color = "3D4D87"
                        )
                    ),
                    sizeHalfPoints = 21,
                    color = "1E2433",
                    spacingAfter = if (answer.images.isEmpty()) 140 else 60
                )
                answer.images.forEach { image ->
                    imagesById[image.id]?.let { asset ->
                        body.append(imageParagraph(asset))
                    }
                }
            }
        }

        body.append(
            paragraph(
                runs = listOf(
                    Run(
                        text = "Generated offline by Paper Practice.",
                        sizeHalfPoints = 16,
                        color = "7A8090"
                    )
                ),
                spacingBefore = 360
            )
        )

        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                        xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math"
                        xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                        xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                        xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                        xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
              <w:body>
                ${body.toString().trimIndent()}
                <w:sectPr>
                  <w:pgSz w:w="11906" w:h="16838"/>
                  <w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134"
                           w:header="708" w:footer="708" w:gutter="0"/>
                  <w:cols w:space="708"/>
                  <w:docGrid w:linePitch="360"/>
                </w:sectPr>
              </w:body>
            </w:document>
        """.trimIndent()
    }

    private data class DocxImageAsset(
        val image: ImageContent,
        val index: Int,
        val relationshipId: String,
        val partName: String
    )

    private data class DocxAnswerEntry(
        val number: Int,
        val blocks: List<RichBlock>,
        val images: List<ImageContent>
    )

    private fun imageParagraph(asset: DocxImageAsset, indentLeft: Int = 0): String {
        val dimensions = if (asset.image.width > 0 && asset.image.height > 0) {
            asset.image.width to asset.image.height
        } else {
            ImageMetadata.dimensions(asset.image.bytes)
        }
        val widthPx = dimensions?.first ?: 800
        val heightPx = dimensions?.second ?: 600
        val maxWidthEmu = 5_400_000L
        val maxHeightEmu = 7_200_000L
        val naturalWidth = widthPx.toDouble() * 9_525.0
        val naturalHeight = heightPx.toDouble() * 9_525.0
        val scale = min(
            1.0,
            min(maxWidthEmu / naturalWidth, maxHeightEmu / naturalHeight)
        )
        val widthEmu = (naturalWidth * scale).toLong().coerceAtLeast(1L)
        val heightEmu = (naturalHeight * scale).toLong().coerceAtLeast(1L)

        return buildString {
            append("<w:p><w:pPr><w:spacing w:before=\"60\" w:after=\"80\"/>")
            if (indentLeft > 0) append("<w:ind w:left=\"$indentLeft\"/>")
            append("</w:pPr><w:r><w:drawing>")
            append(
                "<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">" +
                    "<wp:extent cx=\"$widthEmu\" cy=\"$heightEmu\"/>" +
                    "<wp:docPr id=\"${asset.index}\" name=\"Picture ${asset.index}\" " +
                    "descr=\"Embedded question image\"/>" +
                    "<a:graphic><a:graphicData " +
                    "uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
                    "<pic:pic><pic:nvPicPr>" +
                    "<pic:cNvPr id=\"${asset.index}\" name=\"Image ${asset.index}\"/>" +
                    "<pic:cNvPicPr/></pic:nvPicPr>" +
                    "<pic:blipFill><a:blip r:embed=\"${asset.relationshipId}\"/>" +
                    "<a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
                    "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/>" +
                    "<a:ext cx=\"$widthEmu\" cy=\"$heightEmu\"/></a:xfrm>" +
                    "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>" +
                    "</pic:spPr></pic:pic></a:graphicData></a:graphic>" +
                    "</wp:inline></w:drawing></w:r></w:p>"
            )
        }
    }

    private data class Run(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strikethrough: Boolean = false,
        val highlight: Boolean = false,
        val sizeHalfPoints: Int = 22,
        val color: String = "1E2433"
    )

    private fun paragraph(
        runs: List<Run>,
        spacingBefore: Int = 0,
        spacingAfter: Int = 120,
        indentLeft: Int = 0,
        keepNext: Boolean = false,
        borderBottom: Boolean = false,
        pageBreakBefore: Boolean = false
    ): String = buildString {
        append("<w:p><w:pPr>")
        if (keepNext) append("<w:keepNext/>")
        if (pageBreakBefore) append("<w:pageBreakBefore/>")
        if (borderBottom) {
            append(
                "<w:pBdr><w:bottom w:val=\"single\" w:sz=\"8\" w:space=\"4\" " +
                    "w:color=\"D8D3C6\"/></w:pBdr>"
            )
        }
        append("<w:spacing w:before=\"$spacingBefore\" w:after=\"$spacingAfter\"/>")
        if (indentLeft > 0) append("<w:ind w:left=\"$indentLeft\"/>")
        append("</w:pPr>")

        runs.forEach { run ->
            append(runXml(run))
        }
        append("</w:p>")
    }

    private fun runXml(run: Run): String = buildString {
        append("<w:r><w:rPr>")
        append("<w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:cs=\"Calibri\"/>")
        if (run.bold) append("<w:b/>")
        if (run.italic) append("<w:i/>")
        if (run.strikethrough) append("<w:strike/>")
        append("<w:color w:val=\"${run.color}\"/>")
        append("<w:sz w:val=\"${run.sizeHalfPoints}\"/>")
        append("<w:szCs w:val=\"${run.sizeHalfPoints}\"/>")
        if (run.underline) append("<w:u w:val=\"single\"/>")
        if (run.highlight) {
            append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"FFF0A8\"/>")
        }
        append("</w:rPr>")
        appendRunText(run.text)
        append("</w:r>")
    }

    private fun appendRichBlocks(
        body: StringBuilder,
        blocks: List<RichBlock>,
        prefix: List<Run>,
        sizeHalfPoints: Int,
        color: String,
        spacingAfter: Int,
        indentLeft: Int = 0
    ) {
        if (blocks.isEmpty()) {
            body.append(
                paragraph(
                    runs = prefix,
                    spacingAfter = spacingAfter,
                    indentLeft = indentLeft
                )
            )
            return
        }

        var pendingPrefix = prefix
        blocks.forEachIndexed { index, block ->
            when (block) {
                is RichBlock.Paragraph -> {
                    body.append(
                        richParagraph(
                            inlines = block.inlines,
                            prefix = pendingPrefix,
                            sizeHalfPoints = sizeHalfPoints,
                            color = color,
                            spacingAfter = if (index == blocks.lastIndex) {
                                spacingAfter
                            } else {
                                90
                            },
                            indentLeft = indentLeft
                        )
                    )
                    pendingPrefix = emptyList()
                }

                is RichBlock.Table -> {
                    if (pendingPrefix.isNotEmpty()) {
                        body.append(
                            paragraph(
                                runs = pendingPrefix,
                                spacingAfter = 70,
                                indentLeft = indentLeft
                            )
                        )
                        pendingPrefix = emptyList()
                    }
                    body.append(richTable(block, sizeHalfPoints, color))
                    body.append(
                        paragraph(
                            runs = emptyList(),
                            spacingAfter = if (index == blocks.lastIndex) spacingAfter else 80
                        )
                    )
                }
            }
        }
        if (pendingPrefix.isNotEmpty()) {
            body.append(
                paragraph(
                    runs = pendingPrefix,
                    spacingAfter = spacingAfter,
                    indentLeft = indentLeft
                )
            )
        }
    }

    private fun richParagraph(
        inlines: List<RichInline>,
        prefix: List<Run>,
        sizeHalfPoints: Int,
        color: String,
        spacingAfter: Int,
        indentLeft: Int = 0
    ): String = buildString {
        append("<w:p><w:pPr>")
        append("<w:spacing w:before=\"0\" w:after=\"$spacingAfter\"/>")
        if (indentLeft > 0) append("<w:ind w:left=\"$indentLeft\"/>")
        append("</w:pPr>")
        prefix.forEach { append(runXml(it)) }
        append(inlineXml(inlines, sizeHalfPoints, color))
        append("</w:p>")
    }

    private fun inlineXml(
        inlines: List<RichInline>,
        sizeHalfPoints: Int,
        color: String
    ): String = buildString {
        inlines.forEach { inline ->
            when (inline) {
                is RichInline.Text -> {
                    append(
                        runXml(
                            Run(
                                text = inline.text,
                                bold = inline.style.bold,
                                italic = inline.style.italic,
                                underline = inline.style.underline,
                                strikethrough = inline.style.strikethrough,
                                highlight = inline.style.highlight,
                                sizeHalfPoints = sizeHalfPoints,
                                color = color
                            )
                        )
                    )
                }

                is RichInline.Fraction -> {
                    append(fractionXml(inline, sizeHalfPoints, color))
                }
            }
        }
    }

    private fun fractionXml(
        fraction: RichInline.Fraction,
        sizeHalfPoints: Int,
        color: String
    ): String {
        val numerator = RichText.plain(fraction.numerator)
        val denominator = RichText.plain(fraction.denominator)
        return buildString {
            append("<m:oMath><m:f><m:num>")
            append(mathRunXml(numerator, fraction.style, sizeHalfPoints, color))
            append("</m:num><m:den>")
            append(mathRunXml(denominator, fraction.style, sizeHalfPoints, color))
            append("</m:den></m:f></m:oMath>")
        }
    }

    private fun mathRunXml(
        text: String,
        style: RichInlineStyle,
        sizeHalfPoints: Int,
        color: String
    ): String = buildString {
        append("<m:r><w:rPr>")
        append("<w:rFonts w:ascii=\"Cambria Math\" w:hAnsi=\"Cambria Math\"/>")
        if (style.bold) append("<w:b/>")
        if (style.italic) append("<w:i/>")
        append("<w:color w:val=\"$color\"/>")
        append("<w:sz w:val=\"$sizeHalfPoints\"/>")
        append("<w:szCs w:val=\"$sizeHalfPoints\"/>")
        append("</w:rPr><m:t xml:space=\"preserve\">")
        append(escapeXml(text))
        append("</m:t></m:r>")
    }

    private fun richTable(
        table: RichBlock.Table,
        sizeHalfPoints: Int,
        color: String
    ): String {
        val columnCount = max(
            table.headers.size,
            table.rows.maxOfOrNull { it.size } ?: 0
        ).coerceAtLeast(1)
        val cellWidthPercent = 5000 / columnCount
        val gridWidth = 9638 / columnCount

        return buildString {
            append("<w:tbl><w:tblPr>")
            append("<w:tblW w:w=\"5000\" w:type=\"pct\"/>")
            append("<w:tblBorders>")
            append("<w:top w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"BFC5D4\"/>")
            append("<w:left w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"BFC5D4\"/>")
            append("<w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"BFC5D4\"/>")
            append("<w:right w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"BFC5D4\"/>")
            append("<w:insideH w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"BFC5D4\"/>")
            append("<w:insideV w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"BFC5D4\"/>")
            append("</w:tblBorders>")
            append("<w:tblLayout w:type=\"fixed\"/>")
            append("<w:tblCellMar>")
            append("<w:top w:w=\"70\" w:type=\"dxa\"/>")
            append("<w:left w:w=\"100\" w:type=\"dxa\"/>")
            append("<w:bottom w:w=\"70\" w:type=\"dxa\"/>")
            append("<w:right w:w=\"100\" w:type=\"dxa\"/>")
            append("</w:tblCellMar>")
            append("</w:tblPr>")
            append("<w:tblGrid>")
            repeat(columnCount) {
                append("<w:gridCol w:w=\"$gridWidth\"/>")
            }
            append("</w:tblGrid>")

            append(
                tableRowXml(
                    cells = table.headers,
                    columnCount = columnCount,
                    cellWidthPercent = cellWidthPercent,
                    sizeHalfPoints = sizeHalfPoints,
                    color = color,
                    header = true,
                    alternate = false
                )
            )
            table.rows.forEachIndexed { index, row ->
                append(
                    tableRowXml(
                        cells = row,
                        columnCount = columnCount,
                        cellWidthPercent = cellWidthPercent,
                        sizeHalfPoints = sizeHalfPoints,
                        color = color,
                        header = false,
                        alternate = index % 2 == 1
                    )
                )
            }
            append("</w:tbl>")
        }
    }

    private fun tableRowXml(
        cells: List<List<RichInline>>,
        columnCount: Int,
        cellWidthPercent: Int,
        sizeHalfPoints: Int,
        color: String,
        header: Boolean,
        alternate: Boolean
    ): String = buildString {
        append("<w:tr>")
        if (header) append("<w:trPr><w:tblHeader/></w:trPr>")
        repeat(columnCount) { index ->
            val cell = cells.getOrNull(index).orEmpty()
            append("<w:tc><w:tcPr>")
            append("<w:tcW w:w=\"$cellWidthPercent\" w:type=\"pct\"/>")
            when {
                header -> append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"EEF1FA\"/>")
                alternate -> append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F7F8FB\"/>")
            }
            append("</w:tcPr>")
            append("<w:p><w:pPr><w:spacing w:before=\"0\" w:after=\"0\"/></w:pPr>")
            append(
                inlineXml(
                    inlines = cell,
                    sizeHalfPoints = sizeHalfPoints,
                    color = color
                )
            )
            append("</w:p></w:tc>")
        }
        append("</w:tr>")
    }

    private fun StringBuilder.appendRunText(text: String) {
        val normalized = sanitizeXmlText(text)
        val buffer = StringBuilder()
        fun flush() {
            if (buffer.isNotEmpty()) {
                append("<w:t xml:space=\"preserve\">")
                append(escapeXml(buffer.toString()))
                append("</w:t>")
                buffer.clear()
            }
        }
        normalized.forEach { character ->
            when (character) {
                '\n' -> {
                    flush()
                    append("<w:br/>")
                }
                '\t' -> {
                    flush()
                    append("<w:tab/>")
                }
                else -> buffer.append(character)
            }
        }
        flush()
    }

    private fun sanitizeXmlText(value: String): String {
        val normalized = value.replace("\r\n", "\n").replace('\r', '\n')
        return buildString(normalized.length) {
            normalized.forEach { character ->
                val keep = character == '\n' || character == '\t' ||
                    (character >= ' ' && character != '\u007F')
                if (keep) append(character)
            }
        }
    }

    private fun escapeXml(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(character)
            }
        }
    }

    private val CONTENT_TYPES = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Default Extension="jpeg" ContentType="image/jpeg"/>
          <Default Extension="jpg" ContentType="image/jpeg"/>
          <Default Extension="png" ContentType="image/png"/>
          <Default Extension="gif" ContentType="image/gif"/>
          <Default Extension="webp" ContentType="image/webp"/>
          <Default Extension="bmp" ContentType="image/bmp"/>
          <Override PartName="/word/document.xml"
            ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
        </Types>
    """.trimIndent()

    private val ROOT_RELS = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1"
            Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
            Target="word/document.xml"/>
        </Relationships>
    """.trimIndent()
}
