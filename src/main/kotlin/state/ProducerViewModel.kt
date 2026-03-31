package state

import kafka.ProducerWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProducerViewModel(
    bootstrapServers: String,
    profileId: String,
    hostnameMapping: String = "",
    val topic: String,
    private val scope: CoroutineScope
) : AutoCloseable {
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
                producer.send(topic, key?.ifBlank { null }, value, headers)
                _lastResult.value = "Message sent successfully."
            } catch (e: Exception) {
                _lastResult.value = "Failed to send: ${e.message}"
            } finally {
                _sending.value = false
            }
        }
    }

    override fun close() {
        producer.close()
    }
}
