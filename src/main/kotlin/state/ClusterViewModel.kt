package state

import kafka.AdminClientWrapper
import kafka.MappingHostnameStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import model.TopicInfo

class ClusterViewModel(
    private val profileId: String,
    private val bootstrapServers: String,
    private val hostnameMapping: String = "",
    private val scope: CoroutineScope
) : AutoCloseable {
    private var adminClient: AdminClientWrapper? = null

    private val _connected: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _connecting: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting.asStateFlow()

    private val _topics: MutableStateFlow<List<TopicInfo>> = MutableStateFlow(emptyList())
    val topics: StateFlow<List<TopicInfo>> = _topics.asStateFlow()

    private val _error: MutableStateFlow<String?> = MutableStateFlow(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loadingTopics: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val loadingTopics: StateFlow<Boolean> = _loadingTopics.asStateFlow()

    fun connect() {
        scope.launch(Dispatchers.IO) {
            _connecting.value = true
            _error.value = null
            try {
                adminClient?.close()
                adminClient = AdminClientWrapper(bootstrapServers, profileId, hostnameMapping)
                adminClient!!.testConnection()
                _connected.value = true
                loadTopicsInternal()
            } catch (e: Exception) {
                _error.value = "Connection failed: ${e.cause?.message ?: e.message ?: e.javaClass.name}"
                _connected.value = false
                adminClient?.close()
                adminClient = null
            } finally {
                _connecting.value = false
            }
        }
    }

    fun disconnect() {
        adminClient?.close()
        adminClient = null
        _connected.value = false
        _topics.value = emptyList()
        _error.value = null
        MappingHostnameStore.removeMapping(profileId)
    }

    fun refreshTopics() {
        scope.launch(Dispatchers.IO) {
            loadTopicsInternal()
        }
    }

    private fun loadTopicsInternal() {
        _loadingTopics.value = true
        try {
            _topics.value = adminClient?.listTopics() ?: emptyList()
        } catch (e: Exception) {
            _error.value = "Failed to load topics: ${e.message}"
        } finally {
            _loadingTopics.value = false
        }
    }

    fun createTopic(
        name: String,
        partitions: Int,
        replicationFactor: Short,
        onResult: (Result<Unit>) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                adminClient?.createTopic(name, partitions, replicationFactor)
                delay(500)
                loadTopicsInternal()
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun deleteTopic(name: String, onResult: (Result<Unit>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                adminClient?.deleteTopic(name)
                delay(500)
                loadTopicsInternal()
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun getTopicConfig(topicName: String, onResult: (Result<Map<String, String>>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val config: Map<String, String> = adminClient?.getTopicConfig(topicName) ?: emptyMap()
                onResult(Result.success(config))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    override fun close() {
        try {
            adminClient?.close()
        } catch (_: Exception) {}
        MappingHostnameStore.removeMapping(profileId)
    }
}
