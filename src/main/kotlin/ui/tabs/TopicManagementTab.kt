@file:OptIn(ExperimentalFoundationApi::class)

package ui.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import model.TopicInfo
import state.ClusterViewModel

@Composable
fun TopicManagementTab(
    clusterViewModel: ClusterViewModel,
    profileName: String,
    onOpenProducer: (topicName: String) -> Unit,
    onOpenConsumer: (topicName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val topics: List<TopicInfo> by clusterViewModel.topics.collectAsState()
    val loading: Boolean by clusterViewModel.loadingTopics.collectAsState()
    val error: String? by clusterViewModel.error.collectAsState()

    var selectedTopic: TopicInfo? by remember { mutableStateOf(null) }
    var showCreateDialog: Boolean by remember { mutableStateOf(false) }
    var topicToDelete: TopicInfo? by remember { mutableStateOf(null) }
    var filterText: String by remember { mutableStateOf("") }

    val filteredTopics: List<TopicInfo> = remember(topics, filterText) {
        if (filterText.isBlank()) topics
        else topics.filter { it.name.contains(filterText, ignoreCase = true) }
    }

    Row(modifier = modifier) {
        // Left: topic list
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // Toolbar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = profileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                OutlinedTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    label = { Text("Filter") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.width(24.dp).padding(4.dp), strokeWidth = 2.dp)
                } else {
                    TooltipIconButton(tooltip = "Refresh topics", onClick = { clusterViewModel.refreshTopics() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh topics")
                    }
                }
                TooltipIconButton(tooltip = "Create topic", onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Create topic")
                }
            }

            error?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            HorizontalDivider()

            // Column headers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Topic Name", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text("Partitions", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(80.dp))
                Text("Replication", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(90.dp))
                Text("", modifier = Modifier.width(120.dp))
            }
            HorizontalDivider()

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredTopics, key = { it.name }) { topic ->
                    TopicRow(
                        topic = topic,
                        isSelected = topic == selectedTopic,
                        onClick = { selectedTopic = if (selectedTopic == topic) null else topic },
                        onOpenProducer = { onOpenProducer(topic.name) },
                        onOpenConsumer = { onOpenConsumer(topic.name) },
                        onDelete = { topicToDelete = topic }
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }

        // Right: topic detail
        selectedTopic?.let { topic ->
            VerticalDivider()
            TopicDetailPanel(
                topic = topic,
                clusterViewModel = clusterViewModel,
                modifier = Modifier.width(340.dp).fillMaxHeight()
            )
        }
    }

    if (showCreateDialog) {
        CreateTopicDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, partitions, replicationFactor ->
                clusterViewModel.createTopic(name, partitions, replicationFactor) { result ->
                    result.onFailure { /* error already in viewmodel */ }
                }
                showCreateDialog = false
            }
        )
    }

    topicToDelete?.let { topic ->
        AlertDialog(
            onDismissRequest = { topicToDelete = null },
            title = { Text("Delete Topic") },
            text = { Text("Are you sure you want to delete \"${topic.name}\"? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedTopic == topic) selectedTopic = null
                        clusterViewModel.deleteTopic(topic.name) { /* error in viewmodel */ }
                        topicToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { topicToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TopicRow(
    topic: TopicInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    onOpenProducer: () -> Unit,
    onOpenConsumer: () -> Unit,
    onDelete: () -> Unit
) {
    val background = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = topic.name,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            color = if (topic.isInternal) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = topic.partitionCount.toString(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = topic.replicationFactor.toString(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(90.dp)
        )
        TooltipIconButton(
            tooltip = "Open Producer",
            onClick = onOpenProducer,
            modifier = Modifier.width(40.dp)
        ) {
            Icon(
                Icons.Default.Publish,
                contentDescription = "Open Producer",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        TooltipIconButton(
            tooltip = "Open Consumer",
            onClick = onOpenConsumer,
            modifier = Modifier.width(40.dp)
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Open Consumer",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        TooltipIconButton(
            tooltip = "Delete topic",
            onClick = onDelete,
            modifier = Modifier.width(40.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete topic",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun TopicDetailPanel(
    topic: TopicInfo,
    clusterViewModel: ClusterViewModel,
    modifier: Modifier = Modifier
) {
    var topicConfig: Map<String, String>? by remember(topic.name) { mutableStateOf(null) }
    var configLoading: Boolean by remember(topic.name) { mutableStateOf(false) }
    var configError: String? by remember(topic.name) { mutableStateOf(null) }
    var configRefreshTrigger: Int by remember { mutableStateOf(0) }

    var showEditDialog: Boolean by remember { mutableStateOf(false) }
    var editOverrides: Map<String, String> by remember { mutableStateOf(emptyMap()) }
    var loadingEditOverrides: Boolean by remember { mutableStateOf(false) }
    var saveError: String? by remember(topic.name) { mutableStateOf(null) }
    var showTruncateDialog: Boolean by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(topic.name, configRefreshTrigger) {
        configLoading = true
        configError = null
        clusterViewModel.getTopicConfig(topic.name) { result ->
            result
                .onSuccess { config -> topicConfig = config }
                .onFailure { e -> configError = e.message }
            configLoading = false
        }
    }

    Column(modifier = modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = topic.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TooltipIconButton(tooltip = "Delete all messages", onClick = { showTruncateDialog = true }) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = "Delete all messages",
                    tint = MaterialTheme.colorScheme.error
                )
            }
            if (loadingEditOverrides) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                TooltipIconButton(tooltip = "Edit config", onClick = {
                    saveError = null
                    loadingEditOverrides = true
                    clusterViewModel.getTopicConfigOverrides(topic.name) { result ->
                        result
                            .onSuccess { overrides ->
                                editOverrides = overrides
                                showEditDialog = true
                            }
                            .onFailure { e ->
                                saveError = "Failed to load overrides: ${e.message}"
                            }
                        loadingEditOverrides = false
                    }
                }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit config")
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        DetailRow(label = "Partitions", value = topic.partitionCount.toString())
        DetailRow(label = "Replication", value = topic.replicationFactor.toString())
        DetailRow(label = "Internal", value = topic.isInternal.toString())

        Spacer(modifier = Modifier.padding(top = 12.dp))
        Text("Configuration", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        saveError?.let { err ->
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        when {
            configLoading -> CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            configError != null -> Text(
                text = configError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            topicConfig != null -> {
                val hState: androidx.compose.foundation.ScrollState = rememberScrollState()
                val vState: androidx.compose.foundation.ScrollState = rememberScrollState()
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Box {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(vState)
                                .horizontalScroll(hState)
                                .padding(end = 12.dp, bottom = 12.dp)
                        ) {
                            Column(modifier = Modifier.wrapContentWidth(unbounded = true)) {
                                topicConfig!!.entries
                                    .sortedBy { it.key }
                                    .forEach { (k, v) ->
                                        Row(
                                            modifier = Modifier.padding(vertical = 1.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = k,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                softWrap = false
                                            )
                                            Text(
                                                text = v,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                softWrap = false
                                            )
                                        }
                                    }
                            }
                        }
                        androidx.compose.foundation.VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            adapter = androidx.compose.foundation.rememberScrollbarAdapter(vState)
                        )
                        androidx.compose.foundation.HorizontalScrollbar(
                            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(end = 12.dp),
                            adapter = androidx.compose.foundation.rememberScrollbarAdapter(hState)
                        )
                    }
                }
            }
        }
    }

    if (showTruncateDialog) {
        AlertDialog(
            onDismissRequest = { showTruncateDialog = false },
            title = { Text("Delete All Messages") },
            text = {
                Text("All messages in \"${topic.name}\" will be permanently deleted.\n\nConsumers with uncommitted offsets may skip data or fail when they reconnect.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTruncateDialog = false
                        clusterViewModel.truncateTopic(topic.name) { result ->
                            result.onFailure { e -> saveError = "Delete failed: ${e.message}" }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Messages")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTruncateDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showEditDialog) {
        EditConfigDialog(
            topicName = topic.name,
            initialOverrides = editOverrides,
            onDismiss = { showEditDialog = false },
            onSave = { setEntries, deleteKeys ->
                showEditDialog = false
                if (setEntries.isNotEmpty() || deleteKeys.isNotEmpty()) {
                    clusterViewModel.updateTopicConfig(topic.name, setEntries, deleteKeys) { result ->
                        result
                            .onSuccess { configRefreshTrigger++ }
                            .onFailure { e -> saveError = "Save failed: ${e.message}" }
                    }
                }
            }
        )
    }
}

@Composable
private fun CreateTopicDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, partitions: Int, replicationFactor: Short) -> Unit
) {
    var name: String by remember { mutableStateOf("") }
    var partitions: String by remember { mutableStateOf("1") }
    var replicationFactor: String by remember { mutableStateOf("1") }
    var nameError: String? by remember { mutableStateOf(null) }
    var partitionsError: String? by remember { mutableStateOf(null) }
    var replicationError: String? by remember { mutableStateOf(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Topic") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text("Topic Name") },
                    isError = nameError != null,
                    supportingText = if (nameError != null) ({ Text(nameError!!) }) else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = partitions,
                    onValueChange = { partitions = it; partitionsError = null },
                    label = { Text("Partitions") },
                    isError = partitionsError != null,
                    supportingText = if (partitionsError != null) ({ Text(partitionsError!!) }) else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = replicationFactor,
                    onValueChange = { replicationFactor = it; replicationError = null },
                    label = { Text("Replication Factor") },
                    isError = replicationError != null,
                    supportingText = if (replicationError != null) ({ Text(replicationError!!) }) else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                nameError = if (name.isBlank()) "Required" else null
                val p: Int? = partitions.toIntOrNull()?.takeIf { it > 0 }
                partitionsError = if (p == null) "Must be a positive integer" else null
                val r: Short? = replicationFactor.toShortOrNull()?.takeIf { it > 0 }
                replicationError = if (r == null) "Must be a positive integer" else null
                if (nameError == null && p != null && r != null) {
                    onCreate(name.trim(), p, r)
                }
            }) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TooltipIconButton(
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    TooltipArea(
        tooltip = {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.inverseSurface,
                modifier = Modifier.shadow(4.dp)
            ) {
                Text(
                    text = tooltip,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        },
        delayMillis = 500,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp))
    ) {
        IconButton(onClick = onClick, modifier = modifier) {
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun EditConfigDialog(
    topicName: String,
    initialOverrides: Map<String, String>,
    onDismiss: () -> Unit,
    onSave: (setEntries: Map<String, String>, deleteKeys: Set<String>) -> Unit
) {
    var entries: List<Pair<String, String>> by remember {
        mutableStateOf(initialOverrides.entries.map { it.key to it.value })
    }
    val originalKeys: Set<String> = remember { initialOverrides.keys.toSet() }
    var validationError: String? by remember { mutableStateOf(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 500.dp),
        title = { Text("Edit Config — $topicName") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (entries.isEmpty()) {
                    Text(
                        text = "No overrides set. Add one below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                entries.forEachIndexed { index, (key, value) ->
                    val isExisting: Boolean = key in originalKeys
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = key,
                            onValueChange = { newKey ->
                                if (!isExisting) {
                                    validationError = null
                                    entries = entries.toMutableList().also { it[index] = newKey to value }
                                }
                            },
                            label = { Text("Key") },
                            singleLine = true,
                            readOnly = isExisting,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = value,
                            onValueChange = { newValue ->
                                validationError = null
                                entries = entries.toMutableList().also { it[index] = key to newValue }
                            },
                            label = { Text("Value") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            entries = entries.filterIndexed { i, _ -> i != index }
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove override",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                TextButton(onClick = { entries = entries + ("" to "") }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add override")
                }
                validationError?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val newEntries: List<Pair<String, String>> = entries.filter { it.first !in originalKeys }
                if (newEntries.any { it.first.isBlank() }) {
                    validationError = "New override keys must not be empty"
                    return@Button
                }
                val setEntries: Map<String, String> = entries
                    .filter { (k, _) -> k.isNotBlank() }
                    .associate { (k, v) -> k to v }
                val deleteKeys: Set<String> = originalKeys - entries.map { it.first }.toSet()
                onSave(setEntries, deleteKeys)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
