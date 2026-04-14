package ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import model.ConsumeConfig
import model.KafkaMessage
import model.OffsetStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import state.ConsumerViewModel

private val prettyJson: Json = Json { prettyPrint = true }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsumerTab(
    viewModel: ConsumerViewModel,
    modifier: Modifier = Modifier
) {
    val messages: List<KafkaMessage> by viewModel.messages.collectAsState()
    val consuming: Boolean by viewModel.consuming.collectAsState()
    val error: String? by viewModel.error.collectAsState()
    val filterText: String by viewModel.filterText.collectAsState()
    val filteredMessages: List<KafkaMessage> by viewModel.filteredMessages.collectAsState()

    var selectedStrategy: OffsetStrategy by remember { mutableStateOf(OffsetStrategy.Latest) }
    var strategyExpanded: Boolean by remember { mutableStateOf(false) }
    var specificOffsetText: String by remember { mutableStateOf("0") }
    var maxMessagesText: String by remember { mutableStateOf("100") }
    var unlimited: Boolean by remember { mutableStateOf(false) }
    var selectedMessage: KafkaMessage? by remember { mutableStateOf(null) }

    Column(modifier = modifier) {
        // Config bar
        Surface(tonalElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val topicDisplayName: String = remember(viewModel.topic) {
                    if (viewModel.topic.length > 30) viewModel.topic.take(30) + "…" else viewModel.topic
                }
                if (viewModel.topic.length > 30) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(viewModel.topic) } },
                        state = rememberTooltipState()
                    ) {
                        Text(
                            text = "Topic: $topicDisplayName",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Text(
                        text = "Topic: $topicDisplayName",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Offset strategy
                Box {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("Select the offset to start consuming from") } },
                        state = rememberTooltipState()
                    ) {
                        Button(
                            onClick = { if (!consuming) strategyExpanded = true },
                            enabled = !consuming,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(selectedStrategy.displayName())
                        }
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = strategyExpanded,
                        onDismissRequest = { strategyExpanded = false }
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Latest") },
                            onClick = {
                                selectedStrategy = OffsetStrategy.Latest
                                strategyExpanded = false
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("From Beginning") },
                            onClick = {
                                selectedStrategy = OffsetStrategy.Earliest
                                strategyExpanded = false
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Last Message") },
                            onClick = {
                                selectedStrategy = OffsetStrategy.LastMessage
                                strategyExpanded = false
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Specific Offset") },
                            onClick = {
                                selectedStrategy = OffsetStrategy.SpecificOffset(0L)
                                strategyExpanded = false
                            }
                        )
                    }
                }

                if (selectedStrategy is OffsetStrategy.SpecificOffset) {
                    OutlinedTextField(
                        value = specificOffsetText,
                        onValueChange = { specificOffsetText = it },
                        label = { Text("Offset") },
                        singleLine = true,
                        enabled = !consuming,
                        modifier = Modifier.width(100.dp)
                    )
                }

                // Max messages (hidden when LastMessage strategy is selected)
                if (selectedStrategy !is OffsetStrategy.LastMessage) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("Consume messages without a limit") } },
                        state = rememberTooltipState()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = unlimited,
                                onCheckedChange = { unlimited = it },
                                enabled = !consuming
                            )
                            Text("Unlimited", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (!unlimited) {
                        Text(
                            text = "Max messages:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val borderColor: Color = if (!consuming)
                            MaterialTheme.colorScheme.outline
                        else
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                        val textColor: Color = if (!consuming)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        Box(
                            modifier = Modifier
                                .width(52.dp)
                                .border(1.dp, borderColor, MaterialTheme.shapes.extraSmall)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = maxMessagesText,
                                onValueChange = { if (!consuming) maxMessagesText = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = textColor)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (consuming) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("Stop consuming") } },
                        state = rememberTooltipState()
                    ) {
                        Button(
                            onClick = { viewModel.stopConsuming() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text("Stop")
                        }
                    }
                } else {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("Start consuming messages from the topic") } },
                        state = rememberTooltipState()
                    ) {
                        Button(
                            onClick = {
                                val effectiveStrategy: OffsetStrategy = when (selectedStrategy) {
                                    is OffsetStrategy.SpecificOffset ->
                                        OffsetStrategy.SpecificOffset(specificOffsetText.toLongOrNull() ?: 0L)
                                    else -> selectedStrategy
                                }
                                val maxMessages: Int? = when {
                                    selectedStrategy is OffsetStrategy.LastMessage -> null
                                    unlimited -> null
                                    else -> maxMessagesText.toIntOrNull()?.takeIf { it > 0 } ?: 100
                                }
                                viewModel.startConsuming(ConsumeConfig(effectiveStrategy, maxMessages))
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text("Consume")
                        }
                    }
                }

                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text("Clear all messages") } },
                    state = rememberTooltipState()
                ) {
                    IconButton(onClick = { viewModel.clearMessages(); selectedMessage = null }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear messages")
                    }
                }
            }
        }

        error?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Part", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(40.dp))
                    Text("Offset", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(80.dp))
                    Text("Timestamp", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(190.dp))
                    Text("Key", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(120.dp))
                    Text("Value", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                }
                HorizontalDivider()

                // Filter field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = filterText,
                        onValueChange = { viewModel.setFilterText(it) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        decorationBox = { innerTextField: @Composable () -> Unit ->
                            Box {
                                if (filterText.isEmpty()) {
                                    Text(
                                        "Filter by key, value, or headers...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    if (filterText.isNotEmpty()) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear filter",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp).clickable { viewModel.setFilterText("") }
                        )
                    }
                }

                val listState = rememberLazyListState()
                LaunchedEffect(messages.size) {
                    if (filteredMessages.isNotEmpty() && filterText.isBlank()) {
                        listState.scrollToItem(filteredMessages.size - 1)
                    }
                }

                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(filteredMessages) { message ->
                        MessageRow(
                            message = message,
                            isSelected = message == selectedMessage,
                            onClick = {
                                selectedMessage = if (selectedMessage == message) null else message
                            }
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }

            selectedMessage?.let { msg ->
                VerticalDivider()
                MessageDetailPanel(
                    message = msg,
                    onClose = { selectedMessage = null },
                    modifier = Modifier.width(320.dp).fillMaxHeight()
                )
            }
        }

        // Status bar
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (filterText.isBlank()) "${messages.size} message${if (messages.size != 1) "s" else ""}"
                       else "${filteredMessages.size} of ${messages.size} message${if (messages.size != 1) "s" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (consuming) {
                Text(
                    text = "Consuming...",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CAF50)
                )
            }
        }
    }
}

