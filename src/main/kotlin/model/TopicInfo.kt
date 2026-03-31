package model

data class TopicInfo(
    val name: String,
    val partitionCount: Int,
    val replicationFactor: Int,
    val isInternal: Boolean = false
)
