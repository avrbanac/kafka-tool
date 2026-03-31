package model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class KafkaMessage(
    val partition: Int,
    val offset: Long,
    val timestamp: Long,
    val key: String?,
    val value: String?,
    val headers: Map<String, String> = emptyMap()
) {
    fun formattedTimestamp(): String {
        val formatter: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault())
        return formatter.format(Instant.ofEpochMilli(timestamp))
    }
}
