package com.paperpractice.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parses and validates the import schema without trusting the input.
 *
 * Categories can contain subcategories recursively. Error messages point at
 * the exact field (for example
 * `categories[2].categories[0].questions[1].answerText`) because "invalid JSON"
 * alone is not actionable when a paper has hundreds of questions.
 */
object PaperJsonParser {

    fun parse(source: String): PaperContent {
        val text = source.removePrefix("\uFEFF").trim()
        if (text.isEmpty()) {
            throw PaperFormatException("The selected file is empty.")
        }

        val root = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw PaperFormatException(
                "The file is not valid JSON. ${friendlyParseError(e.message)}"
            )
        }

        val rootObject = root as? JsonObject
            ?: throw PaperFormatException("The top level of the file must be a JSON object.")

        val paperName = requiredString(
            rootObject,
            "paperName",
            "paperName",
            allowBlank = false
        )

        val categoriesElement = rootObject["categories"]
            ?: throw PaperFormatException("\"categories\" is required and must be an array.")
        val categoriesArray = categoriesElement as? JsonArray
            ?: throw PaperFormatException("\"categories\" must be an array.")

        val categories = categoriesArray.mapIndexed { categoryIndex, element ->
            parseCategory(element, "categories[$categoryIndex]")
        }

        return PaperContent(paperName, categories)
    }

    private fun parseCategory(element: JsonElement, path: String): CategoryContent {
        val categoryObject = element as? JsonObject
            ?: throw PaperFormatException("$path must be a JSON object.")

        val categoryName = requiredString(
            categoryObject,
            "categoryName",
            "$path.categoryName",
            allowBlank = false
        )

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
            parseCategory(childElement, "$path.categories[$childIndex]")
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

        return QuestionContent(questionText, answerText)
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
