package state

import kafka.ConsumerWrapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import model.ConsumeConfig
import model.KafkaMessage

class ConsumerViewModel(
    bootstrapServers: String,
    profileId: String,
    hostnameMapping: String = "",
    val topic: String,
    private val scope: CoroutineScope
) {
    private val consumerWrapper: ConsumerWrapper = ConsumerWrapper(bootstrapServers, profileId, hostnameMapping)

    private val _messages: MutableStateFlow<List<KafkaMessage>> = MutableStateFlow(emptyList())
    val messages: StateFlow<List<KafkaMessage>> = _messages.asStateFlow()

    private val _consuming: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val consuming: StateFlow<Boolean> = _consuming.asStateFlow()

    private val _error: MutableStateFlow<String?> = MutableStateFlow(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var consumeJob: Job? = null

    fun startConsuming(config: ConsumeConfig) {
        consumeJob?.cancel()
        _messages.value = emptyList()
        _error.value = null
        _consuming.value = true

        consumeJob = scope.launch {
            try {
                consumerWrapper
                    .consume(topic, config.offsetStrategy, config.maxMessages)
                    .collect { message: KafkaMessage ->
                        _messages.value = _messages.value + message
                    }
            } catch (e: CancellationException) {
                // normal stop, no error
            } catch (e: Exception) {
                _error.value = "Consumer error: ${e.message}"
            } finally {
                _consuming.value = false
            }
        }
    }

    fun stopConsuming() {
        consumeJob?.cancel()
        _consuming.value = false
    }

    fun clearMessages() {
        stopConsuming()
        _messages.value = emptyList()
        _error.value = null
    }
}
