package com.paperpractice.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paperpractice.data.CategoryDetail
import com.paperpractice.data.PaperDetail
import com.paperpractice.ui.util.categoryCountLabel
import com.paperpractice.ui.util.questionCountLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestConfigScreen(
    viewModel: MainViewModel,
    paperId: String,
    onBack: () -> Unit,
    onStartTest: () -> Unit,
    onExport: () -> Unit
) {
    LaunchedEffect(paperId) {
        viewModel.loadPaper(paperId)
    }

    val paper = viewModel.selectedPaper?.takeIf { it.id == paperId }
    val summary = viewModel.configSummary()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configure test") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onExport) {
                        Icon(
                            Icons.Outlined.FileDownload,
                            contentDescription = "Export"
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (paper != null) {
                TestSummaryBar(
                    summary = summary,
                    onStartTest = {
                        if (viewModel.startTest()) onStartTest()
                    }
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
                LoadError(
                    message = viewModel.paperLoadError ?: "This paper could not be loaded.",
                    onBack = onBack,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 20.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            PaperHeader(paper)
                        }

                        if (paper.categories.isEmpty()) {
                            item {
                                EmptyCategories()
                            }
                        } else {
                            item {
                                SelectionActions(
                                    onSelectAll = viewModel::selectAllCategories,
                                    onClear = viewModel::clearCategorySelection
                                )
                            }
                            items(paper.categories, key = { it.id }) { category ->
                                CategoryRow(
                                    category = category,
                                    selected = category.id in viewModel.selectedCategoryIds,
                                    count = viewModel.requestedCounts[category.id].orEmpty(),
                                    error = summary.countErrors[category.id],
                                    onToggle = { viewModel.toggleCategory(category.id) },
                                    onCountChange = {
                                        viewModel.updateCount(category.id, it)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val configError = viewModel.configError
    if (configError != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConfigError,
            title = { Text("Cannot generate test") },
            text = { Text(configError) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissConfigError) { Text("OK") }
            }
        )
    }
}

@Composable
private fun PaperHeader(paper: PaperDetail) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 6.dp)
    ) {
        Text(
            text = paper.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${categoryCountLabel(paper.categories.size)}  \u00b7  " +
                questionCountLabel(paper.questionCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Choose one or more categories and enter how many questions " +
                "you want from each. Questions are picked at random every time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SelectionActions(onSelectAll: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Categories",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onSelectAll) { Text("Select all") }
        TextButton(onClick = onClear) { Text("Clear") }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun CategoryRow(
    category: CategoryDetail,
    selected: Boolean,
    count: String,
    error: String?,
    onToggle: () -> Unit,
    onCountChange: (String) -> Unit
) {
    val available = category.questions.size
    val hasQuestions = available > 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggle() },
                    enabled = hasQuestions
                )
                Spacer(Modifier.width(4.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = hasQuestions, onClick = onToggle)
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (hasQuestions) {
                            "$available available"
                        } else {
                            "No questions in this category"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = count,
                    onValueChange = onCountChange,
                    modifier = Modifier.width(104.dp),
                    enabled = selected && hasQuestions,
                    singleLine = true,
                    isError = error != null,
                    label = { Text("Count") },
                    supportingText = error?.let { message ->
                        { Text(message) }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }
    }
}

@Composable
private fun TestSummaryBar(
    summary: ConfigSummary,
    onStartTest: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 10.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Total questions",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${summary.totalQuestions}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (summary.selectedCategoryCount == 1) {
                            "1 category selected"
                        } else {
                            "${summary.selectedCategoryCount} categories selected"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onStartTest,
                    enabled = summary.canGenerate,
                    modifier = Modifier.height(52.dp)
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Generate test")
                }
            }
            summary.hint?.let { hint ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (summary.countErrors.isNotEmpty()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyCategories() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "No categories found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Re-import this paper with at least one category and question to " +
                    "generate a test.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoadError(message: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
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
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onBack) { Text("Back to papers") }
        }
    }
}
