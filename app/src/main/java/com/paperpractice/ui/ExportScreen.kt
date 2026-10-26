package com.paperpractice.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.paperpractice.data.PaperDetail
import com.paperpractice.export.ExportFeedback
import com.paperpractice.export.ExportFormat
import com.paperpractice.export.ExportScope
import com.paperpractice.ui.util.CreateDocumentContract
import com.paperpractice.ui.util.categoryCountLabel
import com.paperpractice.ui.util.questionCountLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val paper = viewModel.selectedPaper
    val currentSession = viewModel.session?.takeIf { it.paperId == paper?.id }
    val selectedCategories = viewModel.selectedCategoryIds

    val initialScope = when {
        currentSession != null -> ExportScope.CURRENT_TEST
        selectedCategories.isNotEmpty() -> ExportScope.SELECTED_CATEGORIES
        else -> ExportScope.ENTIRE_PAPER
    }
    var scope by rememberSaveable(paper?.id) { mutableStateOf(initialScope) }
    var format by rememberSaveable(paper?.id) { mutableStateOf(ExportFormat.HTML) }
    var includeAnswers by rememberSaveable(paper?.id) { mutableStateOf(true) }
    var answersAtEnd by rememberSaveable(paper?.id) { mutableStateOf(false) }
    var fileName by rememberSaveable(paper?.id) {
        mutableStateOf(viewModel.suggestedFileName())
    }

    val createDocument = rememberLauncherForActivityResult(CreateDocumentContract) { uri ->
        if (uri == null) {
            viewModel.cancelExport()
        } else {
            viewModel.saveExport(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (paper == null) {
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
                        "Nothing to export",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Open a paper first, then choose what you want to export.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onBack) { Text("Go back") }
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column {
                Text(
                    paper.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "${categoryCountLabel(paper.categoryCount)}  \u00b7  " +
                        questionCountLabel(paper.questionCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionLabel("Export scope")
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                ScopeOption(
                    scope = ExportScope.CURRENT_TEST,
                    selectedScope = scope,
                    enabled = currentSession != null,
                    disabledHint = "Generate a practice test first.",
                    detail = currentSession?.let {
                        questionCountLabel(it.questions.size)
                    },
                    onSelect = { scope = ExportScope.CURRENT_TEST }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ScopeOption(
                    scope = ExportScope.SELECTED_CATEGORIES,
                    selectedScope = scope,
                    enabled = selectedCategories.isNotEmpty(),
                    disabledHint = "Select categories on the configuration screen.",
                    detail = if (selectedCategories.isEmpty()) null else {
                        if (selectedCategories.size == 1) "1 category" else {
                            "${selectedCategories.size} categories"
                        }
                    },
                    onSelect = { scope = ExportScope.SELECTED_CATEGORIES }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ScopeOption(
                    scope = ExportScope.ENTIRE_PAPER,
                    selectedScope = scope,
                    enabled = true,
                    disabledHint = null,
                    detail = null,
                    onSelect = { scope = ExportScope.ENTIRE_PAPER }
                )
            }

            SectionLabel("File format")
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                ExportFormat.entries.forEachIndexed { index, option ->
                    FormatOption(
                        format = option,
                        selected = format == option,
                        onSelect = { format = option }
                    )
                    if (index != ExportFormat.entries.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Include answers",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (!includeAnswers) {
                                "A question-only document will be created."
                            } else if (format.supportsAnswersAtEnd && answersAtEnd) {
                                "Answers are collected in a separate section at the end."
                            } else {
                                "Answers appear below each question."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = includeAnswers,
                        onCheckedChange = {
                            includeAnswers = it
                            if (!it) answersAtEnd = false
                        },
                        enabled = !viewModel.exportState.isExporting
                    )
                }
            }

            if (format.supportsAnswersAtEnd) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Put answers at the end",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                when {
                                    !includeAnswers ->
                                        "Turn on Include answers to use a separate answer section."
                                    answersAtEnd ->
                                        "Questions come first, then an answer section on a new page."
                                    else ->
                                        "Answers appear below each question."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = answersAtEnd,
                            onCheckedChange = { answersAtEnd = it },
                            enabled = includeAnswers && !viewModel.exportState.isExporting
                        )
                    }
                }
            }

            SectionLabel("File name")
            Column {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("File name") },
                    supportingText = {
                        Text("The .${format.extension} extension is added automatically.")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    enabled = !viewModel.exportState.isExporting
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "You will choose the save location in the system file picker.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            viewModel.exportState.feedback?.let { feedback ->
                ExportFeedbackCard(
                    feedback = feedback,
                    onDismiss = viewModel::clearExportFeedback
                )
            }

            Button(
                onClick = {
                    val request = viewModel.prepareExport(
                        scope = scope,
                        format = format,
                        includeAnswers = includeAnswers,
                        answersAtEnd = answersAtEnd,
                        rawFileName = fileName
                    )
                    if (request != null) createDocument.launch(request)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !viewModel.exportState.isExporting
            ) {
                if (viewModel.exportState.isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Saving...")
                } else {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Export ${format.label}")
                }
            }

            Text(
                text = "Exports are created on this device. No internet connection " +
                    "is used, and the source paper stays in the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ScopeOption(
    scope: ExportScope,
    selectedScope: ExportScope,
    enabled: Boolean,
    disabledHint: String?,
    detail: String?,
    onSelect: () -> Unit
) {
    val selected = scope == selectedScope
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = scope.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = if (enabled) {
                    scope.description + (detail?.let { "  ($it)" } ?: "")
                } else {
                    disabledHint ?: scope.description
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FormatOption(
    format: ExportFormat,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${format.label}  .${format.extension}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = format.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExportFeedbackCard(feedback: ExportFeedback, onDismiss: () -> Unit) {
    Surface(
        color = if (feedback.isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (feedback.isError) {
                    Icons.Outlined.ErrorOutline
                } else {
                    Icons.Outlined.CheckCircle
                },
                contentDescription = null,
                tint = if (feedback.isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = feedback.message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (feedback.isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Dismiss message",
                    tint = if (feedback.isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }
        }
    }
}
