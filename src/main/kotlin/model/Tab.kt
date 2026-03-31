package model

import java.util.UUID

sealed class TabType {
    abstract val id: String

    data class Consumer(
        override val id: String = UUID.randomUUID().toString(),
        val topicName: String,
        val profileId: String
    ) : TabType()

    data class Producer(
        override val id: String = UUID.randomUUID().toString(),
        val topicName: String,
        val profileId: String
    ) : TabType()

    data class TopicManagement(
        override val id: String = UUID.randomUUID().toString(),
        val profileId: String
    ) : TabType()
}

fun TabType.displayName(): String = when (this) {
    is TabType.Consumer -> "Consumer: ${this.topicName}"
    is TabType.Producer -> "Producer: ${this.topicName}"
    is TabType.TopicManagement -> "Topics"
}
