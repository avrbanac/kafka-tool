package model

data class TopicMetrics(
    val topicName: String,
    val partitionCount: Int,
    val replicationFactor: Int,
    val totalMessages: Long,
    val leaderBytesOnDisk: Long,
    val totalBytesOnDisk: Long,
    val avgStoredBytesPerMessage: Double,
    val messageRatePerSec: Double,
    val bytesRatePerSec: Double,
    val sampleTimestampMs: Long
)
