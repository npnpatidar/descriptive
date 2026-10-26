package com.paperpractice.export

import com.paperpractice.domain.PaperContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Writes the same schema that [com.paperpractice.domain.PaperJsonParser] reads. */
object JsonExporter {

    private val prettyJson = Json { prettyPrint = true }

    fun build(content: PaperContent, includeAnswers: Boolean): String {
        val exportContent = if (includeAnswers) content else {
            ExportContentBuilder.withoutAnswers(content)
        }

        val root = buildJsonObject {
            put("paperName", exportContent.paperName)
            put(
                "categories",
                buildJsonArray {
                    exportContent.categories.forEach { category ->
                        add(
                            buildJsonObject {
                                put("categoryName", category.categoryName)
                                put(
                                    "questions",
                                    buildJsonArray {
                                        category.questions.forEach { question ->
                                            add(
                                                buildJsonObject {
                                                    put("questionText", question.questionText)
                                                    put("answerText", question.answerText)
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            )
        }

        return prettyJson.encodeToString(JsonElement.serializer(), root) + "\n"
    }
}
