package state

import kafka.ProducerWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ProducerViewModel(
    bootstrapServers: String,
    profileId: String,
    hostnameMapping: String = "",
    val topic: String,
    private val scope: CoroutineScope
) : AutoCloseable {
    private val logger: Logger = LoggerFactory.getLogger(ProducerViewModel::class.java)
    private val producer: ProducerWrapper = ProducerWrapper(bootstrapServers, profileId, hostnameMapping)

    private val _sending: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _lastResult: MutableStateFlow<String?> = MutableStateFlow(null)
    val lastResult: StateFlow<String?> = _lastResult.asStateFlow()

    fun send(key: String?, value: String, headers: Map<String, String>) {
        scope.launch(Dispatchers.IO) {
            _sending.value = true
            _lastResult.value = null
            try {
                logger.debug("Sending message to topic '{}', key={}, headers={}", topic, if (key != null) "present" else "null", headers.size)
                producer.send(topic, key?.ifBlank { null }, value, headers)
                _lastResult.value = "Message sent successfully."
                logger.info("Message sent to topic '{}'", topic)
            } catch (e: Exception) {
                _lastResult.value = "Failed to send: ${e.message}"
                logger.error("Failed to send message to topic '{}'", topic, e)
            } finally {
                _sending.value = false
            }
        }
    }

    override fun close() {
        producer.close()
        logger.debug("ProducerViewModel closed for topic '{}'", topic)
    }
}
