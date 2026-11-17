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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperpractice.data.Tag
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val filterIds = viewModel.tagFilterIds
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var newTagName by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var tagToRename by remember { mutableStateOf<Tag?>(null) }
    var tagToDelete by remember { mutableStateOf<Tag?>(null) }
    var tagToMerge by remember { mutableStateOf<Tag?>(null) }
    var confirmClearUnused by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.tagMessage) {
        val message = viewModel.tagMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        if (viewModel.tagMessage == message) {
            viewModel.clearTagMessage()
        }
    }

    // createTag also runs from the question editor, where no snackbar observes
    // tagMessage. Clear any such leftover on exit so it never surfaces on a
    // later visit to this screen.
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.clearTagMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage tags") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { confirmClearUnused = true }) {
                        Text("Clear unused")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("New tag name") },
                    singleLine = true,
                    enabled = !adding
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        adding = true
                        scope.launch {
                            val id = viewModel.createTag(newTagName)
                            if (id != null) newTagName = ""
                            adding = false
                        }
                    },
                    enabled = newTagName.isNotBlank() && !adding
                ) {
                    Text("Add")
                }
            }

            Text(
                "Tap a tag to include it when generating tests or searching. " +
                    "Use the menu to rename, merge or delete it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (tags.isEmpty()) {
                Text(
                    "No tags yet. Add one above, then select it in a question editor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(tags, key = { it.id }) { tag ->
                    TagRow(
                        tag = tag,
                        inFilter = tag.id in filterIds,
                        onToggleFilter = {
                            val next = if (tag.id in filterIds) {
                                filterIds - tag.id
                            } else {
                                filterIds + tag.id
                            }
                            viewModel.setTagFilter(next)
                        },
                        onRename = { tagToRename = tag },
                        onMerge = { tagToMerge = tag },
                        onDelete = { tagToDelete = tag }
                    )
                }
            }
        }
    }

    tagToRename?.let { tag ->
        TagNameDialog(
            title = "Rename tag",
            label = "Tag name",
            initialValue = tag.name,
            confirmLabel = "Rename",
            onDismiss = { tagToRename = null },
            onSave = { name ->
                val renamed = viewModel.renameTag(tag.id, name)
                if (renamed) null else "Could not rename that tag. Check the name and try again."
            },
            onSaved = { tagToRename = null }
        )
    }

    tagToDelete?.let { tag ->
        AlertDialog(
            onDismissRequest = { tagToDelete = null },
            icon = {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete tag?") },
            text = {
                Text(
                    "Remove \"${tag.name}\" from every question that uses it? " +
                        "The questions themselves are kept."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        tagToDelete = null
                        scope.launch { viewModel.deleteTag(tag.id) }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { tagToDelete = null }) { Text("Cancel") }
            }
        )
    }

    tagToMerge?.let { source ->
        MergeTagDialog(
            source = source,
            targets = tags.filter { it.id != source.id },
            onDismiss = { tagToMerge = null },
            onMerge = { target ->
                tagToMerge = null
                scope.launch { viewModel.mergeTags(source.id, target.id) }
            }
        )
    }

    if (confirmClearUnused) {
        AlertDialog(
            onDismissRequest = { confirmClearUnused = false },
            title = { Text("Clear unused tags?") },
            text = {
                Text("Remove every tag that is not attached to any question.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearUnused = false
                        scope.launch { viewModel.clearUnusedTags() }
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearUnused = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TagRow(
    tag: Tag,
    inFilter: Boolean,
    onToggleFilter: () -> Unit,
    onRename: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = inFilter,
            onClick = onToggleFilter,
            label = { Text(tag.name) },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
        )
        Spacer(Modifier.width(4.dp))
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = "Options for ${tag.name}"
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Edit, contentDescription = null)
                    },
                    onClick = {
                        menuOpen = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Merge into another tag") },
                    onClick = {
                        menuOpen = false
                        onMerge()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun MergeTagDialog(
    source: Tag,
    targets: List<Tag>,
    onDismiss: () -> Unit,
    onMerge: (Tag) -> Unit
) {
    var target by remember { mutableStateOf<Tag?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge \"${source.name}\"") },
        text = {
            Column {
                Text(
                    "Questions tagged \"${source.name}\" will also be tagged with " +
                        "the destination, then the source tag is removed.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                if (targets.isEmpty()) {
                    Text(
                        "There is no other tag to merge into.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(targets, key = { it.id }) { tag ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .selectable(
                                        selected = target?.id == tag.id,
                                        role = Role.RadioButton,
                                        onClick = { target = tag }
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = target?.id == tag.id,
                                    onClick = null
                                )
                                Text(tag.name, style = MaterialTheme.typography.bodyMedium)
                            }
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
private fun TagNameDialog(
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
    var validationError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    validationError = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { validationError?.let { error(it) } },
                label = { Text(label) },
                singleLine = true,
                enabled = !saving,
                isError = validationError != null,
                supportingText = validationError?.let { message -> { Text(message) } }
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !saving,
                onClick = {
                    saving = true
                    validationError = null
                    scope.launch {
                        val saveError = onSave(name)
                        if (saveError == null) {
                            onSaved()
                        } else {
                            validationError = saveError
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
