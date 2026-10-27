package com.paperpractice.export

import com.paperpractice.domain.PaperContent
import com.paperpractice.domain.PaperFormatException
import com.paperpractice.domain.PaperJsonParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A single-file backup that preserves every paper and its complete category
 * tree. Restore accepts either this wrapper or one plain paper JSON file.
 */
object PaperBackup {

    const val FORMAT = "paper-practice-backup"
    const val VERSION = 1

    private val prettyJson = Json { prettyPrint = true }

    fun build(papers: List<PaperContent>): String {
        val root = buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("exportedAt", System.currentTimeMillis())
            put(
                "papers",
                buildJsonArray {
                    papers.forEach { paper ->
                        add(JsonExporter.buildObject(paper, includeAnswers = true))
                    }
                }
            )
        }
        return prettyJson.encodeToString(JsonElement.serializer(), root) + "\n"
    }

    fun parse(source: String): List<PaperContent> {
        val text = source.removePrefix("\uFEFF").trim()
        if (text.isEmpty()) {
            throw PaperFormatException("The selected backup is empty.")
        }

        val root = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw PaperFormatException(
                "The backup is not valid JSON. " +
                    (e.message?.lineSequence()?.firstOrNull()?.trim()
                        ?: "Check the file contents.")
            )
        }

        val rootObject = root as? JsonObject
            ?: throw PaperFormatException("The backup must be a JSON object.")

        val format = rootObject["format"]
        if (format != null && format.toString().trim('"') != FORMAT) {
            throw PaperFormatException("This is not a Paper Practice backup.")
        }

        val version = rootObject["version"]
        if (version != null) {
            val parsedVersion = version.toString().trim('"').toIntOrNull()
                ?: throw PaperFormatException("The backup version is not valid.")
            if (parsedVersion > VERSION) {
                throw PaperFormatException(
                    "This backup was created by a newer version of Paper Practice " +
                        "and cannot be restored here."
                )
            }
            if (parsedVersion < 1) {
                throw PaperFormatException("The backup version is not supported.")
            }
        }

        val papersElement = rootObject["papers"]
        if (papersElement == null) {
            // Also accept a normal paper export so "Restore" can act as a
            // second import entry point.
            if (rootObject["paperName"] != null) {
                return listOf(PaperJsonParser.parseObject(rootObject))
            }
            throw PaperFormatException(
                "The backup does not contain a \"papers\" array."
            )
        }

        val papersArray = papersElement as? JsonArray
            ?: throw PaperFormatException("\"papers\" must be an array.")
        if (papersArray.isEmpty()) {
            throw PaperFormatException("The backup contains no papers.")
        }

        return papersArray.mapIndexed { index, element ->
            val paperObject = element as? JsonObject
                ?: throw PaperFormatException("papers[$index] must be a JSON object.")
            try {
                PaperJsonParser.parseObject(paperObject)
            } catch (e: PaperFormatException) {
                throw PaperFormatException("papers[$index]: ${e.message}")
            }
        }
    }
}
