@file:OptIn(ExperimentalFoundationApi::class)

package ui.sidebar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.VerticalScrollbar
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import model.ClusterProfile
import model.TabType
import model.TopicInfo
import state.AppState
import state.ClusterViewModel

@Composable
fun Sidebar(
    appState: AppState,
    clusterViewModels: MutableMap<String, ClusterViewModel>,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    val profiles: List<ClusterProfile> by appState.profiles.collectAsState()
    var showAddDialog: Boolean by remember { mutableStateOf(false) }
    var editingProfile: ClusterProfile? by remember { mutableStateOf(null) }
    var mappingChangedWarningFor: String? by remember { mutableStateOf(null) }
    val expandedProfiles: MutableMap<String, Boolean> = remember { mutableStateMapOf() }

    Column(modifier = modifier) {
        // Fixed header — never scrolls away
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CLUSTERS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            TooltipIconButton(
                tooltip = "Add cluster",
                onClick = { showAddDialog = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add cluster", modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider()

        // One block per profile — headers are fixed, only topics section scrolls
        profiles.forEach { profile ->
            val viewModel: ClusterViewModel? = clusterViewModels[profile.id]
            val connected: Boolean by (viewModel?.connected ?: MutableStateFlow(false)).collectAsState(initial = false)
            val connecting: Boolean by (viewModel?.connecting ?: MutableStateFlow(false)).collectAsState(initial = false)
            val error: String? by (viewModel?.error ?: MutableStateFlow<String?>(null)).collectAsState(initial = null)
            val topics: List<TopicInfo> by (viewModel?.topics ?: MutableStateFlow(emptyList())).collectAsState(initial = emptyList())
            val isExpanded: Boolean = expandedProfiles[profile.id] ?: true

            // Profile header row — always visible, never scrolls
            ProfileHeader(
                profile = profile,
                connected = connected,
                connecting = connecting,
                error = error,
                isExpanded = isExpanded,
                topicCount = topics.size,
                onToggleExpanded = { expandedProfiles[profile.id] = !isExpanded },
                onConnect = {
                    val vm = ClusterViewModel(profile.id, profile.bootstrapServers, profile.hostnameMapping, scope)
                    clusterViewModels[profile.id] = vm
                    vm.connect()
                },
                onDisconnect = {
                    clusterViewModels[profile.id]?.disconnect()
                    clusterViewModels.remove(profile.id)
                },
                onEdit = { editingProfile = profile },
                onDelete = {
                    clusterViewModels[profile.id]?.close()
                    clusterViewModels.remove(profile.id)
                    appState.removeProfile(profile.id)
                },
                onRefresh = { viewModel?.refreshTopics() },
                onOpenTopicManagement = {
                    appState.openTab(TabType.TopicManagement(profileId = profile.id))
                }
            )

            // Topics section — scrolls independently, fills remaining sidebar space
            if (connected && isExpanded) {
                TopicsSection(
                    topics = topics,
                    onOpenConsumer = { topic ->
                        appState.openTab(TabType.Consumer(topicName = topic.name, profileId = profile.id))
                    },
                    onOpenProducer = { topic ->
                        appState.openTab(TabType.Producer(topicName = topic.name, profileId = profile.id))
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
        }
    }

    if (showAddDialog) {
        ProfileDialog(
            profile = null,
            onDismiss = { showAddDialog = false },
            onSave = { profile ->
                appState.addProfile(profile)
                showAddDialog = false
            }
        )
    }

    editingProfile?.let { profile ->
        ProfileDialog(
            profile = profile,
            onDismiss = { editingProfile = null },
            onSave = { updated ->
                val mappingChanged: Boolean = updated.hostnameMapping != profile.hostnameMapping
                val hasOpenTabs: Boolean = appState.tabs.value.any { tab ->
                    when (tab) {
                        is TabType.Consumer -> tab.profileId == profile.id
                        is TabType.Producer -> tab.profileId == profile.id
                        is TabType.TopicManagement -> tab.profileId == profile.id
                    }
                }
                appState.updateProfile(updated)
                editingProfile = null
                if (mappingChanged && hasOpenTabs) {
                    mappingChangedWarningFor = profile.name
                }
            }
        )
    }

    mappingChangedWarningFor?.let { clusterName ->
        AlertDialog(
            onDismissRequest = { mappingChangedWarningFor = null },
            title = { Text("Hostname Mapping Updated") },
            text = { Text("Close and reopen any active tabs for \"$clusterName\" to apply the new mapping.") },
            confirmButton = {
                Button(onClick = { mappingChangedWarningFor = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun ProfileHeader(
    profile: ClusterProfile,
    connected: Boolean,
    connecting: Boolean,
    error: String?,
    isExpanded: Boolean,
    topicCount: Int,
    onToggleExpanded: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
    onOpenTopicManagement: () -> Unit
) {
    var showMenu: Boolean by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (connected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = profile.bootstrapServers,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (connecting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                TooltipIconButton(
                    tooltip = if (connected) "Disconnect from cluster" else "Connect to cluster",
                    onClick = if (connected) onDisconnect else onConnect,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (connected) Icons.Default.LinkOff else Icons.Default.Link,
                        contentDescription = if (connected) "Disconnect" else "Connect",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Box {
                TooltipIconButton(tooltip = "More options", onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options", modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = { onEdit(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { onDelete(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                    )
                }
            }
        }

        error?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 24.dp, end = 8.dp, bottom = 4.dp)
            )
        }

        // Topics sub-header (only when connected)
        if (connected) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(start = 24.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Topics ($topicCount)",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f)
                )
                TooltipIconButton(tooltip = "Refresh topics", onClick = onRefresh, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh topics", modifier = Modifier.size(14.dp))
                }
                TooltipIconButton(tooltip = "Manage topics", onClick = onOpenTopicManagement, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = "Manage topics", modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun TopicsSection(
    topics: List<TopicInfo>,
    onOpenConsumer: (TopicInfo) -> Unit,
    onOpenProducer: (TopicInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(end = 8.dp)
        ) {
            items(topics, key = { it.name }) { topic ->
                TopicItem(
                    topic = topic,
                    onOpenConsumer = { onOpenConsumer(topic) },
                    onOpenProducer = { onOpenProducer(topic) }
                )
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
    }
}

@Composable
private fun TopicItem(
    topic: TopicInfo,
    onOpenConsumer: () -> Unit,
    onOpenProducer: () -> Unit
) {
    var showMenu: Boolean by remember { mutableStateOf(false) }

    TooltipArea(
        tooltip = { SidebarTooltip(topic.name) },
        delayMillis = 600,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(16.dp, 8.dp))
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showMenu = true }
                    .padding(start = 36.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = topic.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    color = if (topic.isInternal)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Open Consumer") },
                    onClick = { onOpenConsumer(); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Open Producer") },
                    onClick = { onOpenProducer(); showMenu = false }
                )
            }
        }
    }
}

@Composable
private fun SidebarTooltip(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.inverseSurface,
        modifier = Modifier.shadow(4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun TooltipIconButton(
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    TooltipArea(
        tooltip = { SidebarTooltip(tooltip) },
        delayMillis = 500,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp))
    ) {
        IconButton(onClick = onClick, modifier = modifier) {
            content()
        }
    }
}
