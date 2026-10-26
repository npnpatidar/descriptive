package com.paperpractice.export

import com.paperpractice.domain.PaperContent
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A small, dependency-free OOXML writer. Producing WordprocessingML directly
 * keeps the app offline and avoids shipping a full document library for what
 * is ultimately a sequence of styled paragraphs. Word, LibreOffice, and Google
 * Docs all paginate the result and keep long questions intact.
 */
object DocxExporter {

    fun write(content: PaperContent, includeAnswers: Boolean, output: OutputStream) {
        val zip = ZipOutputStream(output)
        zip.putNextEntry(ZipEntry("[Content_Types].xml"))
        zip.write(CONTENT_TYPES.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("_rels/.rels"))
        zip.write(ROOT_RELS.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("word/document.xml"))
        zip.write(documentXml(content, includeAnswers).toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        zip.finish()
        zip.flush()
    }

    private fun documentXml(content: PaperContent, includeAnswers: Boolean): String {
        val totalLabel = if (content.questionCount == 1) "1 question" else {
            "${content.questionCount} questions"
        }
        val categoryLabel = if (content.categories.size == 1) {
            "1 category"
        } else {
            "${content.categories.size} categories"
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
        content.categories.forEach { category ->
            body.append(
                paragraph(
                    runs = listOf(
                        Run(
                            text = category.categoryName,
                            bold = true,
                            sizeHalfPoints = 28,
                            color = "3D4D87"
                        )
                    ),
                    spacingBefore = 320,
                    spacingAfter = 120,
                    keepNext = true
                )
            )

            category.questions.forEach { question ->
                body.append(
                    paragraph(
                        runs = listOf(
                            Run(
                                text = "$number. ",
                                bold = true,
                                sizeHalfPoints = 23,
                                color = "3D4D87"
                            ),
                            Run(
                                text = question.questionText,
                                sizeHalfPoints = 23,
                                color = "1E2433"
                            )
                        ),
                        spacingAfter = if (includeAnswers) 100 else 220
                    )
                )

                if (includeAnswers) {
                    body.append(
                        paragraph(
                            runs = listOf(
                                Run(
                                    text = "Answer: ",
                                    bold = true,
                                    sizeHalfPoints = 20,
                                    color = "646B7C"
                                ),
                                Run(
                                    text = question.answerText.ifEmpty { "(No answer provided)" },
                                    sizeHalfPoints = 20,
                                    color = "3B4152",
                                    italic = question.answerText.isEmpty()
                                )
                            ),
                            spacingAfter = 220,
                            indentLeft = 360
                        )
                    )
                }
                number++
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
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
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

    private data class Run(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val sizeHalfPoints: Int = 22,
        val color: String = "1E2433"
    )

    private fun paragraph(
        runs: List<Run>,
        spacingBefore: Int = 0,
        spacingAfter: Int = 120,
        indentLeft: Int = 0,
        keepNext: Boolean = false,
        borderBottom: Boolean = false
    ): String = buildString {
        append("<w:p><w:pPr>")
        append("<w:spacing w:before=\"$spacingBefore\" w:after=\"$spacingAfter\"/>")
        if (keepNext) append("<w:keepNext/>")
        if (indentLeft > 0) append("<w:ind w:left=\"$indentLeft\"/>")
        if (borderBottom) {
            append(
                "<w:pBdr><w:bottom w:val=\"single\" w:sz=\"8\" w:space=\"4\" " +
                    "w:color=\"D8D3C6\"/></w:pBdr>"
            )
        }
        append("</w:pPr>")

        runs.forEach { run ->
            append("<w:r><w:rPr>")
            append("<w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:cs=\"Calibri\"/>")
            if (run.bold) append("<w:b/>")
            if (run.italic) append("<w:i/>")
            append("<w:color w:val=\"${run.color}\"/>")
            append("<w:sz w:val=\"${run.sizeHalfPoints}\"/>")
            append("<w:szCs w:val=\"${run.sizeHalfPoints}\"/>")
            append("</w:rPr>")
            appendRunText(run.text)
            append("</w:r>")
        }
        append("</w:p>")
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
