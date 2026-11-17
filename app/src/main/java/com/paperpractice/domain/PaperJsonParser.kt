package com.paperpractice.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.Normalizer
import java.util.Base64
import java.util.UUID

/**
 * Parses and validates the import schema without trusting the input.
 *
 * Categories can contain subcategories recursively. Error messages point at
 * the exact field (for example
 * `categories[2].categories[0].questions[1].answerText`) because "invalid JSON"
 * alone is not actionable when a paper has hundreds of questions.
 */
object PaperJsonParser {

    private const val MAX_IMAGE_BYTES = 1_500_000
    private const val MAX_IMAGE_DIMENSION = 100_000
    private const val MAX_CATEGORY_DEPTH = 64
    private const val MAX_TAGS_PER_QUESTION = 20
    private const val MAX_TAG_LENGTH = 40
    private val imageMimePattern = Regex("image/(png|jpe?g|gif|webp|bmp)")

    fun parse(source: String): PaperContent {
        val text = source.removePrefix("\uFEFF").trim()
        if (text.isEmpty()) {
            throw PaperFormatException("The selected file is empty.")
        }

        // json parsing is recursive: a pathologically nested document can throw
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
                friendlyParseError(e.message)
            }
            throw PaperFormatException("The file is not valid JSON. $detail")
        }

        val rootObject = root as? JsonObject
            ?: throw PaperFormatException("The top level of the file must be a JSON object.")

        return parseObject(rootObject)
    }

    /** Parses one already-decoded paper object, used by backup restore. */
    fun parseObject(rootObject: JsonObject): PaperContent {
        val paperName = requiredString(
            rootObject,
            "paperName",
            "paperName",
            allowBlank = false
        ).trim()

        val categoriesElement = rootObject["categories"]
            ?: throw PaperFormatException("\"categories\" is required and must be an array.")
        val categoriesArray = categoriesElement as? JsonArray
            ?: throw PaperFormatException("\"categories\" must be an array.")

        val categories = categoriesArray.mapIndexed { categoryIndex, element ->
            parseCategory(element, "categories[$categoryIndex]", depth = 0)
        }

        return PaperContent(paperName, categories)
    }

    private fun parseCategory(
        element: JsonElement,
        path: String,
        depth: Int
    ): CategoryContent {
        // Top-level categories are depth 0, so exactly MAX_CATEGORY_DEPTH
        // levels are allowed and the (MAX + 1)-th is rejected.
        if (depth >= MAX_CATEGORY_DEPTH) {
            throw PaperFormatException(
                "$path is nested more than $MAX_CATEGORY_DEPTH levels deep."
            )
        }
        val categoryObject = element as? JsonObject
            ?: throw PaperFormatException("$path must be a JSON object.")

        val categoryName = requiredString(
            categoryObject,
            "categoryName",
            "$path.categoryName",
            allowBlank = false
        ).trim()

        val questionsElement = categoryObject["questions"]
        val childrenElement = categoryObject["categories"]
        if (questionsElement == null && childrenElement == null) {
            throw PaperFormatException(
                "$path.questions or $path.categories is required and must be an array."
            )
        }

        val questionsArray = when (questionsElement) {
            null -> emptyList()
            is JsonArray -> questionsElement
            else -> throw PaperFormatException("$path.questions must be an array.")
        }
        val questions = questionsArray.mapIndexed { questionIndex, questionElement ->
            parseQuestion(questionElement, "$path.questions[$questionIndex]")
        }

        val childrenArray = when (childrenElement) {
            null -> emptyList()
            is JsonArray -> childrenElement
            else -> throw PaperFormatException("$path.categories must be an array.")
        }
        val children = childrenArray.mapIndexed { childIndex, childElement ->
            parseCategory(childElement, "$path.categories[$childIndex]", depth + 1)
        }

        return CategoryContent(categoryName, questions, children)
    }

    private fun parseQuestion(
        element: JsonElement,
        questionPath: String
    ): QuestionContent {
        val questionObject = element as? JsonObject
            ?: throw PaperFormatException("$questionPath must be a JSON object.")

        val questionText = requiredString(
            questionObject,
            "questionText",
            "$questionPath.questionText",
            allowBlank = false
        )
        // An empty answer is allowed: exporting with "include answers"
        // switched off produces a valid, re-importable file.
        val answerText = requiredString(
            questionObject,
            "answerText",
            "$questionPath.answerText",
            allowBlank = true
        )

        return QuestionContent(
            questionText = questionText,
            answerText = answerText,
            questionImages = parseImages(
                questionObject,
                "questionImages",
                "$questionPath.questionImages"
            ),
            answerImages = parseImages(
                questionObject,
                "answerImages",
                "$questionPath.answerImages"
            ),
            tags = parseTags(questionObject, "$questionPath.tags")
        )
    }

    /**
     * Optional per-question display names. Entries are trimmed, validated, and
     * deduplicated by their NFC + lowercase form while the first spelling seen
     * is kept for display.
     */
    private fun parseTags(obj: JsonObject, path: String): List<String> {
        val element = obj["tags"] ?: return emptyList()
        val array = element as? JsonArray
            ?: throw PaperFormatException("$path must be an array.")

        val result = mutableListOf<String>()
        val seen = hashSetOf<String>()
        array.forEachIndexed { index, tagElement ->
            val itemPath = "$path[$index]"
            val primitive = tagElement as? JsonPrimitive
                ?: throw PaperFormatException("$itemPath must be a string.")
            if (!primitive.isString) {
                throw PaperFormatException("$itemPath must be a string.")
            }
            val value = primitive.content.trim()
            if (value.isEmpty()) {
                throw PaperFormatException("$itemPath must not be empty.")
            }
            if (value.length > MAX_TAG_LENGTH) {
                throw PaperFormatException(
                    "$itemPath must not be longer than $MAX_TAG_LENGTH characters."
                )
            }
            if (value.any { it == '\r' || it == '\n' }) {
                throw PaperFormatException("$itemPath must not contain line breaks.")
            }
            if (seen.add(normalizeTag(value))) {
                result.add(value)
            }
        }
        if (result.size > MAX_TAGS_PER_QUESTION) {
            throw PaperFormatException(
                "$path must contain at most $MAX_TAGS_PER_QUESTION entries."
            )
        }
        return result
    }

    private fun normalizeTag(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFC).lowercase()

    private fun parseImages(
        obj: JsonObject,
        key: String,
        path: String
    ): List<ImageContent> {
        val element = obj[key] ?: return emptyList()
        val array = element as? JsonArray
            ?: throw PaperFormatException("$path must be an array.")
        return array.mapIndexed { index, imageElement ->
            val itemPath = "$path[$index]"
            val imageObject = imageElement as? JsonObject
                ?: throw PaperFormatException("$itemPath must be a JSON object.")
            val mimeType = requiredString(
                imageObject,
                "mimeType",
                "$itemPath.mimeType",
                allowBlank = false
            ).lowercase()
            if (!imageMimePattern.matches(mimeType)) {
                throw PaperFormatException(
                    "$itemPath.mimeType must be one of image/png, image/jpeg, " +
                        "image/jpg, image/gif, image/webp, or image/bmp."
                )
            }
            val encoded = requiredString(
                imageObject,
                "data",
                "$itemPath.data",
                allowBlank = false
            )
            val bytes = try {
                Base64.getDecoder().decode(encoded)
            } catch (e: IllegalArgumentException) {
                throw PaperFormatException("$itemPath.data is not valid base64.")
            }
            if (bytes.isEmpty()) {
                throw PaperFormatException("$itemPath.data is empty.")
            }
            if (bytes.size > MAX_IMAGE_BYTES) {
                throw PaperFormatException(
                    "$itemPath.data is larger than 1.5 MB after decoding."
                )
            }
            val declaredWidth = optionalInt(imageObject, "width", itemPath)
            val declaredHeight = optionalInt(imageObject, "height", itemPath)
            val inferred = ImageMetadata.dimensions(bytes)
            val inferredWidth = inferred?.first?.takeIf { it in 1..MAX_IMAGE_DIMENSION }
            val inferredHeight = inferred?.second?.takeIf { it in 1..MAX_IMAGE_DIMENSION }

            // One consistent rule: the image header is authoritative (it also
            // reflects any EXIF orientation swap). Only when no usable header
            // size exists do we fall back to declared values, and only when
            // BOTH are present. Unknown sizes are stored as 0, which every
            // consumer already treats as "dimensions unavailable".
            val width: Int
            val height: Int
            if (inferredWidth != null && inferredHeight != null) {
                width = inferredWidth
                height = inferredHeight
            } else if (declaredWidth > 0 && declaredHeight > 0) {
                width = declaredWidth
                height = declaredHeight
            } else {
                width = 0
                height = 0
            }

            ImageContent(
                id = UUID.randomUUID().toString(),
                mimeType = mimeType,
                width = width,
                height = height,
                bytes = bytes
            )
        }
    }

    private fun optionalInt(obj: JsonObject, key: String, path: String): Int {
        val element = obj[key] ?: return 0
        val primitive = element as? JsonPrimitive
            ?: throw PaperFormatException("$path.$key must be a number.")
        val value = primitive.content.toIntOrNull()
            ?: throw PaperFormatException("$path.$key must be a number.")
        if (value < 0 || value > MAX_IMAGE_DIMENSION) {
            throw PaperFormatException("$path.$key must be between 0 and 100000.")
        }
        return value
    }

    private fun requiredString(
        obj: JsonObject,
        key: String,
        path: String,
        allowBlank: Boolean
    ): String {
        val element = obj[key]
            ?: throw PaperFormatException("$path is required and must be a string.")
        val primitive = element as? JsonPrimitive
            ?: throw PaperFormatException("$path must be a string.")
        if (!primitive.isString) {
            throw PaperFormatException("$path must be a string.")
        }
        val value = primitive.content
        if (!allowBlank && value.isBlank()) {
            throw PaperFormatException("$path must not be empty.")
        }
        return value
    }

    private fun friendlyParseError(message: String?): String {
        val detail = message
            ?.lineSequence()
            ?.firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: return "Check for missing commas, quotes, or brackets."
        return detail
    }
}
