package com.paperpractice.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paperpractice.data.AppDatabase
import com.paperpractice.data.CategoryDetail
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
                    requestedCounts = detail.categories.associate { category ->
                        category.id to if (category.questions.isEmpty()) "0" else "1"
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
        val category = selectedPaper?.categories?.firstOrNull { it.id == categoryId } ?: return
        if (category.questions.isEmpty()) return
        selectedCategoryIds = if (categoryId in selectedCategoryIds) {
            selectedCategoryIds - categoryId
        } else {
            selectedCategoryIds + categoryId
        }
    }

    fun selectAllCategories() {
        selectedCategoryIds = selectedPaper
            ?.categories
            .orEmpty()
            .filter { it.questions.isNotEmpty() }
            .map { it.id }
            .toSet()
    }

    fun clearCategorySelection() {
        selectedCategoryIds = emptySet()
    }

    fun updateCount(categoryId: String, value: String) {
        if (selectedPaper?.categories?.any { it.id == categoryId } != true) return
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

        val selectedCategories = paper.categories.filter { it.id in selectedCategoryIds }
        val errors = linkedMapOf<String, String>()
        var total = 0

        selectedCategories.forEach { category ->
            val raw = requestedCounts[category.id].orEmpty()
            val parsed = raw.trim().toIntOrNull()
            when {
                raw.isBlank() -> errors[category.id] = "Enter a count."
                parsed == null -> errors[category.id] = "Use a whole number."
                parsed < 0 -> errors[category.id] = "Count cannot be negative."
                parsed > category.questions.size -> {
                    errors[category.id] = "Maximum is ${category.questions.size}."
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

        val counts = paper.categories
            .filter { it.id in selectedCategoryIds }
            .associate { category ->
                category.id to (requestedCounts[category.id]?.trim()?.toIntOrNull() ?: 0)
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

        pendingExport = PendingExport(content, format, includeAnswers, fileName)
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
                                stream
                            )

                            ExportFormat.PDF -> PdfExporter.write(
                                pending.content,
                                pending.includeAnswers,
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
        val fileName: String
    )
}
