package com.paperpractice.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperpractice.data.PaperSummary
import com.paperpractice.ui.util.categoryCountLabel
import com.paperpractice.ui.util.formatImportedAt
import com.paperpractice.ui.util.questionCountLabel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenPaper: (String) -> Unit,
    onOpenQuestions: (String) -> Unit
) {
    val papers by viewModel.papers.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var paperToDelete by remember { mutableStateOf<PaperSummary?>(null) }
    var showCreatePaper by remember { mutableStateOf(false) }

    val openDocumentContract = remember { ActivityResultContracts.OpenDocument() }
    val importLauncher = rememberLauncherForActivityResult(
        contract = openDocumentContract
    ) { uri ->
        if (uri != null) viewModel.importPaper(uri)
    }

    LaunchedEffect(viewModel.homeMessage) {
        val message = viewModel.homeMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearHomeMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Paper Practice", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Import, practise, and export",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showCreatePaper = true }) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = "Create a new paper"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (!viewModel.isImporting) {
                        importLauncher.launch(arrayOf("*/*"))
                    }
                },
                icon = {
                    if (viewModel.isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                    }
                },
                text = {
                    Text(if (viewModel.isImporting) "Importing..." else "Import JSON")
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (viewModel.isImporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (papers.isEmpty()) {
                EmptyHome(
                    onImport = {
                        if (!viewModel.isImporting) {
                            importLauncher.launch(arrayOf("*/*"))
                        }
                    },
                    onCreatePaper = { showCreatePaper = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 104.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Column(modifier = Modifier.padding(bottom = 4.dp)) {
                            Text(
                                "Your papers",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (papers.size == 1) "1 paper on this device" else {
                                    "${papers.size} papers on this device"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(papers, key = { it.id }) { paper ->
                        PaperCard(
                            paper = paper,
                            onClick = { onOpenPaper(paper.id) },
                            onDelete = { paperToDelete = paper }
                        )
                    }
                }
            }
        }
    }

    val importError = viewModel.importError
    if (importError != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissImportError,
            icon = {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Import failed") },
            text = { Text(importError) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissImportError) { Text("OK") }
            }
        )
    }

    paperToDelete?.let { paper ->
        AlertDialog(
            onDismissRequest = { paperToDelete = null },
            icon = {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete this paper?") },
            text = {
                Text(
                    "\"${paper.name}\" and its ${questionCountLabel(paper.questionCount)} " +
                        "will be removed from this device. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePaper(paper.id, paper.name)
                        paperToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { paperToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showCreatePaper) {
        CreatePaperDialog(
            viewModel = viewModel,
            onDismiss = { showCreatePaper = false },
            onCreated = { paperId ->
                showCreatePaper = false
                onOpenQuestions(paperId)
            }
        )
    }
}

@Composable
private fun CreatePaperDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("New paper") },
        text = {
            Column {
                Text(
                    "Give the paper a name. You can add categories and questions next.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Paper name") },
                    singleLine = true,
                    enabled = !saving,
                    isError = error != null,
                    supportingText = error?.let { message -> { Text(message) } }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !saving,
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        when (val result = viewModel.createPaper(name)) {
                            is CreatePaperResult.Success -> onCreated(result.paperId)
                            is CreatePaperResult.Failure -> {
                                error = result.message
                                saving = false
                            }
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
                    Text("Create")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

@Composable
private fun PaperCard(
    paper: PaperSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = paper.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${categoryCountLabel(paper.categoryCount)}  \u00b7  " +
                        questionCountLabel(paper.questionCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Imported ${formatImportedAt(paper.importedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete ${paper.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyHome(onImport: () -> Unit, onCreatePaper: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.size(104.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(52.dp)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "No papers yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Import a JSON paper, or create one from scratch and add your own " +
                    "categories and questions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onImport) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Import a JSON paper")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onCreatePaper) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Create a paper")
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Everything is stored on this device and works offline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
