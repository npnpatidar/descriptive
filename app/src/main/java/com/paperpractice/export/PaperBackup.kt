package com.paperpractice.export

import com.paperpractice.domain.PaperContent
import com.paperpractice.domain.PaperFormatException
import com.paperpractice.domain.PaperJsonParser
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.put
import java.io.OutputStream

/**
 * A single-file backup that preserves every paper and its complete category
 * tree. Restore accepts either this wrapper or one plain paper JSON file.
 */
object PaperBackup {

    const val FORMAT = "paper-practice-backup"
    const val VERSION = 1

    private val prettyJson = Json { prettyPrint = true }

    /** Kept for tests and callers that need the in-memory text. */
    fun build(papers: List<PaperContent>): String {
        return prettyJson.encodeToString(JsonElement.serializer(), buildRoot(papers)) + "\n"
    }

    /**
     * Streams the same bytes as [build] straight to [output]. Encoding directly
     * to the stream avoids materialising a second, full-size String and byte
     * array for large backups. The caller owns the stream and must close it.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun write(output: OutputStream, papers: List<PaperContent>) {
        prettyJson.encodeToStream(JsonElement.serializer(), buildRoot(papers), output)
        // encodeToString never emits a trailing newline; build() appends one.
        output.write('\n'.code)
        output.flush()
    }

    private fun buildRoot(papers: List<PaperContent>): JsonObject = buildJsonObject {
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

    fun parse(source: String): List<PaperContent> {
        val text = source.removePrefix("\uFEFF").trim()
        if (text.isEmpty()) {
            throw PaperFormatException("The selected backup is empty.")
        }

        // JSON parsing is recursive: a pathologically nested document can throw
        // a StackOverflowError, which is an Error rather than an Exception.
        val root = try {
            Json.parseToJsonElement(text)
        } catch (e: Throwable) {
            // Only convert expected parse failures; let OOM and other Errors
            // propagate instead of masking them as a bad input file.
            if (e !is StackOverflowError && e !is Exception) throw e
            val detail = if (e is StackOverflowError) {
                "The file is nested too deeply."
            } else {
                e.message?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
                    ?: "Check the file contents."
            }
            throw PaperFormatException("The backup is not valid JSON. $detail")
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
