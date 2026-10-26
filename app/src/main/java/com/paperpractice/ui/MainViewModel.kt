package com.paperpractice.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paperpractice.data.AppDatabase
import com.paperpractice.data.PaperDetail
import com.paperpractice.data.PaperRepository
import com.paperpractice.data.PaperSummary
import com.paperpractice.domain.PaperFormatException
import com.paperpractice.domain.PaperJsonParser
import com.paperpractice.domain.TestGenerator
import com.paperpractice.domain.TestSession
import com.paperpractice.export.CreateDocumentRequest
import com.paperpractice.export.DocxExporter
import com.paperpractice.export.ExportContentBuilder
import com.paperpractice.export.ExportFeedback
import com.paperpractice.export.ExportFormat
import com.paperpractice.export.ExportScope
import com.paperpractice.export.HtmlExporter
import com.paperpractice.export.JsonExporter
import com.paperpractice.export.PdfExporter
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ConfigSummary(
    val selectedCategoryCount: Int,
    val totalQuestions: Int,
    val countErrors: Map<String, String>,
    val canGenerate: Boolean,
    val hint: String?
)

data class ExportUiState(
    val isExporting: Boolean = false,
    val feedback: ExportFeedback? = null
)

sealed interface CreatePaperResult {
    data class Success(val paperId: String, val paperName: String) : CreatePaperResult
    data class Failure(val message: String) : CreatePaperResult
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PaperRepository(
        AppDatabase.getInstance(application).paperDao()
    )

    val papers: StateFlow<List<PaperSummary>> = repository.observePapers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var selectedPaper by mutableStateOf<PaperDetail?>(null)
        private set

    var isPaperLoading by mutableStateOf(false)
        private set

    var paperLoadError by mutableStateOf<String?>(null)
        private set

    var selectedCategoryIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var requestedCounts by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var session by mutableStateOf<TestSession?>(null)
        private set

    var isImporting by mutableStateOf(false)
        private set

    var importError by mutableStateOf<String?>(null)
        private set

    var homeMessage by mutableStateOf<String?>(null)
        private set

    var configError by mutableStateOf<String?>(null)
        private set

    var exportState by mutableStateOf(ExportUiState())
        private set

    private var paperLoadJob: Job? = null
    private var paperLoadGeneration = 0
    private var pendingExport: PendingExport? = null

    // ---------------------------------------------------------------------
    // Home / import
    // ---------------------------------------------------------------------

