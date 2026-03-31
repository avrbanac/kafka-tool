package model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ClusterProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val bootstrapServers: String,
    val hostnameMapping: String = ""
)
