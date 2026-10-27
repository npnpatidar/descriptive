package com.paperpractice.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paperpractice.data.AppDatabase
import com.paperpractice.data.ImportConflict
import com.paperpractice.data.ImportMode
import com.paperpractice.data.ImportedPaper
import com.paperpractice.data.PaperDetail
import com.paperpractice.data.PaperRepository
import com.paperpractice.data.PaperSummary
import com.paperpractice.data.QuestionDetail
import com.paperpractice.data.SearchHit
import com.paperpractice.domain.ImageContent
import com.paperpractice.domain.PaperContent
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
import com.paperpractice.export.PaperBackup
import com.paperpractice.export.PdfExporter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

data class MergeResult(
    val merged: Int = 0,
    val error: String? = null
)

data class ShareRequest(
    val uri: Uri,
    val mimeType: String,
    val title: String
)

private data class PreparedImage(
    val mimeType: String,
    val width: Int,
    val height: Int,
    val bytes: ByteArray
)

sealed interface CreatePaperResult {
    data class Success(val paperId: String, val paperName: String) : CreatePaperResult
    data class Failure(val message: String) : CreatePaperResult
}

/** A content location selected from global search that should be revealed. */
data class ContentFocus(
    val paperId: String,
    val categoryId: String?,
    val questionId: String?
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PaperRepository(AppDatabase.getInstance(application))

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

    var importConflict by mutableStateOf<ImportConflict?>(null)
        private set

    var searchQuery by mutableStateOf("")
        private set

    var searchResults by mutableStateOf<List<SearchHit>>(emptyList())
        private set

    var isSearching by mutableStateOf(false)
        private set

    var isPreparingBackup by mutableStateOf(false)
        private set

    private var pendingContentFocus by mutableStateOf<ContentFocus?>(null)

    private var paperLoadJob: Job? = null
    private var paperLoadGeneration = 0
    private var pendingExport: PendingExport? = null
    private var pendingImportContents: List<PaperContent>? = null
    private var pendingBackup: PendingBackup? = null
    private var searchJob: Job? = null
    private var searchGeneration = 0

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
                    val text = readText(uri)
                    PaperJsonParser.parse(text)
                }
                prepareImport(listOf(content), fromBackup = false)
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

    fun restoreBackup(uri: Uri) {
        if (isImporting) return
        isImporting = true
        importError = null
        homeMessage = null

        viewModelScope.launch {
            try {
                val papers = withContext(Dispatchers.IO) {
                    PaperBackup.parse(readText(uri))
                }
                prepareImport(papers, fromBackup = true)
            } catch (e: PaperFormatException) {
                importError = e.message ?: "The backup is not valid."
            } catch (e: SecurityException) {
                importError = "The app does not have permission to read that file."
            } catch (e: Exception) {
                importError = "Could not restore the backup. " +
                    (e.message?.takeIf { it.isNotBlank() } ?: "An unknown error occurred.")
            } finally {
                isImporting = false
            }
        }
    }

    private suspend fun prepareImport(contents: List<PaperContent>, fromBackup: Boolean) {
        val conflicts = withContext(Dispatchers.IO) {
            repository.existingPaperNames(contents.map { it.paperName })
        }
        if (conflicts.isEmpty()) {
            val imported = withContext(Dispatchers.IO) {
                repository.importPapers(contents, ImportMode.KEEP_BOTH)
            }
            homeMessage = importSummary(imported, replaced = false)
            return
        }

        pendingImportContents = contents
        importConflict = ImportConflict(
            paperNames = contents.map { it.paperName }
                .filter { name ->
                    conflicts.any { existing -> existing.equals(name, ignoreCase = true) }
                }
                .distinct(),
            isBackup = fromBackup
        )
    }

    fun resolveImportConflict(mode: ImportMode) {
        val contents = pendingImportContents ?: return
        pendingImportContents = null
        importConflict = null
        isImporting = true

        viewModelScope.launch {
            try {
                val selectedId = selectedPaper?.id
                val imported = withContext(Dispatchers.IO) {
                    repository.importPapers(contents, mode)
                }
                if (selectedId != null) {
                    if (imported.any { it.id == selectedId }) {
                        // A merge can add categories/questions to the paper
                        // that is currently open. Refresh it so returning to
                        // that paper never shows stale content.
                        refreshSelectedPaper(selectedId)
                    } else if (mode == ImportMode.REPLACE) {
                        selectedPaper = null
                        selectedCategoryIds = emptySet()
                        requestedCounts = emptyMap()
                        session = null
                    }
                }
                homeMessage = importSummary(
                    imported,
                    replaced = mode == ImportMode.REPLACE
                )
            } catch (e: Exception) {
                importError = "Could not import the paper. " +
                    (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
            } finally {
                isImporting = false
            }
        }
    }

    fun cancelImportConflict() {
        pendingImportContents = null
        importConflict = null
    }

    private fun importSummary(imported: List<ImportedPaper>, replaced: Boolean): String {
        if (imported.isEmpty()) return "Nothing was imported."
        val questions = imported.sumOf { it.questionCount }
        val questionLabel = if (questions == 1) "1 question" else "$questions questions"
        return if (imported.size == 1) {
            val action = if (replaced) "Replaced" else "Imported"
            "$action \"${imported.first().name}\" with $questionLabel."
        } else {
            "Imported ${imported.size} papers with $questionLabel."
        }
    }

    private fun readText(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        val input = resolver.openInputStream(uri)
            ?: throw IOException("The file could not be opened.")
        return input.use { stream ->
            val buffer = ByteArray(8 * 1024)
            val output = ByteArrayOutputStream()
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_IMPORT_BYTES) {
                    throw IOException(
                        "The selected file is too large. Import files must be under 50 MB."
                    )
                }
                output.write(buffer, 0, read)
            }
            output.toString(Charsets.UTF_8.name())
        }
    }

    /**
     * Reads at most [limit] bytes and returns an empty array when the stream is
     * larger. Image pickers must not be allowed to buffer an arbitrarily large
     * file before the size check.
     */
    private fun readAtMost(stream: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) return ByteArray(0)
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    fun dismissImportError() {
        importError = null
    }

    fun clearHomeMessage() {
        homeMessage = null
    }

    // ---------------------------------------------------------------------
    // Global search
    // ---------------------------------------------------------------------

    fun search(query: String) {
        searchQuery = query
        searchJob?.cancel()
        val generation = ++searchGeneration
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            searchResults = emptyList()
            isSearching = false
            return
        }

        isSearching = true
        searchJob = viewModelScope.launch {
            delay(220)
            try {
                val results = withContext(Dispatchers.IO) {
                    repository.search(trimmed)
                }
                if (generation == searchGeneration) {
                    searchResults = results
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException &&
                    generation == searchGeneration
                ) {
                    searchResults = emptyList()
                }
            } finally {
                if (generation == searchGeneration) {
                    isSearching = false
                }
            }
        }
    }

    fun clearSearch() {
        searchGeneration++
        searchJob?.cancel()
        searchJob = null
        searchQuery = ""
        searchResults = emptyList()
        isSearching = false
    }

    /** Remembers the category/question tapped in search until its paper opens. */
    fun requestContentFocus(hit: SearchHit) {
        if (hit.categoryId == null && hit.questionId == null) return
        pendingContentFocus = ContentFocus(
            paperId = hit.paperId,
            categoryId = hit.categoryId,
            questionId = hit.questionId
        )
    }

    fun consumeContentFocus(paperId: String): ContentFocus? {
        val focus = pendingContentFocus ?: return null
        if (focus.paperId != paperId) return null
        pendingContentFocus = null
        return focus
    }

    // ---------------------------------------------------------------------
    // Backup / restore
    // ---------------------------------------------------------------------

    suspend fun prepareBackup(): CreateDocumentRequest? {
        if (isPreparingBackup) return null
        isPreparingBackup = true
        try {
            val contents = try {
                withContext(Dispatchers.IO) {
                    val papers = repository.getAllPapers()
                        .map(ExportContentBuilder::entirePaper)
                    if (papers.isEmpty()) null else PaperBackup.build(papers)
                }
            } catch (e: Exception) {
                homeMessage = "Could not prepare the backup. " +
                    (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
                return null
            }
            if (contents == null) {
                homeMessage = "There are no papers to back up."
                return null
            }

            val fileName = "paper-practice-backup-" +
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json"
            pendingBackup = PendingBackup(
                fileName = fileName,
                contents = contents
            )
            return CreateDocumentRequest("application/json", fileName)
        } finally {
            isPreparingBackup = false
        }
    }

    fun saveBackup(uri: Uri) {
        val backup = pendingBackup ?: return
        pendingBackup = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val output = resolver.openOutputStream(uri, "wt")
                        ?: throw IOException("The destination could not be opened.")
                    output.use { stream ->
                        stream.write(backup.contents.toByteArray(Charsets.UTF_8))
                        stream.flush()
                    }
                }
                homeMessage = "Backup saved as \"${backup.fileName}\"."
            } catch (e: Exception) {
                homeMessage = "Could not save the backup. " +
                    (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
            }
        }
    }

    fun cancelBackup() {
        pendingBackup = null
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
    // Content management
    // ---------------------------------------------------------------------

    suspend fun moveQuestionToCategory(
        questionId: String,
        targetCategoryId: String
    ): String? {
        val paper = selectedPaper ?: return "No paper is loaded."
        if (paper.findCategory(targetCategoryId) == null) {
            return "Select a destination category in this paper."
        }
        return try {
            withContext(Dispatchers.IO) {
                repository.moveQuestion(questionId, targetCategoryId)
            }
            refreshSelectedPaper(paper.id)
            null
        } catch (e: Exception) {
            "Could not move the question. " +
                (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
        }
    }

    suspend fun moveQuestionsToCategory(
        questionIds: Collection<String>,
        targetCategoryId: String
    ): String? {
        val paper = selectedPaper ?: return "No paper is loaded."
        if (questionIds.isEmpty()) return "Select at least one question."
        if (paper.findCategory(targetCategoryId) == null) {
            return "Select a destination category in this paper."
        }
        return try {
            withContext(Dispatchers.IO) {
                repository.moveQuestions(questionIds, targetCategoryId)
            }
            refreshSelectedPaper(paper.id)
            null
        } catch (e: Exception) {
            "Could not move the questions. " +
                (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
        }
    }

    suspend fun reorderQuestion(questionId: String, delta: Int) {
        val paper = selectedPaper ?: return
        try {
            withContext(Dispatchers.IO) {
                repository.reorderQuestion(questionId, delta)
            }
            refreshSelectedPaper(paper.id)
        } catch (_: Exception) {
            // Dragging is best-effort; the next refresh restores the database order.
        }
    }

    suspend fun deleteQuestions(questionIds: Collection<String>): String? {
        val paper = selectedPaper ?: return "No paper is loaded."
        if (questionIds.isEmpty()) return "Select at least one question."
        return try {
            withContext(Dispatchers.IO) {
                repository.deleteQuestions(questionIds)
            }
            refreshSelectedPaper(paper.id)
            null
        } catch (e: Exception) {
            "Could not delete the selected questions. " +
                (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
        }
    }

    suspend fun moveCategoryToPaper(
        categoryId: String,
        targetPaperId: String
    ): String? {
        val paper = selectedPaper ?: return "No paper is loaded."
        val category = paper.findCategory(categoryId)
            ?: return "This category could not be found."
        if (targetPaperId == paper.id) return "Choose a different paper."
        return try {
            withContext(Dispatchers.IO) {
                repository.moveCategory(categoryId, targetPaperId, targetParentId = null)
            }
            selectedCategoryIds = selectedCategoryIds - category.subtreeCategoryIds()
            refreshSelectedPaper(paper.id)
            null
        } catch (e: Exception) {
            "Could not move the category. " +
                (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
        }
    }

    suspend fun moveCategoryToParent(
        categoryId: String,
        targetParentId: String?
    ): String? {
        val paper = selectedPaper ?: return "No paper is loaded."
        val category = paper.findCategory(categoryId)
            ?: return "This category could not be found."
        if (targetParentId == categoryId) return "A category cannot contain itself."
        if (targetParentId != null) {
            val parent = paper.findCategory(targetParentId)
                ?: return "The destination category could not be found."
            if (parent.id in category.subtreeCategoryIds()) {
                return "A category cannot be moved inside one of its subcategories."
            }
        }

        return try {
            withContext(Dispatchers.IO) {
                repository.moveCategory(
                    categoryId = categoryId,
                    targetPaperId = paper.id,
                    targetParentId = targetParentId
                )
            }
            refreshSelectedPaper(paper.id)
            null
        } catch (e: Exception) {
            "Could not move the category. " +
                (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
        }
    }

    suspend fun reorderCategory(categoryId: String, delta: Int) {
        val paper = selectedPaper ?: return
        try {
            withContext(Dispatchers.IO) {
                repository.reorderCategory(categoryId, delta)
            }
            refreshSelectedPaper(paper.id)
        } catch (_: Exception) {
            // Dragging is best-effort; the next refresh restores the database order.
        }
    }

    suspend fun mergeCategories(sourceId: String, targetId: String): String? {
        val paper = selectedPaper ?: return "No paper is loaded."
        if (sourceId == targetId) return "Choose a different category."
        return try {
            withContext(Dispatchers.IO) {
                repository.mergeCategories(sourceId, targetId)
            }
            refreshSelectedPaper(paper.id)
            null
        } catch (e: Exception) {
            "Could not merge the categories. " +
                (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
        }
    }

    suspend fun mergeDuplicateCategories(): MergeResult {
        val paper = selectedPaper ?: return MergeResult(error = "No paper is loaded.")
        return try {
            val merged = withContext(Dispatchers.IO) {
                repository.mergeDuplicateCategories(paper.id)
            }
            refreshSelectedPaper(paper.id)
            MergeResult(merged = merged)
        } catch (e: Exception) {
            MergeResult(
                error = "Could not merge duplicate categories. " +
                    (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
            )
        }
    }

    suspend fun reorderPaper(paperId: String, delta: Int) {
        try {
            withContext(Dispatchers.IO) { repository.reorderPaper(paperId, delta) }
        } catch (_: Exception) {
            // Best-effort drag operation; the list refreshes from the database.
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
     * Decodes, downscales, and compresses a gallery image before it is stored.
     * Keeping a modest max dimension avoids enormous database rows and slow
     * PDF/DOCX rendering while still looking crisp on phone screens and print.
     */
    suspend fun prepareImage(uri: Uri): ImageContent? = withContext(Dispatchers.IO) {
        try {
            val resolver = getApplication<Application>().contentResolver
            val sourceBytes = resolver.openInputStream(uri)?.use { stream ->
                readAtMost(stream, MAX_SOURCE_IMAGE_BYTES)
            } ?: return@withContext null
            if (sourceBytes.isEmpty()) return@withContext null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sampleSize = 1
            while (
                maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >=
                MAX_IMAGE_DIMENSION
            ) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val decoded = BitmapFactory.decodeByteArray(
                sourceBytes,
                0,
                sourceBytes.size,
                decodeOptions
            ) ?: return@withContext null

            val orientation = try {
                ExifInterface(ByteArrayInputStream(sourceBytes)).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
            val oriented = applyExifOrientation(decoded, orientation)
            if (oriented !== decoded) decoded.recycle()

            val largestSide = maxOf(oriented.width, oriented.height)
            val scaled = if (largestSide > MAX_IMAGE_DIMENSION) {
                val ratio = MAX_IMAGE_DIMENSION.toFloat() / largestSide
                oriented.scale(
                    (oriented.width * ratio).toInt().coerceAtLeast(1),
                    (oriented.height * ratio).toInt().coerceAtLeast(1)
                )
            } else {
                oriented
            }

            val stored = encodeImageForStorage(scaled)
            if (scaled !== oriented) scaled.recycle()
            oriented.recycle()

            ImageContent(
                id = UUID.randomUUID().toString(),
                mimeType = stored.mimeType,
                width = stored.width,
                height = stored.height,
                bytes = stored.bytes
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Applies the EXIF orientation that camera/gallery photos often carry but
     * BitmapFactory does not apply itself. A failure leaves the image as-is.
     */
    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
        } catch (_: Exception) {
            bitmap
        }
    }

    /**
     * Keeps each database row comfortably below Android's CursorWindow size.
     * Transparent images stay PNG when that is small enough; otherwise the
     * image is flattened onto white and encoded as progressively smaller JPEG.
     */
    private fun encodeImageForStorage(bitmap: Bitmap): PreparedImage {
        if (bitmap.hasAlpha()) {
            val pngOutput = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, pngOutput)
            val pngBytes = pngOutput.toByteArray()
            if (pngBytes.size <= MAX_STORED_IMAGE_BYTES) {
                return PreparedImage("image/png", bitmap.width, bitmap.height, pngBytes)
            }

            val flattened = createBitmap(
                bitmap.width,
                bitmap.height,
                Bitmap.Config.ARGB_8888
            )
            try {
                val canvas = Canvas(flattened)
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                return encodeJpegForStorage(flattened)
            } finally {
                flattened.recycle()
            }
        }
        return encodeJpegForStorage(bitmap)
    }

    private fun encodeJpegForStorage(source: Bitmap): PreparedImage {
        var working = source
        var quality = 86
        try {
            while (true) {
                val output = ByteArrayOutputStream()
                working.compress(Bitmap.CompressFormat.JPEG, quality, output)
                val bytes = output.toByteArray()
                val maxSide = maxOf(working.width, working.height)
                if (bytes.size <= MAX_STORED_IMAGE_BYTES ||
                    (quality <= 55 && maxSide <= 700)
                ) {
                    return PreparedImage(
                        "image/jpeg",
                        working.width,
                        working.height,
                        bytes
                    )
                }

                if (quality > 55) {
                    quality -= 15
                    continue
                }

                val ratio = 0.8f
                val next = working.scale(
                    (working.width * ratio).toInt().coerceAtLeast(1),
                    (working.height * ratio).toInt().coerceAtLeast(1)
                )
                if (working !== source) working.recycle()
                working = next
                quality = 72
            }
        } finally {
            if (working !== source) working.recycle()
        }
    }

    /**
     * Saves an edited question, or adds a new one when [questionId] is null.
     * Returns null on success or a user-facing error message.
     */

    suspend fun saveQuestion(
        categoryId: String,
        questionId: String?,
        questionText: String,
        answerText: String,
        questionImages: List<ImageContent> = emptyList(),
        answerImages: List<ImageContent> = emptyList()
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
                    repository.addQuestion(
                        categoryId,
                        questionText,
                        answerText,
                        questionImages,
                        answerImages
                    )
                } else {
                    repository.updateQuestion(
                        questionId,
                        questionText,
                        answerText,
                        questionImages,
                        answerImages
                    )
                }
            }

            refreshSelectedPaper(paper.id)
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
        syncSessionWithPaper(detail)
    }

    /**
     * A generated test stores a snapshot of each question and its category
     * path. Renaming, moving, editing, or deleting content must keep that
     * snapshot in sync, and remove questions that no longer exist in this
     * paper (for example after a bulk delete or a category move).
     */
    private fun syncSessionWithPaper(paper: PaperDetail) {
        val current = session ?: return
        if (current.paperId != paper.id) return

        data class Location(
            val categoryId: String,
            val path: List<String>,
            val question: QuestionDetail
        )

        val locations = mutableMapOf<String, Location>()
        paper.flattenCategories().forEach { node ->
            node.category.questions.forEach { question ->
                locations[question.id] = Location(
                    categoryId = node.category.id,
                    path = node.path,
                    question = question
                )
            }
        }

        val questions = current.questions.mapNotNull { sessionQuestion ->
            val location = locations[sessionQuestion.questionId]
                ?: return@mapNotNull null
            sessionQuestion.copy(
                categoryId = location.categoryId,
                categoryName = location.path.lastOrNull()
                    ?: sessionQuestion.categoryName,
                categoryPath = location.path,
                questionText = location.question.questionText,
                answerText = location.question.answerText,
                questionImages = location.question.questionImages,
                answerImages = location.question.answerImages
            )
        }.mapIndexed { index, question -> question.copy(number = index + 1) }
        session = if (questions.isEmpty()) null else current.copy(questions = questions)
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
                        writeExport(pending, stream)
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

    /**
     * Renders the same export into the app cache and hands back a content URI
     * that can be attached to the Android share sheet.
     */
    fun shareExport(
        scope: ExportScope,
        format: ExportFormat,
        includeAnswers: Boolean,
        answersAtEnd: Boolean,
        rawFileName: String,
        onReady: (ShareRequest) -> Unit
    ) {
        val request = prepareExport(
            scope = scope,
            format = format,
            includeAnswers = includeAnswers,
            answersAtEnd = answersAtEnd,
            rawFileName = rawFileName
        ) ?: return
        val pending = pendingExport ?: return
        pendingExport = null
        exportState = ExportUiState(isExporting = true)

        viewModelScope.launch {
            try {
                val shareFile = withContext(Dispatchers.IO) {
                    val directory = File(
                        getApplication<Application>().cacheDir,
                        "exports"
                    ).apply { mkdirs() }
                    val file = File(directory, request.fileName)
                    file.outputStream().use { stream ->
                        writeExport(pending, stream)
                        stream.flush()
                    }
                    file
                }
                val application = getApplication<Application>()
                val uri = FileProvider.getUriForFile(
                    application,
                    "${application.packageName}.fileprovider",
                    shareFile
                )
                exportState = ExportUiState(
                    feedback = ExportFeedback(false, "Ready to share \"${pending.fileName}\".")
                )
                onReady(
                    ShareRequest(
                        uri = uri,
                        mimeType = pending.format.mimeType,
                        title = pending.fileName
                    )
                )
            } catch (e: Exception) {
                exportState = ExportUiState(
                    feedback = ExportFeedback(
                        true,
                        "Could not prepare the share. " +
                            (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
                    )
                )
            }
        }
    }

    private fun writeExport(pending: PendingExport, stream: OutputStream) {
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

    private data class PendingBackup(
        val fileName: String,
        val contents: String
    )

    private companion object {
        const val MAX_IMAGE_DIMENSION = 1400
        const val MAX_SOURCE_IMAGE_BYTES = 25 * 1024 * 1024
        const val MAX_STORED_IMAGE_BYTES = 1_200_000
        const val MAX_IMPORT_BYTES = 50 * 1024 * 1024
    }
}
