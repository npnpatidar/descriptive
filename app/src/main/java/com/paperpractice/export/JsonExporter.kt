package com.paperpractice.export

import com.paperpractice.domain.CategoryContent
import com.paperpractice.domain.ImageContent
import com.paperpractice.domain.PaperContent
import java.io.OutputStream
import java.util.Base64
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.put

/** Writes the same schema that [com.paperpractice.domain.PaperJsonParser] reads. */
object JsonExporter {

    private val prettyJson = Json { prettyPrint = true }

    /** Kept for tests and callers that need the in-memory text. */
    fun build(content: PaperContent, includeAnswers: Boolean): String {
        val root = buildObject(content, includeAnswers)
        return prettyJson.encodeToString(JsonElement.serializer(), root) + "\n"
    }

    /**
     * Streams the same bytes as [build] straight to [output], so a JSON export
     * with large embedded images never has to be held as a full String and a
     * second byte array at the same time. The caller owns the stream.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun write(output: OutputStream, content: PaperContent, includeAnswers: Boolean) {
        val root = buildObject(content, includeAnswers)
        prettyJson.encodeToStream(JsonElement.serializer(), root, output)
        output.write('\n'.code)
        output.flush()
    }

    fun buildObject(content: PaperContent, includeAnswers: Boolean): JsonObject {
        val exportContent = if (includeAnswers) content else {
            ExportContentBuilder.withoutAnswers(content)
        }

        return buildJsonObject {
            put("paperName", exportContent.paperName)
            put(
                "categories",
                buildJsonArray {
                    exportContent.categories.forEach { category -> add(categoryJson(category)) }
                }
            )
        }
    }

    private fun categoryJson(category: CategoryContent): JsonObject = buildJsonObject {
        put("categoryName", category.categoryName)
        put(
            "questions",
            buildJsonArray {
                category.questions.forEach { question ->
                    add(
                        buildJsonObject {
                            put("questionText", question.questionText)
                            put("answerText", question.answerText)
                            if (question.questionImages.isNotEmpty()) {
                                put(
                                    "questionImages",
                                    buildJsonArray {
                                        question.questionImages.forEach { image ->
                                            add(imageJson(image))
                                        }
                                    }
                                )
                            }
                            if (question.answerImages.isNotEmpty()) {
                                put(
                                    "answerImages",
                                    buildJsonArray {
                                        question.answerImages.forEach { image ->
                                            add(imageJson(image))
                                        }
                                    }
                                )
                            }
                        }
                    )
                }
            }
        )
        if (category.categories.isNotEmpty()) {
            put(
                "categories",
                buildJsonArray {
                    category.categories.forEach { child -> add(categoryJson(child)) }
                }
            )
        }
    }

    private fun imageJson(image: ImageContent): JsonObject = buildJsonObject {
        put("mimeType", image.mimeType)
        if (image.width > 0) put("width", image.width)
        if (image.height > 0) put("height", image.height)
        put("data", Base64.getEncoder().encodeToString(image.bytes))
    }
}
