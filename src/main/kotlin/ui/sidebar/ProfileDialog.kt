package ui.sidebar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import model.ClusterProfile

@Composable
fun ProfileDialog(
    profile: ClusterProfile?,
    onDismiss: () -> Unit,
    onSave: (ClusterProfile) -> Unit
) {
    val isEdit: Boolean = profile != null
    var name: String by remember { mutableStateOf(profile?.name ?: "") }
    var bootstrapServers: String by remember { mutableStateOf(profile?.bootstrapServers ?: "") }
    var hostnameMapping: String by remember { mutableStateOf(profile?.hostnameMapping ?: "") }
    var nameError: String? by remember { mutableStateOf(null) }
    var serversError: String? by remember { mutableStateOf(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Cluster Profile" else "Add Cluster Profile") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text("Name") },
                    placeholder = { Text("e.g. prod-eu") },
                    isError = nameError != null,
                    supportingText = if (nameError != null) ({ Text(nameError!!) }) else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bootstrapServers,
                    onValueChange = { bootstrapServers = it; serversError = null },
                    label = { Text("Bootstrap Servers") },
                    placeholder = { Text("e.g. localhost:9092") },
                    isError = serversError != null,
                    supportingText = if (serversError != null) ({ Text(serversError!!) }) else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = hostnameMapping,
                    onValueChange = { hostnameMapping = it },
                    label = { Text("Hostname Mapping (optional)") },
                    placeholder = { Text("10.0.0.1 hostname1\n10.0.0.2 hostname2 hostname2.fqdn.com") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                var valid = true
                if (name.isBlank()) {
                    nameError = "Name is required"
                    valid = false
                }
                if (bootstrapServers.isBlank()) {
                    serversError = "Bootstrap servers are required"
                    valid = false
                }
                if (valid) {
                    val saved: ClusterProfile = if (isEdit) {
                        profile!!.copy(
                            name = name.trim(),
                            bootstrapServers = bootstrapServers.trim(),
                            hostnameMapping = hostnameMapping.trim()
                        )
                    } else {
                        ClusterProfile(
                            name = name.trim(),
                            bootstrapServers = bootstrapServers.trim(),
                            hostnameMapping = hostnameMapping.trim()
                        )
                    }
                    onSave(saved)
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
