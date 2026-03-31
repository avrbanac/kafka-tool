package kafka

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties

class ProducerWrapper(bootstrapServers: String, profileId: String = "", hostnameMapping: String = "") : AutoCloseable {
    private val producer: KafkaProducer<String, String>

    init {
        MappingHostnameStore.applyMapping(profileId, hostnameMapping)
        val props = Properties()
        props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.name
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.name
        props[ProducerConfig.ACKS_CONFIG] = "1"
        props[ProducerConfig.RETRIES_CONFIG] = "1"
        producer = KafkaProducer(props)
    }

    fun send(
        topic: String,
        key: String?,
        value: String,
        headers: Map<String, String> = emptyMap()
    ) {
        val record: ProducerRecord<String, String> = ProducerRecord(topic, null, key, value)
        headers.forEach { (k, v) -> record.headers().add(RecordHeader(k, v.toByteArray())) }
        producer.send(record).get()
    }

    override fun close() {
        try {
            producer.close()
        } catch (_: Exception) {}
    }
}
