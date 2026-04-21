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
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.DisposableEffect
import model.TopicInfo
import model.TopicMetrics
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
    var showStructureDialog: Boolean by remember { mutableStateOf(false) }

    val metrics: TopicMetrics? by clusterViewModel.topicMetrics.collectAsState()
    val metricsLoading: Boolean by clusterViewModel.metricsLoading.collectAsState()
    val metricsError: String? by clusterViewModel.metricsError.collectAsState()

    DisposableEffect(topic.name) {
        clusterViewModel.startTopicMetrics(topic.name)
        onDispose { clusterViewModel.stopTopicMetrics() }
    }

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
            TooltipIconButton(tooltip = "Edit partitions / replication", onClick = { showStructureDialog = true }) {
                Icon(Icons.Default.Tune, contentDescription = "Edit partitions / replication")
            }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Metrics", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            if (metricsLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                TooltipIconButton(tooltip = "Refresh metrics", onClick = { clusterViewModel.refreshTopicMetrics() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh metrics")
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        metricsError?.let { err ->
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        val snapshot: TopicMetrics? = metrics
        if (snapshot != null) {
            DetailRow(label = "Messages", value = formatCount(snapshot.totalMessages))
            DetailRow(
                label = "Msg rate",
                value = if (snapshot.messageRatePerSec > 0.0) "%.1f msg/s".format(snapshot.messageRatePerSec) else "—"
            )
            DetailRow(
                label = "Avg size",
                value = if (snapshot.totalMessages > 0L) formatBytes((snapshot.avgStoredBytesPerMessage).toLong()) else "—"
            )
            DetailRow(
                label = "Throughput",
                value = if (snapshot.bytesRatePerSec > 0.0) "${formatBytes(snapshot.bytesRatePerSec.toLong())}/s" else "—"
            )
            DetailRow(label = "Leader bytes", value = formatBytes(snapshot.leaderBytesOnDisk))
            DetailRow(label = "Total bytes (all replicas)", value = formatBytes(snapshot.totalBytesOnDisk))
        } else if (metricsError == null) {
            Text(
                text = "Sampling…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

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

    if (showStructureDialog) {
        TopicStructureDialog(
            topicName = topic.name,
            currentPartitions = topic.partitionCount,
            currentReplicationFactor = topic.replicationFactor,
            onDismiss = { showStructureDialog = false },
            onSave = { newPartitions, newReplicationFactor ->
                showStructureDialog = false
                clusterViewModel.updateTopicStructure(
                    topic.name,
                    topic.partitionCount,
                    newPartitions,
                    topic.replicationFactor,
                    newReplicationFactor
                ) { result ->
                    result.onFailure { e -> saveError = "Failed: ${e.message}" }
                }
            }
        )
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

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units: Array<String> = arrayOf("KiB", "MiB", "GiB", "TiB", "PiB")
    var value: Double = bytes.toDouble() / 1024.0
    var unitIndex: Int = 0
    while (value >= 1024.0 && unitIndex < units.size - 1) {
        value /= 1024.0
        unitIndex++
    }
    return "%.2f %s".format(value, units[unitIndex])
}

private fun formatCount(count: Long): String {
    return "%,d".format(count)
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
private fun TopicStructureDialog(
    topicName: String,
    currentPartitions: Int,
    currentReplicationFactor: Int,
    onDismiss: () -> Unit,
    onSave: (newPartitions: Int, newReplicationFactor: Int) -> Unit
) {
    var partitions: String by remember { mutableStateOf(currentPartitions.toString()) }
    var replicationFactor: String by remember { mutableStateOf(currentReplicationFactor.toString()) }
    var partitionsError: String? by remember { mutableStateOf(null) }
    var replicationError: String? by remember { mutableStateOf(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Topic Structure — $topicName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Partitions", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Current: $currentPartitions — can only be increased",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = partitions,
                        onValueChange = { partitions = it; partitionsError = null },
                        label = { Text("New partition count") },
                        isError = partitionsError != null,
                        supportingText = if (partitionsError != null) ({ Text(partitionsError!!) }) else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Replication Factor", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Current: $currentReplicationFactor — brokers assigned round-robin",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = replicationFactor,
                        onValueChange = { replicationFactor = it; replicationError = null },
                        label = { Text("New replication factor") },
                        isError = replicationError != null,
                        supportingText = if (replicationError != null) ({ Text(replicationError!!) }) else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val newP: Int? = partitions.toIntOrNull()
                partitionsError = when {
                    newP == null || newP <= 0 -> "Must be a positive integer"
                    newP < currentPartitions -> "Cannot be less than current ($currentPartitions)"
                    else -> null
                }
                val newR: Int? = replicationFactor.toIntOrNull()?.takeIf { it > 0 }
                replicationError = if (newR == null) "Must be a positive integer" else null
                if (partitionsError == null && replicationError == null && newP != null && newR != null) {
                    onSave(newP, newR)
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
