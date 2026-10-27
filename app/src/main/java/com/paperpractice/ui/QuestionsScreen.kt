package com.paperpractice.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperpractice.data.CategoryDetail
import com.paperpractice.data.CategoryNode
import com.paperpractice.data.PaperDetail
import com.paperpractice.data.PaperSummary
import com.paperpractice.data.QuestionDetail
import com.paperpractice.domain.ImageContent
import com.paperpractice.ui.util.categoryCountLabel
import com.paperpractice.ui.util.decodeImageForDisplay
import com.paperpractice.ui.util.questionCountLabel
import kotlinx.coroutines.launch

private data class QuestionEditorState(
    val categoryId: String,
    val categoryName: String,
    val questionId: String?,
    val questionText: String,
    val answerText: String,
    val questionImages: List<ImageContent> = emptyList(),
    val answerImages: List<ImageContent> = emptyList()
) {
    val isNew: Boolean
        get() = questionId == null
}

private data class AddCategoryTarget(
    val parentId: String?,
    val parentName: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionsScreen(
    viewModel: MainViewModel,
    paperId: String,
    onBack: () -> Unit
) {
    LaunchedEffect(paperId) {
        viewModel.loadPaper(paperId)
    }

    val paper = viewModel.selectedPaper?.takeIf { it.id == paperId }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editor by remember { mutableStateOf<QuestionEditorState?>(null) }
    var deleteTarget by remember { mutableStateOf<QuestionEditorState?>(null) }
    var addCategoryTarget by remember { mutableStateOf<AddCategoryTarget?>(null) }
    var categoryToRename by remember { mutableStateOf<CategoryDetail?>(null) }
    var categoryToDelete by remember { mutableStateOf<CategoryDetail?>(null) }
    var showRenamePaper by remember { mutableStateOf(false) }
    var paperMenuOpen by remember { mutableStateOf(false) }
    var expandedCategoryIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedQuestionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var moveQuestionsTarget by remember { mutableStateOf<Set<String>?>(null) }
    var categoryToMove by remember { mutableStateOf<CategoryDetail?>(null) }
    var categoryToReparent by remember { mutableStateOf<CategoryDetail?>(null) }
    var categoryToMerge by remember { mutableStateOf<CategoryDetail?>(null) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    var confirmMergeDuplicates by remember { mutableStateOf(false) }
    var focusedCategoryId by remember { mutableStateOf<String?>(null) }
    var focusedQuestionId by remember { mutableStateOf<String?>(null) }
    val allPapers by viewModel.papers.collectAsStateWithLifecycle()

    LaunchedEffect(paper?.id) {
        selectionMode = false
        selectedQuestionIds = emptySet()
        focusedCategoryId = null
        focusedQuestionId = null

        val loaded = paper ?: return@LaunchedEffect
        val focus = viewModel.consumeContentFocus(loaded.id) ?: return@LaunchedEffect
        val categoryId = focus.categoryId ?: return@LaunchedEffect
        val lineage = loaded.categoryLineage(categoryId) ?: return@LaunchedEffect
        expandedCategoryIds = expandedCategoryIds + lineage.map { it.id }
        if (focus.questionId == null) {
            focusedCategoryId = categoryId
        } else {
            focusedQuestionId = focus.questionId
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (selectionMode) {
                        Text("${selectedQuestionIds.size} selected")
                    } else {
                        Text("Questions")
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectionMode) {
                                selectionMode = false
                                selectedQuestionIds = emptySet()
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            if (selectionMode) {
                                Icons.Outlined.Close
                            } else {
                                Icons.AutoMirrored.Outlined.ArrowBack
                            },
                            contentDescription = if (selectionMode) {
                                "Exit selection mode"
                            } else {
                                "Back"
                            }
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(
                            onClick = {
                                val visible = paper?.flattenCategories()
                                    ?.flatMap { it.category.questions }
                                    ?.map { it.id }
                                    ?.toSet()
                                    .orEmpty()
                                selectedQuestionIds = if (
                                    visible.isNotEmpty() &&
                                    selectedQuestionIds.containsAll(visible)
                                ) {
                                    emptySet()
                                } else {
                                    visible
                                }
                            }
                        ) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = "Select or clear all questions"
                            )
                        }
                    } else {
                        Box {
                            IconButton(onClick = { paperMenuOpen = true }) {
                                Icon(
                                    Icons.Outlined.MoreVert,
                                    contentDescription = "Paper options"
                                )
                            }
                            DropdownMenu(
                                expanded = paperMenuOpen,
                                onDismissRequest = { paperMenuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename paper") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Edit, contentDescription = null)
                                    },
                                    enabled = paper != null,
                                    onClick = {
                                        paperMenuOpen = false
                                        showRenamePaper = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add category") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Add, contentDescription = null)
                                    },
                                    enabled = paper != null,
                                    onClick = {
                                        paperMenuOpen = false
                                        addCategoryTarget = AddCategoryTarget(null, null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Select questions") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Check, contentDescription = null)
                                    },
                                    enabled = paper != null && paper.questionCount > 0,
                                    onClick = {
                                        paperMenuOpen = false
                                        selectionMode = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Merge duplicate categories") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.ArrowDownward,
                                            contentDescription = null
                                        )
                                    },
                                    enabled = paper != null && paper.categoryCount > 1,
                                    onClick = {
                                        paperMenuOpen = false
                                        confirmMergeDuplicates = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (selectionMode) {
                SelectionBottomBar(
                    selectedCount = selectedQuestionIds.size,
                    onMove = {
                        if (selectedQuestionIds.isNotEmpty()) {
                            moveQuestionsTarget = selectedQuestionIds
                        }
                    },
                    onDelete = {
                        if (selectedQuestionIds.isNotEmpty()) confirmBulkDelete = true
                    }
                )
            }
        },
        floatingActionButton = {
            if (paper != null && !selectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { addCategoryTarget = AddCategoryTarget(null, null) },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Add category") }
                )
            }
        }
    ) { innerPadding ->
        when {
            viewModel.isPaperLoading || (paper == null && viewModel.paperLoadError == null) -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            paper == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(28.dp)
                    ) {
                        Text(
                            "Paper unavailable",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            viewModel.paperLoadError ?: "This paper could not be loaded.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = onBack) { Text("Back") }
                    }
                }
            }

            else -> {
                QuestionsContent(
                    paper = paper,
                    selectionMode = selectionMode,
                    selectedQuestionIds = selectedQuestionIds,
                    onToggleQuestionSelected = { questionId ->
                        selectedQuestionIds = if (questionId in selectedQuestionIds) {
                            selectedQuestionIds - questionId
                        } else {
                            selectedQuestionIds + questionId
                        }
                    },
                    onMoveQuestion = { question ->
                        moveQuestionsTarget = setOf(question.id)
                    },
                    onMoveCategory = { category -> categoryToMove = category },
                    onReparentCategory = { category -> categoryToReparent = category },
                    onMergeCategory = { category -> categoryToMerge = category },
                    onReorderQuestion = { question, delta ->
                        scope.launch {
                            viewModel.reorderQuestion(question.id, delta)
                        }
                    },
                    onReorderCategory = { category, delta ->
                        scope.launch {
                            viewModel.reorderCategory(category.id, delta)
                        }
                    },
                    onEditQuestion = { node, question ->
                        if (selectionMode) {
                            selectedQuestionIds = if (question.id in selectedQuestionIds) {
                                selectedQuestionIds - question.id
                            } else {
                                selectedQuestionIds + question.id
                            }
                        } else {
                            editor = QuestionEditorState(
                                categoryId = node.category.id,
                                categoryName = node.displayName,
                                questionId = question.id,
                                questionText = question.questionText,
                                answerText = question.answerText,
                                questionImages = question.questionImages,
                                answerImages = question.answerImages
                            )
                        }
                    },
                    onAddQuestion = { node ->
                        expandedCategoryIds = expandedCategoryIds + node.category.id
                        editor = QuestionEditorState(
                            categoryId = node.category.id,
                            categoryName = node.displayName,
                            questionId = null,
                            questionText = "",
                            answerText = ""
                        )
                    },
                    onAddSubcategory = { node ->
                        expandedCategoryIds = expandedCategoryIds + node.category.id
                        addCategoryTarget = AddCategoryTarget(
                            parentId = node.category.id,
                            parentName = node.displayName
                        )
                    },
                    onRenameCategory = { category -> categoryToRename = category },
                    onDeleteCategory = { category -> categoryToDelete = category },
                    expandedCategoryIds = expandedCategoryIds,
                    onToggleCategoryExpanded = { categoryId ->
                        expandedCategoryIds = if (categoryId in expandedCategoryIds) {
                            expandedCategoryIds - categoryId
                        } else {
                            expandedCategoryIds + categoryId
                        }
                    },
                    focusedCategoryId = focusedCategoryId,
                    focusedQuestionId = focusedQuestionId,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }

    val currentEditor = editor
    if (currentEditor != null) {
        QuestionEditorDialog(
            state = currentEditor,
            onDismiss = { editor = null },
            onSaved = { wasNew ->
                editor = null
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (wasNew) "Question added." else "Question updated."
                    )
                }
            },
            onRequestDelete = { target ->
                editor = null
                deleteTarget = target
            },
            viewModel = viewModel
        )
    }

    val currentDeleteTarget = deleteTarget
    if (currentDeleteTarget != null) {
        var deleting by remember(currentDeleteTarget) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = {
                if (!deleting) deleteTarget = null
            },
            icon = {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete this question?") },
            text = {
                Text(
                    buildString {
                        append("This question will be removed from \"")
                        append(currentDeleteTarget.categoryName)
                        append("\". This cannot be undone.")
                        if (currentDeleteTarget.questionText.isNotBlank()) {
                            append("\n\n")
                            append(currentDeleteTarget.questionText.take(180))
                            if (currentDeleteTarget.questionText.length > 180) append("...")
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        scope.launch {
                            val error = viewModel.deleteQuestion(
                                currentDeleteTarget.questionId.orEmpty()
                            )
                            deleting = false
                            deleteTarget = null
                            snackbarHostState.showSnackbar(error ?: "Question deleted.")
                        }
                    }
                ) {
                    if (deleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!deleting) deleteTarget = null },
                    enabled = !deleting
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    val currentAddTarget = addCategoryTarget
    if (currentAddTarget != null && paper != null) {
        val parentName = currentAddTarget.parentName
        NameEditorDialog(
            title = if (parentName == null) "Add category" else "Add subcategory",
            label = if (parentName == null) "Category name" else "Subcategory name",
            initialValue = "",
            confirmLabel = "Add",
            onDismiss = { addCategoryTarget = null },
            onSave = { name ->
                viewModel.addCategory(paper.id, name, currentAddTarget.parentId)
            },
            onSaved = {
                addCategoryTarget = null
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (parentName == null) "Category added." else "Subcategory added."
                    )
                }
            }
        )
    }

    val currentRenameCategory = categoryToRename
    if (currentRenameCategory != null) {
        NameEditorDialog(
            title = "Rename category",
            label = "Category name",
            initialValue = currentRenameCategory.name,
            confirmLabel = "Save",
            onDismiss = { categoryToRename = null },
            onSave = { name -> viewModel.renameCategory(currentRenameCategory.id, name) },
            onSaved = {
                categoryToRename = null
                scope.launch { snackbarHostState.showSnackbar("Category renamed.") }
            }
        )
    }

    val currentCategoryDelete = categoryToDelete
    if (currentCategoryDelete != null) {
        var deleting by remember(currentCategoryDelete) { mutableStateOf(false) }
        val questionCount = currentCategoryDelete.totalQuestionCount
        val hasSubcategories = currentCategoryDelete.children.isNotEmpty()
        AlertDialog(
            onDismissRequest = { if (!deleting) categoryToDelete = null },
            icon = {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete this category?") },
            text = {
                Text(
                    "Delete \"${currentCategoryDelete.name}\"" +
                        (if (hasSubcategories) " and all of its subcategories" else "") +
                        "? " +
                        (if (questionCount == 0) {
                            "This cannot be undone."
                        } else if (questionCount == 1) {
                            "Its 1 question will also be deleted. This cannot be undone."
                        } else {
                            "All $questionCount questions inside it will also be deleted. " +
                                "This cannot be undone."
                        })
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        scope.launch {
                            val error = viewModel.deleteCategory(currentCategoryDelete.id)
                            deleting = false
                            categoryToDelete = null
                            snackbarHostState.showSnackbar(error ?: "Category deleted.")
                        }
                    }
                ) {
                    if (deleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!deleting) categoryToDelete = null },
                    enabled = !deleting
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    val currentMoveQuestions = moveQuestionsTarget
    if (currentMoveQuestions != null && paper != null) {
        MoveQuestionsDialog(
            paper = paper,
            questionIds = currentMoveQuestions,
            onDismiss = { moveQuestionsTarget = null },
            onMove = { targetCategoryId ->
                val ids = currentMoveQuestions
                moveQuestionsTarget = null
                selectionMode = false
                selectedQuestionIds = emptySet()
                scope.launch {
                    val error = if (ids.size == 1) {
                        viewModel.moveQuestionToCategory(ids.first(), targetCategoryId)
                    } else {
                        viewModel.moveQuestionsToCategory(ids, targetCategoryId)
                    }
                    snackbarHostState.showSnackbar(
                        error ?: if (ids.size == 1) {
                            "Question moved."
                        } else {
                            "${ids.size} questions moved."
                        }
                    )
                }
            }
        )
    }

    val currentMoveCategory = categoryToMove
    if (currentMoveCategory != null && paper != null) {
        MoveCategoryDialog(
            papers = allPapers.filterNot { it.id == paper.id },
            category = currentMoveCategory,
            onDismiss = { categoryToMove = null },
            onMove = { targetPaperId ->
                val category = currentMoveCategory
                categoryToMove = null
                scope.launch {
                    val error = viewModel.moveCategoryToPaper(category.id, targetPaperId)
                    snackbarHostState.showSnackbar(
                        error ?: "Moved \"${category.name}\" to the selected paper."
                    )
                }
            }
        )
    }

    val currentReparentCategory = categoryToReparent
    if (currentReparentCategory != null && paper != null) {
        val excluded = currentReparentCategory.subtreeCategoryIds()
        ReparentCategoryDialog(
            source = currentReparentCategory,
            targets = paper.flattenCategories().filter { it.category.id !in excluded },
            onDismiss = { categoryToReparent = null },
            onMove = { targetParentId ->
                val source = currentReparentCategory
                categoryToReparent = null
                scope.launch {
                    val error = viewModel.moveCategoryToParent(
                        source.id,
                        targetParentId
                    )
                    snackbarHostState.showSnackbar(
                        error ?: "Moved \"${source.name}\"."
                    )
                }
            }
        )
    }

    val currentMergeCategory = categoryToMerge
    if (currentMergeCategory != null && paper != null) {
        val excluded = currentMergeCategory.subtreeCategoryIds()
        MergeCategoryDialog(
            source = currentMergeCategory,
            targets = paper.flattenCategories().filter { it.category.id !in excluded },
            onDismiss = { categoryToMerge = null },
            onMerge = { target ->
                val source = currentMergeCategory
                categoryToMerge = null
                scope.launch {
                    val error = viewModel.mergeCategories(source.id, target.id)
                    snackbarHostState.showSnackbar(
                        error ?: "Merged \"${source.name}\" into \"${target.name}\"."
                    )
                }
            }
        )
    }

    if (confirmBulkDelete) {
        var deleting by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!deleting) confirmBulkDelete = false },
            icon = {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete selected questions?") },
            text = {
                Text(
                    "Delete ${selectedQuestionIds.size} " +
                        (if (selectedQuestionIds.size == 1) "question" else "questions") +
                        "? This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        val ids = selectedQuestionIds
                        scope.launch {
                            val error = viewModel.deleteQuestions(ids)
                            deleting = false
                            confirmBulkDelete = false
                            selectionMode = false
                            selectedQuestionIds = emptySet()
                            snackbarHostState.showSnackbar(
                                error ?: if (ids.size == 1) {
                                    "Question deleted."
                                } else {
                                    "${ids.size} questions deleted."
                                }
                            )
                        }
                    }
                ) {
                    if (deleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!deleting) confirmBulkDelete = false },
                    enabled = !deleting
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (confirmMergeDuplicates && paper != null) {
        var merging by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!merging) confirmMergeDuplicates = false },
            title = { Text("Merge duplicate categories?") },
            text = {
                Text(
                    "Sibling categories with the same name will be combined. " +
                        "Their questions and subcategories are kept under the first " +
                        "category, and the empty duplicates are removed."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !merging,
                    onClick = {
                        merging = true
                        scope.launch {
                            val result = viewModel.mergeDuplicateCategories()
                            merging = false
                            confirmMergeDuplicates = false
                            snackbarHostState.showSnackbar(
                                result.error ?: if (result.merged == 0) {
                                    "No duplicate category names were found."
                                } else {
                                    "Merged ${result.merged} duplicate " +
                                        (if (result.merged == 1) "category." else "categories.")
                                }
                            )
                        }
                    }
                ) {
                    if (merging) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Merge")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!merging) confirmMergeDuplicates = false },
                    enabled = !merging
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRenamePaper && paper != null) {
        NameEditorDialog(
            title = "Rename paper",
            label = "Paper name",
            initialValue = paper.name,
            confirmLabel = "Save",
            onDismiss = { showRenamePaper = false },
            onSave = { name -> viewModel.renamePaper(paper.id, name) },
            onSaved = {
                showRenamePaper = false
                scope.launch { snackbarHostState.showSnackbar("Paper renamed.") }
            }
        )
    }
}

@Composable
private fun SelectionBottomBar(
    selectedCount: Int,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (selectedCount == 1) "1 selected" else "$selectedCount selected",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onMove, enabled = selectedCount > 0) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Move")
            }
            TextButton(onClick = onDelete, enabled = selectedCount > 0) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(4.dp))
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun MoveQuestionsDialog(
    paper: PaperDetail,
    questionIds: Set<String>,
    onDismiss: () -> Unit,
    onMove: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var targetId by remember { mutableStateOf<String?>(null) }
    val categories = paper.flattenCategories().filter {
        query.isBlank() || it.displayName.contains(query, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (questionIds.size == 1) {
                    "Move question"
                } else {
                    "Move ${questionIds.size} questions"
                }
            )
        },
        text = {
            Column {
                Text(
                    "Choose the destination category.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Filter categories") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(categories.size) { index ->
                        val node = categories[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { targetId = node.category.id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = targetId == node.category.id,
                                onClick = { targetId = node.category.id }
                            )
                            Text(
                                node.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { targetId?.let(onMove) },
                enabled = targetId != null
            ) {
                Text("Move")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun MoveCategoryDialog(
    papers: List<PaperSummary>,
    category: CategoryDetail,
    onDismiss: () -> Unit,
    onMove: (String) -> Unit
) {
    var targetId by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move \"${category.name}\"") },
        text = {
            Column {
                Text(
                    "The category, its subcategories, and all of its questions will move " +
                        "to the top level of the selected paper.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                if (papers.isEmpty()) {
                    Text(
                        "There is no other paper to move this category to.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(papers.size) { index ->
                            val paper = papers[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { targetId = paper.id }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = targetId == paper.id,
                                    onClick = { targetId = paper.id }
                                )
                                Text(
                                    paper.name,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { targetId?.let(onMove) },
                enabled = targetId != null
            ) {
                Text("Move")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ReparentCategoryDialog(
    source: CategoryDetail,
    targets: List<CategoryNode>,
    onDismiss: () -> Unit,
    onMove: (String?) -> Unit
) {
    var targetParentId by remember { mutableStateOf<String?>(null) }
    var topLevelSelected by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move \"${source.name}\"") },
        text = {
            Column {
                Text(
                    "Choose a new parent. The category keeps its questions and " +
                        "subcategories.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            topLevelSelected = true
                            targetParentId = null
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = topLevelSelected,
                        onClick = {
                            topLevelSelected = true
                            targetParentId = null
                        }
                    )
                    Text("Top level", style = MaterialTheme.typography.bodyMedium)
                }
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(targets.size) { index ->
                        val node = targets[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    topLevelSelected = false
                                    targetParentId = node.category.id
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !topLevelSelected &&
                                    targetParentId == node.category.id,
                                onClick = {
                                    topLevelSelected = false
                                    targetParentId = node.category.id
                                }
                            )
                            Text(
                                node.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onMove(targetParentId) },
                enabled = topLevelSelected || targetParentId != null
            ) {
                Text("Move")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun MergeCategoryDialog(
    source: CategoryDetail,
    targets: List<CategoryNode>,
    onDismiss: () -> Unit,
    onMerge: (CategoryDetail) -> Unit
) {
    var target by remember { mutableStateOf<CategoryDetail?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge \"${source.name}\"") },
        text = {
            Column {
                Text(
                    "Questions and subcategories from the source category are moved " +
                        "into the destination, then the empty source category is removed.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(targets.size) { index ->
                        val node = targets[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { target = node.category }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = target?.id == node.category.id,
                                onClick = { target = node.category }
                            )
                            Text(
                                node.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { target?.let(onMerge) },
                enabled = target != null
            ) {
                Text("Merge")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun NameEditorDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onSave: suspend (String) -> String?,
    onSaved: () -> Unit
) {
    var name by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(label) },
                singleLine = true,
                enabled = !saving,
                isError = error != null,
                supportingText = error?.let { message -> { Text(message) } }
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !saving,
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        val saveError = onSave(name)
                        if (saveError == null) {
                            onSaved()
                        } else {
                            error = saveError
                            saving = false
                        }
                    }
                }
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(confirmLabel)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

@Composable
private fun CategoryMenu(
    categoryName: String,
    onAddSubcategory: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onReparent: () -> Unit,
    onMerge: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    var open by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = "Options for $categoryName"
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Add subcategory") },
                leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                onClick = {
                    open = false
                    onAddSubcategory()
                }
            )
            DropdownMenuItem(
                text = { Text("Rename category") },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = {
                    open = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = { Text("Move to another paper") },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                },
                onClick = {
                    open = false
                    onMove()
                }
            )
            DropdownMenuItem(
                text = { Text("Move under another category") },
                leadingIcon = {
                    Icon(Icons.Outlined.ArrowDownward, contentDescription = null)
                },
                onClick = {
                    open = false
                    onReparent()
                }
            )
            DropdownMenuItem(
                text = { Text("Merge into another category") },
                leadingIcon = {
                    Icon(Icons.Outlined.ArrowDownward, contentDescription = null)
                },
                onClick = {
                    open = false
                    onMerge()
                }
            )
            DropdownMenuItem(
                text = { Text("Move up") },
                leadingIcon = {
                    Icon(Icons.Outlined.ArrowUpward, contentDescription = null)
                },
                onClick = {
                    open = false
                    onMoveUp()
                }
            )
            DropdownMenuItem(
                text = { Text("Move down") },
                leadingIcon = {
                    Icon(Icons.Outlined.ArrowDownward, contentDescription = null)
                },
                onClick = {
                    open = false
                    onMoveDown()
                }
            )
            DropdownMenuItem(
                text = {
                    Text("Delete category", color = MaterialTheme.colorScheme.error)
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    open = false
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun QuestionsContent(
    paper: PaperDetail,
    selectionMode: Boolean,
    selectedQuestionIds: Set<String>,
    onToggleQuestionSelected: (String) -> Unit,
    onMoveQuestion: (QuestionDetail) -> Unit,
    onMoveCategory: (CategoryDetail) -> Unit,
    onReparentCategory: (CategoryDetail) -> Unit,
    onMergeCategory: (CategoryDetail) -> Unit,
    onReorderQuestion: (QuestionDetail, Int) -> Unit,
    onReorderCategory: (CategoryDetail, Int) -> Unit,
    onEditQuestion: (CategoryNode, QuestionDetail) -> Unit,
    onAddQuestion: (CategoryNode) -> Unit,
    onAddSubcategory: (CategoryNode) -> Unit,
    onRenameCategory: (CategoryDetail) -> Unit,
    onDeleteCategory: (CategoryDetail) -> Unit,
    expandedCategoryIds: Set<String>,
    onToggleCategoryExpanded: (String) -> Unit,
    focusedCategoryId: String?,
    focusedQuestionId: String?,
    modifier: Modifier = Modifier
) {
    val categoryNodes = paper.flattenCategories()
    val listState = rememberLazyListState()
    var lastScrolledFocus by remember { mutableStateOf<String?>(null) }
    val targetItemIndex = run {
        var index = 1
        if (categoryNodes.isEmpty()) index++
        for (node in categoryNodes) {
            val parentIds = node.idPath.dropLast(1)
            if (parentIds.any { it !in expandedCategoryIds }) continue
            val category = node.category
            if (focusedQuestionId == null && category.id == focusedCategoryId) {
                return@run index
            }
            index++
            if (category.id in expandedCategoryIds) {
                if (category.questions.isEmpty() && category.children.isEmpty()) index++
                category.questions.forEach { question ->
                    if (question.id == focusedQuestionId) return@run index
                    index++
                }
            }
        }
        null
    }

    LaunchedEffect(focusedCategoryId, focusedQuestionId, targetItemIndex) {
        val focusKey = focusedQuestionId ?: focusedCategoryId ?: return@LaunchedEffect
        if (targetItemIndex == null || lastScrolledFocus == focusKey) {
            return@LaunchedEffect
        }
        lastScrolledFocus = focusKey
        listState.scrollToItem(targetItemIndex)
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 10.dp,
            bottom = 104.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    paper.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${categoryCountLabel(paper.categoryCount)}  \u00b7  " +
                        questionCountLabel(paper.questionCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap a question to edit its text or answer. Categories start " +
                        "collapsed; use the arrow to show one. Categories can be " +
                        "nested using the category menu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (categoryNodes.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        "This paper has no categories yet. Tap \"Add category\" to create " +
                            "one, then add questions or subcategories to it.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        }

        categoryNodes.forEach { node ->
            val parentIds = node.idPath.dropLast(1)
            if (parentIds.any { it !in expandedCategoryIds }) return@forEach

            val category = node.category
            val expanded = category.id in expandedCategoryIds
            item(key = "header-${category.id}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = (node.depth.coerceAtMost(4) * 18).dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onToggleCategoryExpanded(category.id) }) {
                        Icon(
                            if (expanded) {
                                Icons.Outlined.KeyboardArrowDown
                            } else {
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight
                            },
                            contentDescription = if (expanded) {
                                "Collapse ${category.name}"
                            } else {
                                "Expand ${category.name}"
                            }
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            category.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val ownQuestions = category.questions.size
                        val totalQuestions = category.totalQuestionCount
                        Text(
                            when {
                                ownQuestions > 0 && totalQuestions > ownQuestions ->
                                    "$ownQuestions here \u00b7 $totalQuestions including " +
                                        "subcategories"
                                ownQuestions == 1 -> "1 question"
                                ownQuestions > 1 -> "$ownQuestions questions"
                                totalQuestions > 0 ->
                                    "$totalQuestions questions in subcategories"
                                else -> "No questions yet"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ReorderHandle(
                        contentDescription = "Drag ${category.name} to reorder",
                        onMove = { delta -> onReorderCategory(category, delta) }
                    )
                    TextButton(onClick = { onAddQuestion(node) }) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                    CategoryMenu(
                        categoryName = category.name,
                        onAddSubcategory = { onAddSubcategory(node) },
                        onRename = { onRenameCategory(category) },
                        onMove = { onMoveCategory(category) },
                        onReparent = { onReparentCategory(category) },
                        onMerge = { onMergeCategory(category) },
                        onMoveUp = { onReorderCategory(category, -1) },
                        onMoveDown = { onReorderCategory(category, 1) },
                        onDelete = { onDeleteCategory(category) }
                    )
                }
            }

            if (expanded) {
                if (category.questions.isEmpty() && category.children.isEmpty()) {
                    item(key = "empty-${category.id}") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                "No questions in this category yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = category.questions,
                    key = { _, question -> question.id }
                ) { index, question ->
                    QuestionCard(
                        number = index + 1,
                        question = question,
                        selectionMode = selectionMode,
                        selected = question.id in selectedQuestionIds,
                        onToggleSelected = { onToggleQuestionSelected(question.id) },
                        onEdit = { onEditQuestion(node, question) },
                        onMove = { onMoveQuestion(question) },
                        onReorder = { delta -> onReorderQuestion(question, delta) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(
    number: Int,
    question: QuestionDetail,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onReorder: (Int) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        onClick = if (selectionMode) onToggleSelected else onEdit,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelected() },
                        modifier = Modifier.semantics {
                            contentDescription = "Select question $number"
                        }
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "$number",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                RichTextContent(
                    text = question.questionText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                if (!selectionMode) {
                    ReorderHandle(
                        contentDescription = "Drag question $number to reorder",
                        onMove = onReorder
                    )
                    Box {
                        IconButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = "Options for question $number"
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Edit, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Move to another category") },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onMove()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Move up") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.ArrowUpward, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onReorder(-1)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Move down") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.ArrowDownward, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onReorder(1)
                                }
                            )
                        }
                    }
                }
            }

            AttachedImages(
                images = question.questionImages,
                modifier = Modifier.padding(top = 10.dp),
                description = "Question image"
            )

            Spacer(Modifier.height(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Answer",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(3.dp))
                    RichTextContent(
                        text = question.answerText.ifBlank { "(No answer provided)" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    AttachedImages(
                        images = question.answerImages,
                        modifier = Modifier.padding(top = 8.dp),
                        description = "Answer image"
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorImageSection(
    title: String,
    images: List<ImageContent>,
    importing: Boolean,
    enabled: Boolean,
    onAdd: () -> Unit,
    onRemove: (ImageContent) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = onAdd,
                enabled = enabled && !importing
            ) {
                if (importing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Add images")
                }
            }
        }
        if (images.isEmpty()) {
            Text(
                "No images attached.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                images.forEachIndexed { index, image ->
                    EditorImageThumb(
                        image = image,
                        index = index,
                        enabled = enabled,
                        onRemove = { onRemove(image) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorImageThumb(
    image: ImageContent,
    index: Int,
    enabled: Boolean,
    onRemove: () -> Unit
) {
    val bitmap = remember(image.id, image.bytes) {
        decodeImageForDisplay(image.bytes, maxDimension = 240)
            ?.asImageBitmap()
    }
    Box(modifier = Modifier.size(86.dp)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Attached image ${index + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Image", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        IconButton(
            onClick = onRemove,
            enabled = enabled,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(30.dp)
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Remove image ${index + 1}",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun QuestionEditorDialog(
    state: QuestionEditorState,
    onDismiss: () -> Unit,
    onSaved: (Boolean) -> Unit,
    onRequestDelete: (QuestionEditorState) -> Unit,
    viewModel: MainViewModel
) {
    var questionText by rememberSaveable(state.questionId) {
        mutableStateOf(state.questionText)
    }
    var answerText by rememberSaveable(state.questionId) {
        mutableStateOf(state.answerText)
    }
    var questionTouched by rememberSaveable(state.questionId) { mutableStateOf(false) }
    var questionImages by remember(state.questionId) {
        mutableStateOf(state.questionImages)
    }
    var answerImages by remember(state.questionId) {
        mutableStateOf(state.answerImages)
    }
    var importingQuestionImages by remember { mutableStateOf(false) }
    var importingAnswerImages by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val questionBlank = questionText.isBlank()

    val questionImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            importingQuestionImages = true
            saveError = null
            scope.launch {
                val prepared = uris.mapNotNull { uri -> viewModel.prepareImage(uri) }
                questionImages = questionImages + prepared
                if (prepared.size < uris.size) {
                    saveError = "Some selected images could not be added."
                }
                importingQuestionImages = false
            }
        }
    }
    val answerImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            importingAnswerImages = true
            saveError = null
            scope.launch {
                val prepared = uris.mapNotNull { uri -> viewModel.prepareImage(uri) }
                answerImages = answerImages + prepared
                if (prepared.size < uris.size) {
                    saveError = "Some selected images could not be added."
                }
                importingAnswerImages = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Text(if (state.isNew) "Add question" else "Edit question")
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    state.categoryName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = questionText,
                    onValueChange = {
                        questionText = it
                        questionTouched = true
                        saveError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Question") },
                    minLines = 3,
                    maxLines = 7,
                    enabled = !saving,
                    isError = questionTouched && questionBlank,
                    supportingText = if (questionTouched && questionBlank) {
                        { Text("Question text is required.") }
                    } else {
                        null
                    }
                )
                Spacer(Modifier.height(8.dp))
                EditorImageSection(
                    title = "Question images",
                    images = questionImages,
                    importing = importingQuestionImages,
                    enabled = !saving,
                    onAdd = { questionImageLauncher.launch("image/*") },
                    onRemove = { image ->
                        questionImages = questionImages.filterNot { it.id == image.id }
                    }
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = answerText,
                    onValueChange = {
                        answerText = it
                        saveError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Answer (optional)") },
                    minLines = 4,
                    maxLines = 9,
                    enabled = !saving,
                    supportingText = {
                        Text(
                            "Answers can be left blank. Use H_2O, x^2, \\frac{a}{b}, " +
                                "and -> for formulas and chemical equations."
                        )
                    }
                )
                Spacer(Modifier.height(8.dp))
                EditorImageSection(
                    title = "Answer images",
                    images = answerImages,
                    importing = importingAnswerImages,
                    enabled = !saving,
                    onAdd = { answerImageLauncher.launch("image/*") },
                    onRemove = { image ->
                        answerImages = answerImages.filterNot { it.id == image.id }
                    }
                )
                saveError?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !questionBlank && !saving &&
                    !importingQuestionImages && !importingAnswerImages,
                onClick = {
                    questionTouched = true
                    saving = true
                    saveError = null
                    scope.launch {
                        val error = viewModel.saveQuestion(
                            categoryId = state.categoryId,
                            questionId = state.questionId,
                            questionText = questionText,
                            answerText = answerText,
                            questionImages = questionImages,
                            answerImages = answerImages
                        )
                        if (error == null) {
                            onSaved(state.isNew)
                        } else {
                            saveError = error
                            saving = false
                        }
                    }
                }
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            Row {
                if (!state.isNew) {
                    TextButton(
                        enabled = !saving,
                        onClick = { onRequestDelete(state) }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(
                    enabled = !saving,
                    onClick = onDismiss
                ) {
                    Text("Cancel")
                }
            }
        }
    )
}
