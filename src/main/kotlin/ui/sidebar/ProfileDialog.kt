package ui.sidebar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import model.ClusterProfile
import model.SshAuthType

@Composable
fun ProfileDialog(
    profile: ClusterProfile?,
    onDismiss: () -> Unit,
    onSave: (ClusterProfile) -> Unit
) {
    val isEdit: Boolean = profile != null
    var name: String by remember { mutableStateOf(profile?.name ?: "") }
    var bootstrapServers: String by remember { mutableStateOf(profile?.bootstrapServers ?: "") }
    var nameError: String? by remember { mutableStateOf(null) }
    var serversError: String? by remember { mutableStateOf(null) }

    var sshTunnelEnabled: Boolean by remember { mutableStateOf(profile?.sshTunnelEnabled ?: false) }
    var sshHost: String by remember { mutableStateOf(profile?.sshHost ?: "") }
    var sshPort: String by remember { mutableStateOf((profile?.sshPort ?: 22).toString()) }
    var sshUsername: String by remember { mutableStateOf(profile?.sshUsername ?: "") }
    var sshAuthType: SshAuthType by remember { mutableStateOf(profile?.sshAuthType ?: SshAuthType.KEY_FILE) }
    var sshKeyPath: String by remember { mutableStateOf(profile?.sshKeyPath ?: "") }
    var sshPassword: String by remember { mutableStateOf(profile?.sshPassword ?: "") }
    var sshHostError: String? by remember { mutableStateOf(null) }
    var sshUsernameError: String? by remember { mutableStateOf(null) }

    var proxyJumpEnabled: Boolean by remember { mutableStateOf(profile?.sshProxyJumpEnabled ?: false) }
    var proxyJumpHost: String by remember { mutableStateOf(profile?.sshProxyJumpHost ?: "") }
    var proxyJumpPort: String by remember { mutableStateOf((profile?.sshProxyJumpPort ?: 22).toString()) }
    var proxyJumpUsername: String by remember { mutableStateOf(profile?.sshProxyJumpUsername ?: "") }
    var proxyJumpKeyPath: String by remember { mutableStateOf(profile?.sshProxyJumpKeyPath ?: "") }
    var proxyJumpHostError: String? by remember { mutableStateOf(null) }
    var proxyJumpUsernameError: String? by remember { mutableStateOf(null) }

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
                    placeholder = { Text("e.g. kafka-broker-1.internal:9092") },
                    isError = serversError != null,
                    supportingText = if (serversError != null) ({ Text(serversError!!) }) else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = sshTunnelEnabled,
                        onCheckedChange = { sshTunnelEnabled = it }
                    )
                    Text(
                        text = "SSH Tunnel",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (sshTunnelEnabled) {
                    Text(
                        text = "Tunnels traffic through an SSH host. Broker addresses are discovered automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = sshHost,
                            onValueChange = { sshHost = it; sshHostError = null },
                            label = { Text("SSH Host") },
                            placeholder = { Text("10.0.0.5") },
                            isError = sshHostError != null,
                            supportingText = if (sshHostError != null) ({ Text(sshHostError!!) }) else null,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = sshPort,
                            onValueChange = { sshPort = it },
                            label = { Text("Port") },
                            singleLine = true,
                            modifier = Modifier.width(80.dp)
                        )
                    }
                    OutlinedTextField(
                        value = sshUsername,
                        onValueChange = { sshUsername = it; sshUsernameError = null },
                        label = { Text("SSH Username") },
                        isError = sshUsernameError != null,
                        supportingText = if (sshUsernameError != null) ({ Text(sshUsernameError!!) }) else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    SshAuthTypeSelector(
                        selectedType = sshAuthType,
                        onTypeSelected = { sshAuthType = it }
                    )

                    when (sshAuthType) {
                        SshAuthType.KEY_FILE -> {
                            OutlinedTextField(
                                value = sshKeyPath,
                                onValueChange = { sshKeyPath = it },
                                label = { Text("Private Key Path (blank = auto-detect)") },
                                placeholder = { Text("~/.ssh/id_rsa") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = sshPassword,
                                onValueChange = { sshPassword = it },
                                label = { Text("Key Passphrase (optional)") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        SshAuthType.PASSWORD -> {
                            OutlinedTextField(
                                value = sshPassword,
                                onValueChange = { sshPassword = it },
                                label = { Text("SSH Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    HorizontalDivider()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = proxyJumpEnabled,
                            onCheckedChange = { proxyJumpEnabled = it }
                        )
                        Text(
                            text = "Proxy Jump",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (proxyJumpEnabled) {
                        Text(
                            text = "Connect through this intermediate host first, then hop to the SSH host above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = proxyJumpHost,
                                onValueChange = { proxyJumpHost = it; proxyJumpHostError = null },
                                label = { Text("Jump Host") },
                                placeholder = { Text("10.0.0.1") },
                                isError = proxyJumpHostError != null,
                                supportingText = if (proxyJumpHostError != null) ({ Text(proxyJumpHostError!!) }) else null,
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = proxyJumpPort,
                                onValueChange = { proxyJumpPort = it },
                                label = { Text("Port") },
                                singleLine = true,
                                modifier = Modifier.width(80.dp)
                            )
                        }
                        OutlinedTextField(
                            value = proxyJumpUsername,
                            onValueChange = { proxyJumpUsername = it; proxyJumpUsernameError = null },
                            label = { Text("Jump Username") },
                            isError = proxyJumpUsernameError != null,
                            supportingText = if (proxyJumpUsernameError != null) ({ Text(proxyJumpUsernameError!!) }) else null,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = proxyJumpKeyPath,
                            onValueChange = { proxyJumpKeyPath = it },
                            label = { Text("Jump Private Key Path (blank = auto-detect)") },
                            placeholder = { Text("~/.ssh/id_rsa") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                nameError = if (name.isBlank()) "Name is required" else null
                serversError = if (bootstrapServers.isBlank()) "Bootstrap servers are required" else null

                if (sshTunnelEnabled) {
                    sshHostError = if (sshHost.isBlank()) "SSH host is required" else null
                    sshUsernameError = if (sshUsername.isBlank()) "SSH username is required" else null
                    if (proxyJumpEnabled) {
                        proxyJumpHostError = if (proxyJumpHost.isBlank()) "Jump host is required" else null
                        proxyJumpUsernameError = if (proxyJumpUsername.isBlank()) "Jump username is required" else null
                    } else {
                        proxyJumpHostError = null
                        proxyJumpUsernameError = null
                    }
                } else {
                    sshHostError = null
                    sshUsernameError = null
                    proxyJumpHostError = null
                    proxyJumpUsernameError = null
                }

                val hasErrors: Boolean = listOfNotNull(
                    nameError, serversError, sshHostError, sshUsernameError,
                    proxyJumpHostError, proxyJumpUsernameError
                ).isNotEmpty()
                if (!hasErrors) {
                    val parsedSshPort: Int = sshPort.toIntOrNull() ?: 22
                    val parsedJumpPort: Int = proxyJumpPort.toIntOrNull() ?: 22
                    val saved: ClusterProfile = if (profile != null) {
                        profile.copy(
                            name = name.trim(),
                            bootstrapServers = bootstrapServers.trim(),
                            sshTunnelEnabled = sshTunnelEnabled,
                            sshHost = sshHost.trim(),
                            sshPort = parsedSshPort,
                            sshUsername = sshUsername.trim(),
                            sshAuthType = sshAuthType,
                            sshKeyPath = sshKeyPath.trim(),
                            sshPassword = sshPassword,
                            sshProxyJumpEnabled = proxyJumpEnabled,
                            sshProxyJumpHost = proxyJumpHost.trim(),
                            sshProxyJumpPort = parsedJumpPort,
                            sshProxyJumpUsername = proxyJumpUsername.trim(),
                            sshProxyJumpKeyPath = proxyJumpKeyPath.trim()
                        )
                    } else {
                        ClusterProfile(
                            name = name.trim(),
                            bootstrapServers = bootstrapServers.trim(),
                            sshTunnelEnabled = sshTunnelEnabled,
                            sshHost = sshHost.trim(),
                            sshPort = parsedSshPort,
                            sshUsername = sshUsername.trim(),
                            sshAuthType = sshAuthType,
                            sshKeyPath = sshKeyPath.trim(),
                            sshPassword = sshPassword,
                            sshProxyJumpEnabled = proxyJumpEnabled,
                            sshProxyJumpHost = proxyJumpHost.trim(),
                            sshProxyJumpPort = parsedJumpPort,
                            sshProxyJumpUsername = proxyJumpUsername.trim(),
                            sshProxyJumpKeyPath = proxyJumpKeyPath.trim()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SshAuthTypeSelector(
    selectedType: SshAuthType,
    onTypeSelected: (SshAuthType) -> Unit
) {
    var expanded: Boolean by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = when (selectedType) {
                SshAuthType.KEY_FILE -> "Key File"
                SshAuthType.PASSWORD -> "Password"
            },
            onValueChange = {},
            readOnly = true,
            label = { Text("Auth Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Key File") },
                onClick = { onTypeSelected(SshAuthType.KEY_FILE); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("Password") },
                onClick = { onTypeSelected(SshAuthType.PASSWORD); expanded = false }
            )
        }
    }
}
