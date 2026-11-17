package com.paperpractice.ui

import android.app.Application
import android.content.Context
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
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paperpractice.data.AppDatabase
import com.paperpractice.data.CategoryDetail
import com.paperpractice.data.ImportConflict
import com.paperpractice.data.ImportMode
import com.paperpractice.data.ImportedPaper
import com.paperpractice.data.PaperDetail
import com.paperpractice.data.PaperRepository
import com.paperpractice.data.PaperSummary
import com.paperpractice.data.QuestionDetail
import com.paperpractice.data.SearchHit
import com.paperpractice.data.Tag
import com.paperpractice.data.normalizeTagKey
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
import com.paperpractice.ui.theme.AppTheme
import com.paperpractice.ui.theme.CustomTheme
import com.paperpractice.ui.theme.CustomThemeCodec
import com.paperpractice.ui.theme.InvalidThemeException
import com.paperpractice.ui.theme.MAX_CUSTOM_THEMES
import com.paperpractice.ui.theme.MAX_THEME_FILE_BYTES
import com.paperpractice.ui.theme.ThemeMode
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
import kotlinx.coroutines.CancellationException
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
    private val appearancePreferences =
        application.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val samplePreferences =
        application.getSharedPreferences("sample_paper", Context.MODE_PRIVATE)

    val papers: StateFlow<List<PaperSummary>> = repository.observePapers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Live global tag library, shared by search, test generation and editors. */
    val tags: StateFlow<List<Tag>> = repository.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Include-only tag ids applied to search and test generation. Empty means
     * no filter. Ids (not names) are kept so a rename does not break the filter.
     */
    var tagFilterIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** User-facing feedback from the tag-management screen. */
    var tagMessage by mutableStateOf<String?>(null)
        private set

    /**
     * Fresh for every ViewModel instance and never saved. Compose state keyed
     * with this token cannot be restored into a new ViewModel after process
     * death, so an editor never comes back holding text but no images.
     */
    val uiSessionToken: String = UUID.randomUUID().toString()

    var themeMode by mutableStateOf(
        ThemeMode.fromStoredValue(appearancePreferences.getString("theme_mode", null))
    )
        private set

    /** A built-in [AppTheme] key or the key of an imported theme. */
    var themePalette by mutableStateOf(
        appearancePreferences.getString("theme_palette", null)
            ?.takeIf { it.isNotBlank() }
            ?: AppTheme.PAPER.key
    )
        private set

    internal var customThemes by mutableStateOf(
        CustomThemeCodec.decodeAll(appearancePreferences.getString("custom_themes", null))
    )
        private set

    private var isImportingTheme = false

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

    var samplePaperToOpen by mutableStateOf<String?>(null)
        private set

    var configError by mutableStateOf<String?>(null)
        private set

    /** A reason an image picker could not attach a file; shown once in the UI. */
    var imageError by mutableStateOf<String?>(null)
        private set

    /**
     * Unsaved attachments for the open question editor. They live here rather
     * than in Compose saved state because image bytes can exceed the saved-state
     * transaction limit; the ViewModel survives rotation.
     */
    var editorQuestionImages by mutableStateOf<List<ImageContent>>(emptyList())
        private set

    var editorAnswerImages by mutableStateOf<List<ImageContent>>(emptyList())
        private set

    fun setEditorImages(
        questionImages: List<ImageContent>,
        answerImages: List<ImageContent>
    ) {
        editorQuestionImages = questionImages
        editorAnswerImages = answerImages
    }

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
    private var refreshGeneration = 0
    private var pendingExport: PendingExport? = null
    private var pendingImportContents: List<PaperContent>? = null
    private var pendingBackup: PendingBackup? = null
    private var searchJob: Job? = null
    private var searchGeneration = 0

    fun updateThemeMode(mode: ThemeMode) {
        if (mode == themeMode) return
        themeMode = mode
        appearancePreferences.edit { putString("theme_mode", mode.name) }
    }

    fun updateThemePalette(key: String) {
        if (key == themePalette) return
        // Stored as-is so an imported key survives a restart; resolveScheme
        // falls back to Paper if the theme has since been removed.
        themePalette = key
        appearancePreferences.edit { putString("theme_palette", key) }
    }

    /**
     * Imports a theme file picked by the user and selects it. The read is
     * capped at [MAX_THEME_FILE_BYTES]: the picker also offers "any file" on
     * some devices, and a bank or a backup must fail with a sentence, not
     * fill the heap.
     */
    fun importTheme(uri: Uri) {
        if (isImportingTheme) return
        isImportingTheme = true
        homeMessage = null
        viewModelScope.launch {
            try {
                val source = withContext(Dispatchers.IO) { readThemeText(uri) }
                val parsed = CustomThemeCodec.parse(source)
                val existing = customThemes
                val key = existing
                    .firstOrNull { it.name.equals(parsed.name, ignoreCase = true) }
                    ?.key
                    ?: "custom-" + System.currentTimeMillis().toString(36)
                if (existing.none { it.key == key } && existing.size >= MAX_CUSTOM_THEMES) {
                    throw InvalidThemeException(
                        "You can keep up to $MAX_CUSTOM_THEMES imported themes."
                    )
                }
                val updated = existing.filterNot { it.key == key } +
                    CustomTheme(key, parsed.name, parsed.spec)
                customThemes = updated
                themePalette = key
                appearancePreferences.edit {
                    putString("custom_themes", CustomThemeCodec.encodeAll(updated))
                    putString("theme_palette", key)
                }
                homeMessage = "Imported theme \"${parsed.name}\"."
            } catch (e: InvalidThemeException) {
                homeMessage = e.message ?: "That theme file is not valid."
            } catch (e: SecurityException) {
                homeMessage = "The app does not have permission to read that file."
            } catch (e: Exception) {
                homeMessage = "Could not import the theme. " +
                    (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
            } finally {
                isImportingTheme = false
            }
        }
    }

    fun removeCustomTheme(key: String) {
        val updated = customThemes.filterNot { it.key == key }
        if (updated.size == customThemes.size) return
        val wasSelected = themePalette == key
        customThemes = updated
        if (wasSelected) themePalette = AppTheme.PAPER.key
        appearancePreferences.edit {
            putString("custom_themes", CustomThemeCodec.encodeAll(updated))
            if (wasSelected) putString("theme_palette", AppTheme.PAPER.key)
        }
        homeMessage = "Removed the imported theme."
    }

    private fun readThemeText(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        val input = resolver.openInputStream(uri)
            ?: throw IOException("The theme file could not be opened.")
        return input.use { stream ->
            val buffer = ByteArray(8 * 1024)
            val output = ByteArrayOutputStream()
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_THEME_FILE_BYTES) {
                    throw InvalidThemeException(
                        "That file is too large for a theme " +
                            "(over ${MAX_THEME_FILE_BYTES / 1024} KB)."
                    )
                }
                output.write(buffer, 0, read)
            }
            output.toString(Charsets.UTF_8.name())
        }
    }

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

    /**
     * Imports the bundled example paper in one tap. If the sample is already
     * present, opens that copy instead of creating a duplicate.
     */
    fun loadSamplePaper() {
        if (isImporting || isPreparingBackup) return
        isImporting = true
        importError = null
        homeMessage = null

        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    val text = getApplication<Application>().assets
                        .open(SAMPLE_PAPER_ASSET)
                        .bufferedReader()
                        .use { it.readText() }
                    PaperJsonParser.parse(text)
                }
                // The id is remembered so a renamed sample is not imported a
                // second time. If that copy was deleted, forget the id and
                // fall back to the original name check.
                val storedId = samplePreferences.getString(SAMPLE_PAPER_ID_KEY, null)
                    ?.takeIf { it.isNotBlank() }
                val persistedId = withContext(Dispatchers.IO) {
                    storedId?.let { repository.getPaper(it, includeImages = false)?.id }
                }
                if (persistedId != null) {
                    homeMessage = "Sample paper is already loaded."
                    samplePaperToOpen = persistedId
                    return@launch
                }
                if (storedId != null) {
                    samplePreferences.edit { remove(SAMPLE_PAPER_ID_KEY) }
                }

                val existingId = withContext(Dispatchers.IO) {
                    repository.findPaperIdByName(content.paperName)
                }
                if (existingId != null) {
                    samplePreferences.edit { putString(SAMPLE_PAPER_ID_KEY, existingId) }
                    homeMessage = "Sample paper is already loaded."
                    samplePaperToOpen = existingId
                    return@launch
                }

                val imported = withContext(Dispatchers.IO) {
                    repository.importPapers(listOf(content), ImportMode.KEEP_BOTH)
                }
                val importedId = imported.firstOrNull()?.id
                if (importedId != null) {
                    samplePreferences.edit { putString(SAMPLE_PAPER_ID_KEY, importedId) }
                }
                homeMessage = importSummary(imported, replaced = false)
                samplePaperToOpen = importedId
            } catch (e: PaperFormatException) {
                importError = "Could not load the sample paper. " +
                    (e.message ?: "The sample file is not valid.")
            } catch (e: Exception) {
                importError = "Could not load the sample paper. " +
                    (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
            } finally {
                isImporting = false
            }
        }
    }

    fun consumeSamplePaperToOpen(): String? {
        val paperId = samplePaperToOpen ?: return null
        samplePaperToOpen = null
        return paperId
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
                        // A merge can add categories/questions and replace
                        // images in the paper that is currently open. Reload
                        // with bytes so returning to that paper never shows
                        // stale content.
                        refreshSelectedPaper(selectedId, forceImageReload = true)
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
    private fun readAtMost(
        stream: InputStream,
        limit: Int,
        onTooLarge: () -> Unit = {}
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) {
                onTooLarge()
                return ByteArray(0)
            }
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

    fun clearTagMessage() {
        tagMessage = null
    }

    /** Returns the pending image failure and clears it so it cannot replay. */
    fun consumeImageError(): String? {
        val message = imageError
        imageError = null
        return message
    }

    fun clearImageError() {
        imageError = null
    }

    // ---------------------------------------------------------------------
    // Global search
    // ---------------------------------------------------------------------

    fun search(query: String) {
        searchQuery = query
        runSearch(query)
    }

    /** Shared by typing and by changing the tag filter, with the same guard. */
    private fun runSearch(query: String) {
        searchJob?.cancel()
        val generation = ++searchGeneration
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            searchResults = emptyList()
            isSearching = false
            return
        }

        // Capture the filter at launch time so a concurrent change cannot make
        // an older query read a newer filter (or vice versa).
        val filterIds = tagFilterIds
        isSearching = true
        searchJob = viewModelScope.launch {
            delay(220)
            try {
                val results = withContext(Dispatchers.IO) {
                    repository.search(trimmed, limit = 10_000, tagIds = filterIds)
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

    /** Applies a tag include-filter and re-runs a search if one is active. */
    fun setTagFilter(ids: Set<String>) {
        tagFilterIds = ids
        if (searchQuery.trim().length >= 2) {
            runSearch(searchQuery)
        }
    }

    fun clearTagFilter() {
        setTagFilter(emptySet())
    }

    // ---------------------------------------------------------------------
    // Global tag library
    // ---------------------------------------------------------------------

    /** Creates a tag; returns its id, or null on an invalid/duplicate name. */
    suspend fun createTag(name: String): String? = try {
        val created = withContext(Dispatchers.IO) { repository.createTag(name) }
        tagMessage = if (created != null) {
            "Added tag \"${created.name}\"."
        } else {
            "Could not add that tag. The name may be empty, too long, or already in use."
        }
        created?.id
    } catch (e: Exception) {
        tagMessage = "Could not add the tag. " +
            (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
        null
    }

    suspend fun renameTag(tagId: String, newName: String): Boolean = try {
        val paperId = selectedPaper?.id
        val renamed = withContext(Dispatchers.IO) { repository.renameTag(tagId, newName) }
        tagMessage = if (renamed) {
            "Tag renamed."
        } else {
            "Could not rename the tag. The name may be empty, too long, or already in use."
        }
        // QuestionDetail stores display names, so a rename must reload the
        // open tree for filters and test generation to keep matching.
        if (renamed && paperId != null) refreshSelectedPaper(paperId)
        renamed
    } catch (e: Exception) {
        tagMessage = "Could not rename the tag. " +
            (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
        false
    }

    suspend fun deleteTag(tagId: String): Boolean = try {
        val paperId = selectedPaper?.id
        val deleted = withContext(Dispatchers.IO) { repository.deleteTag(tagId) }
        tagMessage = if (deleted) "Tag deleted." else "Could not delete that tag."
        if (deleted) {
            setTagFilter(tagFilterIds - tagId)
            if (paperId != null) refreshSelectedPaper(paperId)
        }
        deleted
    } catch (e: Exception) {
        tagMessage = "Could not delete the tag. " +
            (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
        false
    }

    /** Moves every link from the source tag into the target, then removes source. */
    suspend fun mergeTags(sourceTagId: String, targetTagId: String): Boolean = try {
        val paperId = selectedPaper?.id
        val merged = withContext(Dispatchers.IO) {
            repository.mergeTags(sourceTagId, targetTagId)
        }
        tagMessage = if (merged) "Tags merged." else "Could not merge those tags."
        if (merged) {
            // Only translate a filter that actually contained the source tag.
            // Merging two tags must not silently activate a filter when none
            // was set, which would otherwise restrict search/tests to target.
            if (sourceTagId in tagFilterIds) {
                setTagFilter((tagFilterIds - sourceTagId) + targetTagId)
            }
            if (paperId != null) refreshSelectedPaper(paperId)
        }
        merged
    } catch (e: Exception) {
        tagMessage = "Could not merge the tags. " +
            (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
        false
    }

    suspend fun clearUnusedTags(): Int = try {
        val removed = withContext(Dispatchers.IO) { repository.clearUnusedTags() }
        tagMessage = if (removed == 0) {
            "No unused tags to remove."
        } else {
            "Removed $removed unused ${if (removed == 1) "tag" else "tags"}."
        }
        if (removed > 0) {
            // Deleted tag ids must not linger in the filter: they would give
            // search zero hits forever while tests silently fall back to all
            // questions. Re-read the live library and keep only surviving ids.
            val liveIds = withContext(Dispatchers.IO) {
                repository.getTags().mapTo(linkedSetOf()) { it.id }
            }
            setTagFilter(tagFilterIds intersect liveIds)
        }
        removed
    } catch (e: Exception) {
        tagMessage = "Could not clear unused tags. " +
            (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
        0
    }

    /** Display names for [ids] from the current library, sorted like the DB. */
    private fun tagDisplayNames(ids: Set<String>): List<String> =
        tags.value.filter { it.id in ids }
            .map { it.name }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })

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
        if (focus.paperId != paperId) {
            // The focus belongs to a different paper; do not let it leak into
            // the next paper the user opens.
            pendingContentFocus = null
            return null
        }
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
            // Render the whole backup into a private cache file first. Only a
            // small filename/path is kept in state, never the full JSON text.
            val prepared = try {
                withContext(Dispatchers.IO) {
                    val papers = repository.getAllPapers()
                        .map(ExportContentBuilder::entirePaper)
                    if (papers.isEmpty()) {
                        null
                    } else {
                        val directory = backupCacheDirectory()
                        // Supersede only the file this ViewModel still owns. A
                        // saveBackup already in flight cleared pendingBackup,
                        // so the file it is copying is never touched here.
                        pendingBackup?.let { File(it.filePath).delete() }
                        // Bound leftovers from cancelled flows, but never delete
                        // a fresh file another operation may still be using.
                        val staleBefore = System.currentTimeMillis() - BACKUP_STALE_MS
                        directory.listFiles()?.forEach { old ->
                            if (old.name.startsWith(BACKUP_FILE_PREFIX) &&
                                old.lastModified() < staleBefore
                            ) {
                                old.delete()
                            }
                        }
                        // Unique per prepare so two staged backups cannot share
                        // (and delete) the same path.
                        val fileName = BACKUP_FILE_PREFIX +
                            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) +
                            "-" + UUID.randomUUID() + ".json"
                        val file = renderToCacheFile(directory, fileName) { out ->
                            PaperBackup.write(out, papers)
                        }
                        PendingBackup(fileName = fileName, filePath = file.absolutePath)
                    }
                }
            } catch (e: Exception) {
                homeMessage = "Could not prepare the backup. " +
                    (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
                return null
            }
            if (prepared == null) {
                homeMessage = "There are no papers to back up."
                return null
            }

            pendingBackup = prepared
            return CreateDocumentRequest("application/json", prepared.fileName)
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
                    val source = File(backup.filePath)
                    if (!source.exists()) {
                        throw IOException("The backup is no longer available.")
                    }
                    // The payload is already complete on disk; copying it to
                    // the picked Uri is a single bounded pass, so nothing is
                    // written to the destination while the backup is rendered.
                    copyToUri(source, uri)
                }
                homeMessage = "Backup saved as \"${backup.fileName}\"."
            } catch (e: Exception) {
                homeMessage = "Could not save the backup. " +
                    (e.message?.takeIf { it.isNotBlank() } ?: "Please try again.")
            } finally {
                // Non-suspending on purpose: withContext is cancellable, so a
                // cancelled coroutine could skip this cleanup entirely.
                File(backup.filePath).delete()
            }
        }
    }

    fun cancelBackup() {
        pendingBackup?.let { File(it.filePath).delete() }
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
        if (hasPaperWithName(normalized)) {
            return CreatePaperResult.Failure(
                "A paper named \"$normalized\" already exists."
            )
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
        if (hasPaperWithName(normalized, exceptPaperId = paperId)) {
            return "A paper named \"$normalized\" already exists."
        }

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

    /** Imports match names case-insensitively; manual create/rename must too. */
    private fun hasPaperWithName(name: String, exceptPaperId: String? = null): Boolean {
        val lowered = name.trim().lowercase()
        return papers.value.any { paper ->
            paper.id != exceptPaperId && paper.name.trim().lowercase() == lowered
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

    /** Normalized display names for the ids in the active tag filter. */
    private fun activeTagKeys(): Set<String> =
        tags.value.filter { it.id in tagFilterIds }
            .mapTo(linkedSetOf()) { normalizeTagKey(it.name) }

    /**
     * Questions in this subtree that the active tag filter would keep, counted
     * as distinct question ids to match TestGenerator (a MERGE import can leave
     * duplicate ids). With no filter every question is kept, but it is still
     * de-duplicated by id so the UI never accepts a count the generator rejects.
     */
    private fun CategoryDetail.availableWith(keys: Set<String>): Int {
        return allQuestions()
            .filter { keys.isEmpty() || it.tags.any { tag -> normalizeTagKey(tag) in keys } }
            .mapTo(linkedSetOf()) { it.id }
            .size
    }

    fun toggleCategory(categoryId: String) {
        val paper = selectedPaper ?: return
        val category = paper.findCategory(categoryId) ?: return
        if (category.availableWith(activeTagKeys()) == 0) return

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
        val tagKeys = activeTagKeys()

        selectedCategories.forEach { node ->
            val category = node.category
            val available = category.availableWith(tagKeys)
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
            val generated = TestGenerator.generate(
                paper = paper,
                selectedCategoryIds = selectedCategoryIds,
                requestedCounts = counts,
                tagFilter = tagDisplayNames(tagFilterIds).toSet()
            )
            val baseSession = generated
            session = baseSession
            // The generator copies whatever images the tree carried. Hydrate
            // them from the database by question id so practice and "current
            // test" export keep their images even when the tree was refreshed
            // without BLOBs. Guard on object identity, not createdAt: an edit
            // syncs the live session through copy(...) while keeping createdAt,
            // so a timestamp check would still pass and restore stale text over
            // the user's edit. Any edit also brings the tree's images along, so
            // skipping hydration then is safe.
            viewModelScope.launch {
                val live = session
                if (live !== baseSession || live?.paperId != baseSession.paperId) return@launch
                val hydrated = withContext(Dispatchers.IO) { hydrateSessionImages(live) }
                if (session === live && session?.paperId == baseSession.paperId) {
                    session = hydrated
                }
            }
            configError = null
            true
        } catch (e: IllegalArgumentException) {
            configError = e.message ?: "Could not generate the test."
            false
        }
    }

    /** Fills a generated session's question/answer images from the database. */
    private suspend fun hydrateSessionImages(session: TestSession): TestSession = try {
        val images = repository.findImagesByQuestionIds(
            session.questions.map { it.questionId }
        )
        session.copy(
            questions = session.questions.map { question ->
                val forQuestion = images[question.questionId]
                if (forQuestion == null) {
                    question
                } else {
                    question.copy(
                        questionImages = forQuestion.questionImages,
                        answerImages = forQuestion.answerImages
                    )
                }
            }
        )
    } catch (e: CancellationException) {
        // Cancellation must still cancel the coroutine, not be swallowed.
        throw e
    } catch (e: Exception) {
        // Hydration is best-effort. The generator already copied the tree's
        // bytes into the session, so failure leaves those intact instead of
        // taking down the session or producing an uncaught exception.
        session
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
        // A batch of picked images may contain several failures; do not clear a
        // message set by an earlier file. The picker callback consumes the
        // final message and shows it inline in the editor dialog.
        try {
            val resolver = getApplication<Application>().contentResolver
            var tooLarge = false
            val sourceBytes = resolver.openInputStream(uri)?.use { stream ->
                readAtMost(stream, MAX_SOURCE_IMAGE_BYTES) { tooLarge = true }
            } ?: run {
                imageError = "That image could not be opened."
                return@withContext null
            }
            if (sourceBytes.isEmpty()) {
                imageError = if (tooLarge) {
                    "That image is too large. Images must be under " +
                        "${MAX_SOURCE_IMAGE_BYTES / (1024 * 1024)} MB."
                } else {
                    "That image is empty or could not be read."
                }
                return@withContext null
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                imageError = "That file is not a supported image format."
                return@withContext null
            }

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
            ) ?: run {
                imageError = "That image could not be decoded."
                return@withContext null
            }

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
            imageError = "That image could not be added. Please try another file."
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
        answerImages: List<ImageContent> = emptyList(),
        tagIds: Set<String> = emptySet()
    ): String? {
        val paper = selectedPaper ?: return "No paper is loaded."
        val category = paper.findCategory(categoryId)
            ?: return "This category could not be found."
        if (questionText.isBlank()) {
            return "Question text cannot be empty."
        }

        return try {
            val newQuestionId = withContext(Dispatchers.IO) {
                if (questionId == null) {
                    repository.addQuestion(
                        categoryId = categoryId,
                        questionText = questionText,
                        answerText = answerText,
                        questionImages = questionImages,
                        answerImages = answerImages,
                        tagIds = tagIds
                    )
                } else {
                    repository.updateQuestion(
                        questionId = questionId,
                        questionText = questionText,
                        answerText = answerText,
                        questionImages = questionImages,
                        answerImages = answerImages,
                        tagIds = tagIds
                    )
                    null
                }
            }
            val savedQuestionId = newQuestionId ?: checkNotNull(questionId)
            // updateQuestion treats an empty set as "not supplied", so write
            // links explicitly to make clearing every tag actually stick.
            withContext(Dispatchers.IO) {
                repository.setQuestionTags(savedQuestionId, tagIds)
            }
            val resolvedTags = tagDisplayNames(tagIds)

            // Patch the in-memory tree BEFORE the image-free refresh. That
            // refresh reuses bytes only for question ids already present in the
            // old tree, so a newly added question must be inserted here with
            // both of its supplied image lists; otherwise it would arrive empty
            // and a later Save would delete its DB images. `updateCategory`
            // targets the category by id at any depth, and `paper.findCategory`
            // above already validated that id.
            selectedPaper = selectedPaper?.updateCategory(categoryId) { target ->
                if (questionId == null) {
                    target.copy(
                        questions = target.questions + QuestionDetail(
                            id = checkNotNull(newQuestionId),
                            questionText = questionText,
                            answerText = answerText,
                            questionImages = questionImages,
                            answerImages = answerImages,
                            tags = resolvedTags
                        )
                    )
                } else {
                    target.updateQuestion(questionId) { question ->
                        question.copy(
                            questionImages = questionImages,
                            answerImages = answerImages,
                            tags = resolvedTags
                        )
                    }
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

    private suspend fun refreshSelectedPaper(
        paperId: String,
        forceImageReload: Boolean = false
    ) {
        // Refreshes can overlap (reordering queues several, and mutations can
        // land while one is reading). Mirror loadPaper's generation guard so a
        // slower, older refresh cannot publish its result over a newer one.
        val generation = ++refreshGeneration
        val fresh = withContext(Dispatchers.IO) {
            if (forceImageReload) {
                repository.getPaper(paperId)
            } else {
                repository.getPaperTree(paperId)
            }
        } ?: return
        if (generation != refreshGeneration) return
        if (selectedPaper?.id != paperId) return

        // Re-read after the IO suspension: another writer may have replaced the
        // tree while we were reading, and the carry below must use that latest
        // in-memory state rather than a pre-IO snapshot of the bytes.
        val previous = selectedPaper

        // A structural refresh deliberately skips image BLOBs. Reuse the bytes
        // already held for this paper so thumbnails, the editor and exports keep
        // working without re-reading every image after each mutation. An import
        // merge is the one case that can change images in the database itself.
        val detail = if (!forceImageReload && previous?.id == paperId) {
            carryImagesIntoTree(fresh, previous)
        } else {
            fresh
        }
        selectedPaper = detail
        val nodes = detail.flattenCategories()
        val nodeById = nodes.associateBy { it.category.id }

        // Selecting a category covers its whole subtree. A move/merge/delete
        // can leave both an ancestor and one of its descendants selected, which
        // would double-count their questions. Keep only the outermost selected
        // id and drop any selected id it covers. The outcome is independent of
        // iteration order: an ancestor always displaces a kept descendant, and
        // a kept ancestor always makes a descendant get skipped, so the
        // topmost id of every overlapping group survives.
        val previousSelection = selectedCategoryIds
        val normalizedSelection = linkedSetOf<String>()
        previousSelection.forEach { id ->
            val node = nodeById[id] ?: return@forEach
            if (normalizedSelection.any { kept -> nodeById[kept]?.contains(node) == true }) {
                return@forEach
            }
            normalizedSelection.removeAll { kept ->
                nodeById[kept]?.let { node.contains(it) } == true
            }
            normalizedSelection.add(id)
        }
        selectedCategoryIds = normalizedSelection

        // Drop counts for deleted and de-selected categories, then clamp every
        // remaining value to the category's current subtree size.
        val deSelectedIds = previousSelection - normalizedSelection
        val currentCategoryIds = nodeById.keys
        val retainedCounts = (requestedCounts - deSelectedIds)
            .filterKeys { it in currentCategoryIds }
            .toMutableMap()
        nodes.forEach { node ->
            val id = node.category.id
            val available = node.category.totalQuestionCount
            val raw = retainedCounts[id] ?: if (available == 0) "0" else "1"
            val parsed = raw.trim().toIntOrNull()
            retainedCounts[id] = if (parsed != null && parsed > available) {
                available.toString()
            } else {
                raw
            }
        }
        requestedCounts = retainedCounts
        syncSessionWithPaper(detail)
    }

    /**
     * Copies the image lists from an already loaded tree into a freshly loaded,
     * image-free tree, matching questions by id. Text, ordering and the category
     * structure come from the fresh tree; only image bytes are reused, so no
     * BLOB is read from the database during a refresh.
     */
    private fun carryImagesIntoTree(
        fresh: PaperDetail,
        previous: PaperDetail
    ): PaperDetail {
        val oldImages = previous.flattenCategories()
            .flatMap { it.category.questions }
            .associateBy { it.id }
        return fresh.copy(
            categories = fresh.categories.map { it.copyImagesFrom(oldImages) }
        )
    }

    private fun CategoryDetail.copyImagesFrom(
        oldQuestions: Map<String, QuestionDetail>
    ): CategoryDetail = copy(
        questions = questions.map { question ->
            oldQuestions[question.id]?.let { old ->
                // Only image bytes are carried over. `question` comes from the
                // fresh DB tree, so its tag names stay authoritative here.
                question.copy(
                    questionImages = old.questionImages,
                    answerImages = old.answerImages
                )
            } ?: question
        },
        children = children.map { it.copyImagesFrom(oldQuestions) }
    )

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
                // Match TestGenerator: the snapshot stores the full path, not
                // just the leaf, so headers stay identical after a refresh.
                categoryName = location.path.joinToString(" / "),
                categoryPath = location.path,
                questionText = location.question.questionText,
                answerText = location.question.answerText,
                questionImages = location.question.questionImages,
                answerImages = location.question.answerImages,
                tags = location.question.tags
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
                    // Render fully into a private cache file, verify it, then
                    // copy to the picked Uri in one bounded pass. A failed or
                    // cancelled render never touches the destination.
                    renderToCacheThenCopy(
                        fileName = pending.fileName,
                        destination = uri,
                        render = { out -> writeExport(pending, out) }
                    )
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
                    // Share files are only needed for the current share sheet;
                    // clear old ones so the cache does not grow without bound.
                    directory.listFiles()?.forEach { old -> old.delete() }
                    // Render via a temp file and rename on success so a partial
                    // write can never be handed to the share sheet.
                    renderToCacheFile(directory, request.fileName) { out ->
                        writeExport(pending, out)
                    }
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

    private suspend fun writeExport(pending: PendingExport, stream: OutputStream) {
        when (pending.format) {
            ExportFormat.HTML -> HtmlExporter.write(
                stream,
                pending.content,
                pending.includeAnswers
            )

            ExportFormat.DOCX -> DocxExporter.writeCancellable(
                pending.content,
                pending.includeAnswers,
                pending.answersAtEnd,
                stream
            )

            ExportFormat.PDF -> PdfExporter.writeCancellable(
                pending.content,
                pending.includeAnswers,
                pending.answersAtEnd,
                stream
            )

            ExportFormat.JSON -> JsonExporter.write(
                stream,
                pending.content,
                pending.includeAnswers
            )
        }
    }

    /** Private cache directory used for staged backup/export payloads. */
    private fun backupCacheDirectory(): File =
        File(getApplication<Application>().cacheDir, BACKUP_CACHE_DIR).apply { mkdirs() }

    /**
     * Renders into a private cache file, verifies the result, then copies it to
     * the user-picked Uri in a single bounded pass. Nothing is written to the
     * destination until rendering has fully succeeded, and the temp file is
     * always removed.
     */
    private suspend fun renderToCacheThenCopy(
        fileName: String,
        destination: Uri,
        render: suspend (OutputStream) -> Unit
    ) {
        val directory = backupCacheDirectory()
        // Unique temp name so concurrent operations with the same destination
        // file name cannot overwrite each other's staged bytes. The final
        // (share/backup) name stays deterministic.
        val temp = File(directory, "$fileName-${UUID.randomUUID()}.tmp")
        try {
            temp.outputStream().use { out ->
                render(out)
                out.flush()
            }
            verifyComplete(temp)
            copyToUri(temp, destination)
        } finally {
            temp.delete()
        }
    }

    /**
     * Renders into a temp file in [directory], verifies it, then renames it to
     * [fileName]. Returns the completed file; the temp file is removed on any
     * failure.
     */
    private suspend fun renderToCacheFile(
        directory: File,
        fileName: String,
        render: suspend (OutputStream) -> Unit
    ): File {
        // Only the temp name is unique; `final` keeps the deterministic name
        // used by the FileProvider URI and share title.
        val temp = File(directory, "$fileName-${UUID.randomUUID()}.tmp")
        val final = File(directory, fileName)
        try {
            temp.outputStream().use { out ->
                render(out)
                out.flush()
            }
            verifyComplete(temp)
            if (!temp.renameTo(final)) {
                // Some filesystems refuse a rename even within one directory.
                temp.copyTo(final, overwrite = true)
            }
            return final
        } finally {
            temp.delete()
        }
    }

    private fun verifyComplete(file: File) {
        if (!file.exists() || file.length() <= 0L) {
            throw IOException("The file could not be prepared.")
        }
    }

    /** Copies a fully rendered cache file to a SAF Uri with bounded memory. */
    private fun copyToUri(source: File, destination: Uri) {
        val resolver = getApplication<Application>().contentResolver
        val output = resolver.openOutputStream(destination, "wt")
            ?: throw IOException("The destination could not be opened.")
        output.use { stream ->
            source.inputStream().use { input ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    stream.write(buffer, 0, read)
                }
                stream.flush()
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
        val filePath: String
    )

    private companion object {
        const val BACKUP_CACHE_DIR = "pending-exports"
        const val BACKUP_FILE_PREFIX = "paper-practice-backup-"
        const val BACKUP_STALE_MS = 60 * 60 * 1000L
        const val SAMPLE_PAPER_ASSET = "sample-paper.json"
        const val SAMPLE_PAPER_ID_KEY = "sample_paper_id"
        const val MAX_IMAGE_DIMENSION = 1400
        const val MAX_SOURCE_IMAGE_BYTES = 25 * 1024 * 1024
        const val MAX_STORED_IMAGE_BYTES = 1_200_000
        const val MAX_IMPORT_BYTES = 50 * 1024 * 1024
    }
}
