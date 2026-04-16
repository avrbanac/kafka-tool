package model

import kotlinx.serialization.Serializable

@Serializable
enum class SshAuthType {
    KEY_FILE,
    PASSWORD
}
