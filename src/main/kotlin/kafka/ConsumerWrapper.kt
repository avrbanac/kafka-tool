package kafka

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import model.KafkaMessage
import model.OffsetStrategy
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import java.util.Properties
import java.util.UUID
import kotlin.coroutines.coroutineContext

class ConsumerWrapper(private val bootstrapServers: String, private val profileId: String = "", private val hostnameMapping: String = "") {

    fun consume(
        topic: String,
        offsetStrategy: OffsetStrategy,
        maxMessages: Int?
    ): Flow<KafkaMessage> = flow {
        MappingHostnameStore.applyMapping(profileId, hostnameMapping)
        val props = Properties()
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[ConsumerConfig.GROUP_ID_CONFIG] = "kafka-tool-${UUID.randomUUID()}"
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.name
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.name
        props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "false"
        props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"

        val consumer: KafkaConsumer<String, String> = KafkaConsumer(props)
        var messageCount: Int = 0

        try {
            val partitions: List<TopicPartition> = consumer
                .partitionsFor(topic)
                .map { info -> TopicPartition(topic, info.partition()) }
            consumer.assign(partitions)

            var effectiveMaxMessages: Int? = maxMessages

            when (offsetStrategy) {
                is OffsetStrategy.Earliest -> consumer.seekToBeginning(partitions)
                is OffsetStrategy.Latest -> consumer.seekToEnd(partitions)
                is OffsetStrategy.LastMessage -> {
                    val endOffsets: Map<TopicPartition, Long> = consumer.endOffsets(partitions)
                    val nonEmptyPartitions: List<TopicPartition> = partitions.filter { tp ->
                        (endOffsets[tp] ?: 0L) > 0L
                    }
                    val emptyPartitions: List<TopicPartition> = partitions.filter { tp ->
                        !nonEmptyPartitions.contains(tp)
                    }
                    // Empty partitions must be seeked to a valid position before pausing,
                    // otherwise AUTO_OFFSET_RESET=none throws NoOffsetForPartitionException.
                    if (emptyPartitions.isNotEmpty()) {
                        consumer.seekToBeginning(emptyPartitions)
                        consumer.pause(emptyPartitions)
                    }
                    nonEmptyPartitions.forEach { tp ->
                        consumer.seek(tp, endOffsets[tp]!! - 1L)
                    }
                    effectiveMaxMessages = nonEmptyPartitions.size
                }
                is OffsetStrategy.SpecificOffset -> partitions.forEach { tp ->
                    consumer.seek(tp, offsetStrategy.offset)
                }
            }

            while (coroutineContext.isActive) {
                if (effectiveMaxMessages != null && messageCount >= effectiveMaxMessages) break

                val records = consumer.poll(Duration.ofMillis(500))
                for (record in records) {
                    if (!coroutineContext.isActive) break
                    if (effectiveMaxMessages != null && messageCount >= effectiveMaxMessages) break

                    val headers: Map<String, String> = record.headers()
                        .associate { h -> h.key() to String(h.value()) }

                    emit(
                        KafkaMessage(
                            partition = record.partition(),
                            offset = record.offset(),
                            timestamp = record.timestamp(),
                            key = record.key(),
                            value = record.value(),
                            headers = headers
                        )
                    )
                    messageCount++
                }
            }
        } finally {
            consumer.close()
        }
    }.flowOn(Dispatchers.IO)
}
