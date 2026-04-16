package model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ClusterProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val bootstrapServers: String,
    val sshTunnelEnabled: Boolean = false,
    val sshHost: String = "",
    val sshPort: Int = 22,
    val sshUsername: String = "",
    val sshAuthType: SshAuthType = SshAuthType.KEY_FILE,
    val sshKeyPath: String = "",
    val sshPassword: String = "",
    val sshProxyJumpEnabled: Boolean = false,
    val sshProxyJumpHost: String = "",
    val sshProxyJumpPort: Int = 22,
    val sshProxyJumpUsername: String = "",
    val sshProxyJumpKeyPath: String = ""
)
