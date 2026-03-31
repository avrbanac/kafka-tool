package ui.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import state.ProducerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProducerTab(
    viewModel: ProducerViewModel,
    modifier: Modifier = Modifier
) {
    val sending: Boolean by viewModel.sending.collectAsState()
    val lastResult: String? by viewModel.lastResult.collectAsState()

    var key: String by remember { mutableStateOf("") }
    var value: String by remember { mutableStateOf("") }
    val headers: MutableList<Pair<String, String>> = remember { mutableStateListOf() }
    var valueError: String? by remember { mutableStateOf(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Topic: ${viewModel.topic}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        HorizontalDivider()

        // Key
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("Key (optional)") },
            placeholder = { Text("Leave empty for null key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !sending
        )

        // Value
        OutlinedTextField(
            value = value,
            onValueChange = { value = it; valueError = null },
            label = { Text("Value") },
            isError = valueError != null,
            supportingText = if (valueError != null) ({ Text(valueError!!) }) else null,
            modifier = Modifier.fillMaxWidth().height(160.dp),
            enabled = !sending,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )

        // Headers
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Headers", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text("Add a header to the message") } },
                    state = rememberTooltipState()
                ) {
                    TextButton(
                        onClick = { headers.add(Pair("", "")) },
                        enabled = !sending,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Add Header")
                    }
                }
            }

            headers.forEachIndexed { index, (headerKey, headerValue) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = headerKey,
                        onValueChange = { headers[index] = Pair(it, headerValue) },
                        label = { Text("Key") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        enabled = !sending
                    )
                    OutlinedTextField(
                        value = headerValue,
                        onValueChange = { headers[index] = Pair(headerKey, it) },
                        label = { Text("Value") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        enabled = !sending
                    )
                    IconButton(
                        onClick = { headers.removeAt(index) },
                        enabled = !sending
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove header")
                    }
                }
            }
        }

        // Send button + result
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("Send message to topic") } },
                state = rememberTooltipState()
            ) {
                Button(
                    onClick = {
                        if (value.isBlank()) {
                            valueError = "Value is required"
                            return@Button
                        }
                        val headerMap: Map<String, String> = headers
                            .filter { (k, _) -> k.isNotBlank() }
                            .associate { (k, v) -> k to v }
                        viewModel.send(
                            key = key.ifBlank { null },
                            value = value,
                            headers = headerMap
                        )
                    },
                    enabled = !sending,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(if (sending) "Sending..." else "Send")
                }
            }

            lastResult?.let { result ->
                val isError: Boolean = result.startsWith("Failed")
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