@Composable
private fun MessageRow(
    message: KafkaMessage,
    isSelected: Boolean,
    onClick: () -> Unit
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
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message.partition.toString(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(40.dp)
        )
        Text(
            text = message.offset.toString(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = message.formattedTimestamp(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(190.dp)
        )
        Text(
            text = message.key ?: "(null)",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(120.dp),
            color = if (message.key == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = message.value ?: "(null)",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            color = if (message.value == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageDetailPanel(
    message: KafkaMessage,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayValue: String = remember(message.value) { prettyPrintIfJson(message.value) }
    val vState: androidx.compose.foundation.ScrollState = rememberScrollState()
    val hState: androidx.compose.foundation.ScrollState = rememberScrollState()

    Column(modifier = modifier.padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Message Detail", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("Close detail panel") } },
                state = rememberTooltipState()
            ) {
                IconButton(onClick = onClose, modifier = Modifier.padding(0.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        SelectionContainer {
            Column {
                DetailRow(label = "Partition", value = message.partition.toString())
                DetailRow(label = "Offset", value = message.offset.toString())
                DetailRow(label = "Timestamp", value = message.formattedTimestamp())
                DetailRow(label = "Key", value = message.key ?: "(null)")
            }
        }

        Spacer(modifier = Modifier.padding(top = 8.dp))
        Text("Value", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.padding(top = 4.dp))

        SelectionContainer(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(vState)
                        .horizontalScroll(hState)
                        .padding(8.dp)
                        .padding(end = 12.dp, bottom = 12.dp)
                ) {
                    Text(
                        text = displayValue,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.wrapContentWidth(unbounded = true),
                        softWrap = false
                    )
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

        if (message.headers.isNotEmpty()) {
            Spacer(modifier = Modifier.padding(top = 8.dp))
            Text("Headers", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            SelectionContainer {
                Column {
                    message.headers.forEach { (k, v) ->
                        DetailRow(label = k, value = v)
                    }
                }
            }
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
            modifier = Modifier.widthIn(min = 72.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun prettyPrintIfJson(raw: String?): String {
    if (raw == null) return "(null)"
    val trimmed: String = raw.trim()
    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return raw
    return try {
        val element: JsonElement = prettyJson.parseToJsonElement(trimmed)
        prettyJson.encodeToString(JsonElement.serializer(), element)
    } catch (_: Exception) {
        raw
    }
}
