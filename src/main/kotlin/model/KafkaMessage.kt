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

    fun matchesFilter(filter: String): Boolean {
        val lowerFilter: String = filter.lowercase()
        if (key?.lowercase()?.contains(lowerFilter) == true) return true
        if (value?.lowercase()?.contains(lowerFilter) == true) return true
        for ((headerKey: String, headerValue: String) in headers) {
            if (headerKey.lowercase().contains(lowerFilter)) return true
            if (headerValue.lowercase().contains(lowerFilter)) return true
        }
        return false
    }
}
