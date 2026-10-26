package com.paperpractice.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paperpractice.data.CategoryDetail
import com.paperpractice.data.CategoryNode
import com.paperpractice.data.PaperDetail
import com.paperpractice.data.QuestionDetail
import com.paperpractice.ui.util.categoryCountLabel
import com.paperpractice.ui.util.questionCountLabel
import kotlinx.coroutines.launch

private data class QuestionEditorState(
    val categoryId: String,
    val categoryName: String,
    val questionId: String?,
    val questionText: String,
    val answerText: String
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Questions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
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
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (paper != null) {
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
                    onEditQuestion = { node, question ->
                        editor = QuestionEditorState(
                            categoryId = node.category.id,
                            categoryName = node.displayName,
                            questionId = question.id,
                            questionText = question.questionText,
                            answerText = question.answerText
                        )
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
    onEditQuestion: (CategoryNode, QuestionDetail) -> Unit,
    onAddQuestion: (CategoryNode) -> Unit,
    onAddSubcategory: (CategoryNode) -> Unit,
    onRenameCategory: (CategoryDetail) -> Unit,
    onDeleteCategory: (CategoryDetail) -> Unit,
    expandedCategoryIds: Set<String>,
    onToggleCategoryExpanded: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryNodes = paper.flattenCategories()

    LazyColumn(
        modifier = modifier,
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
                        .padding(start = (node.depth * 18).dp, top = 8.dp),
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
                        onEdit = { onEditQuestion(node, question) }
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
    onEdit: () -> Unit
) {
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
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
                Spacer(Modifier.width(10.dp))
                Text(
                    text = question.questionText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "Edit question $number",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

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
                    Text(
                        question.answerText.ifBlank { "(No answer provided)" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
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
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val questionBlank = questionText.isBlank()

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
                    supportingText = { Text("Answers can be left blank.") }
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
                enabled = !questionBlank && !saving,
                onClick = {
                    questionTouched = true
                    saving = true
                    saveError = null
                    scope.launch {
                        val error = viewModel.saveQuestion(
                            categoryId = state.categoryId,
                            questionId = state.questionId,
                            questionText = questionText,
                            answerText = answerText
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