    fun importPaper(uri: Uri) {
        if (isImporting) return
        isImporting = true
        importError = null
        homeMessage = null

        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val text = resolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    } ?: throw IOException("The file could not be opened.")
                    PaperJsonParser.parse(text)
                }

                val imported = withContext(Dispatchers.IO) {
                    repository.importPaper(content)
                }

                val questionLabel = if (imported.questionCount == 1) "1 question" else {
                    "${imported.questionCount} questions"
                }
                homeMessage = "Imported \"${imported.name}\" with $questionLabel."
            } catch (e: PaperFormatException) {
                importError = e.message ?: "The file is not a valid paper."
            } catch (e: SecurityException) {
                importError = "The app does not have permission to read that file."
            } catch (e: Exception) {
                importError = "Could not import the file. " +
                    (e.message?.takeIf { it.isNotBlank() } ?: "An unknown error occurred.")
            } finally {
                isImporting = false
            }
        }
    }

    fun dismissImportError() {
        importError = null
    }

    fun clearHomeMessage() {
        homeMessage = null
    }

    fun deletePaper(paperId: String, paperName: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.deletePaper(paperId) }
                if (selectedPaper?.id == paperId) {
                    selectedPaper = null
                    selectedCategoryIds = emptySet()
                    requestedCounts = emptyMap()
                    session = null
                }
                homeMessage = "Deleted \"$paperName\"."
            } catch (e: Exception) {
                homeMessage = "Could not delete \"$paperName\". " +
                    (e.message ?: "Please try again.")
            }
        }
    }

    suspend fun createPaper(name: String): CreatePaperResult {
        val normalized = name.trim()
        if (normalized.isEmpty()) {
            return CreatePaperResult.Failure("Enter a paper name.")
        }

        return try {
            val paper = withContext(Dispatchers.IO) {
                repository.createPaper(normalized)
            }
            CreatePaperResult.Success(paper.id, paper.name)
        } catch (e: Exception) {
            CreatePaperResult.Failure(
                "Could not create the paper. " +
                    (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
            )
        }
    }

    suspend fun renamePaper(paperId: String, name: String): String? {
        val normalized = name.trim()
        if (normalized.isEmpty()) return "Enter a paper name."

        return try {
            withContext(Dispatchers.IO) {
                repository.renamePaper(paperId, normalized)
            }
            if (selectedPaper?.id == paperId) {
                selectedPaper = selectedPaper?.copy(name = normalized)
            }
            session = session?.let { current ->
                if (current.paperId == paperId) {
                    current.copy(paperName = normalized)
                } else {
                    current
                }
            }
            null
        } catch (e: Exception) {
            "Could not rename the paper. " +
                (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
        }
    }

    suspend fun addCategory(
        paperId: String,
        name: String,
        parentId: String? = null
    ): String? {
        val normalized = name.trim()
        if (normalized.isEmpty()) return "Enter a category name."

        return try {
            withContext(Dispatchers.IO) {
                repository.addCategory(paperId, normalized, parentId)
            }
            refreshSelectedPaper(paperId)
            null
        } catch (e: Exception) {
            "Could not add the category. " +
                (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
        }
    }

    suspend fun renameCategory(categoryId: String, name: String): String? {
        val normalized = name.trim()
        if (normalized.isEmpty()) return "Enter a category name."
        val paper = selectedPaper ?: return "No paper is loaded."
        if (paper.findCategory(categoryId) == null) {
            return "This category could not be found."
        }

        return try {
            withContext(Dispatchers.IO) {
                repository.renameCategory(categoryId, normalized)
            }
            refreshSelectedPaper(paper.id)
            refreshSessionCategoryNames()
            null
        } catch (e: Exception) {
            "Could not rename the category. " +
                (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
        }
    }

    /**
     * Removes a category, all nested subcategories, and every question inside
     * them. A generated test containing those questions is trimmed as well so
     * the practice screen cannot show a question that no longer exists.
     */
    suspend fun deleteCategory(categoryId: String): String? {
        val paper = selectedPaper ?: return "No paper is loaded."
        val category = paper.findCategory(categoryId)
            ?: return "This category could not be found."
        val deletedIds = category.subtreeCategoryIds()

        return try {
            withContext(Dispatchers.IO) {
                repository.deleteCategory(categoryId)
            }

            selectedPaper = paper.removeCategories(deletedIds)
            selectedCategoryIds = selectedCategoryIds - deletedIds
            requestedCounts = requestedCounts.filterKeys { it !in deletedIds }
            session = session?.let { current ->
                if (current.questions.none { it.categoryId in deletedIds }) {
                    current
                } else {
                    val remaining = current.questions
                        .filterNot { it.categoryId in deletedIds }
                        .mapIndexed { index, question -> question.copy(number = index + 1) }
                    if (remaining.isEmpty()) null else current.copy(questions = remaining)
                }
            }
            null
        } catch (e: Exception) {
            "Could not delete \"${category.name}\". " +
                (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
        }
    }

    // ---------------------------------------------------------------------
    // Paper / test configuration
    // ---------------------------------------------------------------------

    fun loadPaper(paperId: String) {
        if (selectedPaper?.id == paperId) {
            paperLoadError = null
            return
        }

        paperLoadJob?.cancel()
        val generation = ++paperLoadGeneration
        isPaperLoading = true
        paperLoadError = null
        paperLoadJob = viewModelScope.launch {
            try {
                val detail = withContext(Dispatchers.IO) {
                    repository.getPaper(paperId)
                }
                ensureActive()
                if (generation != paperLoadGeneration) return@launch

                if (detail == null) {
                    selectedPaper = null
                    paperLoadError = "This paper could not be found. It may have been deleted."
                } else {
                    selectedPaper = detail
                    selectedCategoryIds = emptySet()
                    requestedCounts = detail.flattenCategories().associate { node ->
                        node.category.id to if (node.category.totalQuestionCount == 0) "0" else "1"
                    }
                    session = null
                    configError = null
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException &&
                    generation == paperLoadGeneration
                ) {
                    paperLoadError = "Could not load this paper. " +
                        (e.message ?: "Please try again.")
                }
            } finally {
                if (generation == paperLoadGeneration) {
                    isPaperLoading = false
                }
            }
        }
    }

    fun toggleCategory(categoryId: String) {
        val paper = selectedPaper ?: return
        val category = paper.findCategory(categoryId) ?: return
        if (category.totalQuestionCount == 0) return

        selectedCategoryIds = if (categoryId in selectedCategoryIds) {
            selectedCategoryIds - categoryId
        } else {
            // A selected category covers its whole subtree, so an ancestor or
            // descendant cannot stay selected at the same time.
            (selectedCategoryIds - paper.selectionConflicts(categoryId)) + categoryId
        }
    }

    fun selectAllCategories() {
        selectedCategoryIds = selectedPaper
            ?.categories
            .orEmpty()
            .filter { it.totalQuestionCount > 0 }
            .map { it.id }
            .toSet()
    }

    fun clearCategorySelection() {
        selectedCategoryIds = emptySet()
    }

    fun updateCount(categoryId: String, value: String) {
        if (selectedPaper?.findCategory(categoryId) == null) return
        requestedCounts = requestedCounts + (categoryId to value)
    }

    fun configSummary(): ConfigSummary {
        val paper = selectedPaper ?: return ConfigSummary(
            selectedCategoryCount = 0,
            totalQuestions = 0,
            countErrors = emptyMap(),
            canGenerate = false,
            hint = "No paper is loaded."
        )

        val selectedCategories = paper.flattenCategories()
            .filter { it.category.id in selectedCategoryIds }
        val errors = linkedMapOf<String, String>()
        var total = 0

        selectedCategories.forEach { node ->
            val category = node.category
            val available = category.totalQuestionCount
            val raw = requestedCounts[category.id].orEmpty()
            val parsed = raw.trim().toIntOrNull()
            when {
                raw.isBlank() -> errors[category.id] = "Enter a count."
                parsed == null -> errors[category.id] = "Use a whole number."
                parsed < 0 -> errors[category.id] = "Count cannot be negative."
                parsed > available -> {
                    errors[category.id] = "Maximum is $available."
                }
                else -> total += parsed
            }
        }

        val hint = when {
            selectedCategories.isEmpty() -> "Select at least one category to continue."
            errors.isNotEmpty() -> "Fix the highlighted question counts."
            total == 0 -> "Add at least one question to generate a test."
            else -> null
        }

        return ConfigSummary(
            selectedCategoryCount = selectedCategories.size,
            totalQuestions = total,
            countErrors = errors,
            canGenerate = selectedCategories.isNotEmpty() && errors.isEmpty() && total > 0,
            hint = hint
        )
    }

    fun startTest(): Boolean {
        val paper = selectedPaper ?: return false
        val summary = configSummary()
        if (!summary.canGenerate) {
            configError = summary.hint ?: "Check the category selection and question counts."
            return false
        }

        val counts = paper.flattenCategories()
            .filter { it.category.id in selectedCategoryIds }
            .associate { node ->
                node.category.id to
                    (requestedCounts[node.category.id]?.trim()?.toIntOrNull() ?: 0)
            }

        return try {
            session = TestGenerator.generate(
                paper = paper,
                selectedCategoryIds = selectedCategoryIds,
                requestedCounts = counts
            )
            configError = null
            true
        } catch (e: IllegalArgumentException) {
            configError = e.message ?: "Could not generate the test."
            false
        }
    }

    fun dismissConfigError() {
        configError = null
    }

    // ---------------------------------------------------------------------
    // View / edit questions
    // ---------------------------------------------------------------------

    /**
     * Saves an edited question, or adds a new one when [questionId] is null.
     * Returns null on success or a user-facing error message.
     */
    suspend fun saveQuestion(
        categoryId: String,
        questionId: String?,
        questionText: String,
        answerText: String
    ): String? {
        val paper = selectedPaper ?: return "No paper is loaded."
        val category = paper.findCategory(categoryId)
            ?: return "This category could not be found."
        if (questionText.isBlank()) {
            return "Question text cannot be empty."
        }

        return try {
            withContext(Dispatchers.IO) {
                if (questionId == null) {
                    repository.addQuestion(categoryId, questionText, answerText)
                } else {
                    repository.updateQuestion(questionId, questionText, answerText)
                }
            }

            refreshSelectedPaper(paper.id)
            if (questionId != null) {
                session = session?.let { current ->
                    current.copy(
                        questions = current.questions.map { question ->
                            if (question.questionId == questionId) {
                                question.copy(
                                    questionText = questionText,
                                    answerText = answerText
                                )
                            } else {
                                question
                            }
                        }
                    )
                }
            }
            null
        } catch (e: Exception) {
            "Could not save the question. " +
                (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
        }
    }

    /**
     * Deletes a question and keeps any generated practice test consistent with
     * the change. Returns null on success or a user-facing error message.
     */
    suspend fun deleteQuestion(questionId: String): String? {
        val paper = selectedPaper ?: return "No paper is loaded."
        val exists = paper.findQuestion(questionId) != null
        if (!exists) return "This question could not be found."

        return try {
            withContext(Dispatchers.IO) {
                repository.deleteQuestion(questionId)
            }

            refreshSelectedPaper(paper.id)
            session = session?.let { current ->
                if (current.questions.none { it.questionId == questionId }) {
                    current
                } else {
                    val remaining = current.questions
                        .filterNot { it.questionId == questionId }
                        .mapIndexed { index, question -> question.copy(number = index + 1) }
                    if (remaining.isEmpty()) null else current.copy(questions = remaining)
                }
            }
            null
        } catch (e: Exception) {
            "Could not delete the question. " +
                (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
        }
    }

    private suspend fun refreshSelectedPaper(paperId: String) {
        val detail = withContext(Dispatchers.IO) { repository.getPaper(paperId) } ?: return
        if (selectedPaper?.id != paperId) return

        selectedPaper = detail
        val currentCategoryIds = detail.allCategoryIds()
        selectedCategoryIds = selectedCategoryIds.filter { it in currentCategoryIds }.toSet()
        requestedCounts = requestedCounts.filterKeys { it in currentCategoryIds } +
            detail.flattenCategories()
                .filter { it.category.id !in requestedCounts }
                .associate { node ->
                    node.category.id to
                        if (node.category.totalQuestionCount == 0) "0" else "1"
                }
    }

    /**
     * A generated test stores the display path of each category. Renaming or
     * moving around the tree must keep those labels in sync with the paper.
     */
    private fun refreshSessionCategoryNames() {
        val current = session ?: return
        val paper = selectedPaper ?: return
        if (current.paperId != paper.id) return
        val names = paper.categoryDisplayNames()
        val paths = paper.flattenCategories().associate { it.category.id to it.path }
        session = current.copy(
            questions = current.questions.map { question ->
                question.copy(
                    categoryName = names[question.categoryId] ?: question.categoryName,
                    categoryPath = paths[question.categoryId] ?: question.categoryPath
                )
            }
        )
    }

    // ---------------------------------------------------------------------
    // Export
    // ---------------------------------------------------------------------

    /**
     * Builds the export payload and returns the details needed to open the
     * system "create document" picker. Returns null when validation fails; the
     * reason is placed in [exportState].
     */
    fun prepareExport(
        scope: ExportScope,
        format: ExportFormat,
        includeAnswers: Boolean,
        answersAtEnd: Boolean,
        rawFileName: String
    ): CreateDocumentRequest? {
        val paper = selectedPaper
        if (paper == null) {
            exportState = ExportUiState(
                feedback = ExportFeedback(true, "No paper is loaded.")
            )
            return null
        }

        val content = when (scope) {
            ExportScope.CURRENT_TEST -> {
                val currentSession = session?.takeIf { it.paperId == paper.id }
                if (currentSession == null) {
                    exportState = ExportUiState(
                        feedback = ExportFeedback(
                            true,
                            "Generate a practice test before exporting the current test."
                        )
                    )
                    return null
                }
                ExportContentBuilder.currentTest(paper, currentSession)
            }

            ExportScope.SELECTED_CATEGORIES -> {
                if (selectedCategoryIds.isEmpty()) {
                    exportState = ExportUiState(
                        feedback = ExportFeedback(
                            true,
                            "Select at least one category on the configuration screen."
                        )
                    )
                    return null
                }
                ExportContentBuilder.selectedCategories(paper, selectedCategoryIds)
            }

            ExportScope.ENTIRE_PAPER -> ExportContentBuilder.entirePaper(paper)
        }

        if (content.questionCount == 0) {
            exportState = ExportUiState(
                feedback = ExportFeedback(
                    true,
                    "There are no questions to export for this scope."
                )
            )
            return null
        }

        val fileName = normalizeFileName(rawFileName, format.extension)
        if (fileName == null) {
            exportState = ExportUiState(
                feedback = ExportFeedback(true, "Enter a valid filename.")
            )
            return null
        }

        pendingExport = PendingExport(
            content = content,
            format = format,
            includeAnswers = includeAnswers,
            answersAtEnd = includeAnswers && answersAtEnd && format.supportsAnswersAtEnd,
            fileName = fileName
        )
        exportState = ExportUiState()
        return CreateDocumentRequest(format.mimeType, fileName)
    }

    fun cancelExport() {
        pendingExport = null
    }

    fun saveExport(uri: Uri) {
        val pending = pendingExport
        if (pending == null) {
            exportState = ExportUiState(
                feedback = ExportFeedback(true, "The export was not prepared. Please try again.")
            )
            return
        }
        pendingExport = null
        exportState = ExportUiState(isExporting = true)

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val output = resolver.openOutputStream(uri, "wt")
                        ?: throw IOException("The destination could not be opened.")
                    output.use { stream ->
                        when (pending.format) {
                            ExportFormat.HTML -> {
                                stream.write(
                                    HtmlExporter.build(pending.content, pending.includeAnswers)
                                        .toByteArray(Charsets.UTF_8)
                                )
                            }

                            ExportFormat.DOCX -> DocxExporter.write(
                                pending.content,
                                pending.includeAnswers,
                                pending.answersAtEnd,
                                stream
                            )

                            ExportFormat.PDF -> PdfExporter.write(
                                pending.content,
                                pending.includeAnswers,
                                pending.answersAtEnd,
                                stream
                            )

                            ExportFormat.JSON -> {
                                stream.write(
                                    JsonExporter.build(pending.content, pending.includeAnswers)
                                        .toByteArray(Charsets.UTF_8)
                                )
                            }
                        }
                        stream.flush()
                    }
                }

                exportState = ExportUiState(
                    feedback = ExportFeedback(
                        false,
                        "Saved \"${pending.fileName}\"."
                    )
                )
            } catch (e: Exception) {
                exportState = ExportUiState(
                    feedback = ExportFeedback(
                        true,
                        "Could not save the file. " +
                            (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
                    )
                )
            }
        }
    }

    fun clearExportFeedback() {
        exportState = exportState.copy(feedback = null)
    }

    fun suggestedFileName(): String {
        val name = selectedPaper?.name ?: "practice_paper"
        return name
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
            .trim()
            .trim('.')
            .ifBlank { "practice_paper" }
    }

    private fun normalizeFileName(raw: String, extension: String): String? {
        var base = raw.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
            .trim()
            .trim('.')
        if (base.isBlank()) return null

        // If the user typed an old extension (or switched format after typing),
        // replace it rather than producing "paper.pdf.docx".
        listOf("html", "docx", "pdf", "json").forEach { known ->
            val suffix = ".$known"
            if (base.endsWith(suffix, ignoreCase = true) && base.length > suffix.length) {
                base = base.dropLast(suffix.length)
            }
        }
        base = base.trim().trim('.')
        if (base.isBlank()) return null

        val suffix = ".$extension"
        if (!base.endsWith(suffix, ignoreCase = true)) {
            base += suffix
        }
        if (base.length > 120) {
            val keep = (120 - suffix.length).coerceAtLeast(1)
            base = base.take(keep).trimEnd('.', ' ') + suffix
        }
        return base
    }

    private data class PendingExport(
        val content: com.paperpractice.domain.PaperContent,
        val format: ExportFormat,
        val includeAnswers: Boolean,
        val answersAtEnd: Boolean,
        val fileName: String
    )
}
