package state

import kafka.AdminClientWrapper
import kafka.KafkaMetadataFetcher
import kafka.MappingHostnameStore
import kafka.SshTunnelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import model.BrokerNode
import model.SshAuthType
import model.TopicInfo
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ClusterViewModel(
    private val profileId: String,
    private val bootstrapServers: String,
    private val sshTunnelEnabled: Boolean = false,
    private val sshHost: String = "",
    private val sshPort: Int = 22,
    private val sshUsername: String = "",
    private val sshAuthType: SshAuthType = SshAuthType.KEY_FILE,
    private val sshKeyPath: String = "",
    private val sshPassword: String = "",
    private val sshProxyJumpEnabled: Boolean = false,
    private val sshProxyJumpHost: String = "",
    private val sshProxyJumpPort: Int = 22,
    private val sshProxyJumpUsername: String = "",
    private val sshProxyJumpKeyPath: String = "",
    private val scope: CoroutineScope
) : AutoCloseable {
    private val logger: Logger = LoggerFactory.getLogger(ClusterViewModel::class.java)
    private var adminClient: AdminClientWrapper? = null
    private var tunnelManager: SshTunnelManager? = null

    var effectiveHostnameMapping: String = ""
        private set

    var effectiveBootstrapServers: String = bootstrapServers
        private set

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
            logger.info("Connecting to cluster '{}' at {}", profileId, bootstrapServers)
            _connecting.value = true
            _error.value = null
            try {
                adminClient?.close()

                if (sshTunnelEnabled) {
                    connectWithSshTunnel()
                } else {
                    adminClient = AdminClientWrapper(bootstrapServers, profileId)
                }

                adminClient!!.testConnection()
                _connected.value = true
                logger.info("Connected to cluster '{}'", profileId)

                loadTopicsInternal()
            } catch (e: Exception) {
                _error.value = "Connection failed: ${e.cause?.message ?: e.message ?: e.javaClass.name}"
                _connected.value = false
                adminClient?.close()
                adminClient = null
                tunnelManager?.close()
                tunnelManager = null
                if (sshTunnelEnabled) MappingHostnameStore.setSshTunnelActive(false)
                logger.error("Connection failed for cluster '{}'", profileId, e)
            } finally {
                _connecting.value = false
            }
        }
    }

    private fun connectWithSshTunnel() {
        val manager = SshTunnelManager(
            sshHost, sshPort, sshUsername, sshAuthType, sshKeyPath, sshPassword,
            sshProxyJumpEnabled, sshProxyJumpHost, sshProxyJumpPort, sshProxyJumpUsername, sshProxyJumpKeyPath
        )
        manager.connect()
        tunnelManager = manager
        MappingHostnameStore.setSshTunnelActive(true)

        // 1. Tunnel the bootstrap server
        val bootstrapHosts: List<BrokerNode> = parseBootstrapServers(bootstrapServers)
        val bootstrapLoopback: java.net.InetAddress = manager.createTunnel(bootstrapHosts.first().host, bootstrapHosts.first().port)
        val bootstrapPort: Int = bootstrapHosts.first().port

        // 2. Fetch broker metadata via raw socket through the bootstrap tunnel
        val brokerNodes: List<BrokerNode> = KafkaMetadataFetcher.fetchBrokerNodes(
            bootstrapLoopback.hostAddress, bootstrapPort
        )
        logger.info("Discovered {} broker(s) via metadata: {}", brokerNodes.size, brokerNodes)

        // 3. Create tunnels for ALL brokers
        for (node in brokerNodes) {
            manager.createTunnel(node.host, node.port)
        }

        // 4. Build hostname mapping and rewritten bootstrap with all loopback addresses
        effectiveHostnameMapping = manager.generateHostnameMapping()
        MappingHostnameStore.applyMapping(profileId, effectiveHostnameMapping)

        val allBrokerLoopbacks: String = brokerNodes.joinToString(",") { node: BrokerNode ->
            val loopback: java.net.InetAddress = manager.createTunnel(node.host, node.port)
            "${loopback.hostAddress}:${node.port}"
        }
        effectiveBootstrapServers = allBrokerLoopbacks
        logger.info("Tunneled bootstrap: {}", allBrokerLoopbacks)

        // 5. Create the AdminClient — all brokers are already tunneled and mapped
        adminClient = AdminClientWrapper(allBrokerLoopbacks, profileId, effectiveHostnameMapping)
    }

    private fun parseBootstrapServers(servers: String): List<BrokerNode> {
        return servers.split(",").map { entry: String ->
            val trimmed: String = entry.trim()
            val parts: List<String> = trimmed.split(":")
            val host: String = parts[0]
            val port: Int = if (parts.size > 1) parts[1].toIntOrNull() ?: 9092 else 9092
            BrokerNode(host, port)
        }
    }

    fun disconnect() {
        logger.info("Disconnecting from cluster '{}'", profileId)
        adminClient?.close()
        adminClient = null
        tunnelManager?.close()
        tunnelManager = null
        if (sshTunnelEnabled) MappingHostnameStore.setSshTunnelActive(false)
        _connected.value = false
        _topics.value = emptyList()
        _error.value = null
        MappingHostnameStore.removeMapping(profileId)
    }

    fun refreshTopics() {
        scope.launch(Dispatchers.IO) {
            logger.debug("Refreshing topics for cluster '{}'", profileId)
            loadTopicsInternal()
        }
    }

    private fun loadTopicsInternal() {
        _loadingTopics.value = true
        try {
            _topics.value = adminClient?.listTopics() ?: emptyList()
        } catch (e: Exception) {
            _error.value = "Failed to load topics: ${e.message}"
            logger.error("Failed to load topics for cluster '{}'", profileId, e)
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
                logger.error("Failed to create topic '{}' on cluster '{}'", name, profileId, e)
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
                logger.error("Failed to delete topic '{}' on cluster '{}'", name, profileId, e)
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
                logger.error("Failed to get config for topic '{}' on cluster '{}'", topicName, profileId, e)
                onResult(Result.failure(e))
            }
        }
    }

    fun updateTopicStructure(
        topicName: String,
        currentPartitionCount: Int,
        newPartitionCount: Int,
        currentReplicationFactor: Int,
        newReplicationFactor: Int,
        onResult: (Result<Unit>) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                if (newPartitionCount != currentPartitionCount) {
                    adminClient?.increasePartitions(topicName, newPartitionCount)
                }
                if (newReplicationFactor != currentReplicationFactor) {
                    adminClient?.changeReplicationFactor(topicName, newReplicationFactor)
                }
                delay(500)
                loadTopicsInternal()
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                logger.error("Failed to update structure for topic '{}' on cluster '{}'", topicName, profileId, e)
                onResult(Result.failure(e))
            }
        }
    }

    fun truncateTopic(topicName: String, onResult: (Result<Unit>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                adminClient?.truncateTopic(topicName)
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                logger.error("Failed to truncate topic '{}' on cluster '{}'", topicName, profileId, e)
                onResult(Result.failure(e))
            }
        }
    }

    fun getTopicConfigOverrides(topicName: String, onResult: (Result<Map<String, String>>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val overrides: Map<String, String> = adminClient?.getTopicConfigOverrides(topicName) ?: emptyMap()
                onResult(Result.success(overrides))
            } catch (e: Exception) {
                logger.error("Failed to get config overrides for topic '{}' on cluster '{}'", topicName, profileId, e)
                onResult(Result.failure(e))
            }
        }
    }

    fun updateTopicConfig(
        topicName: String,
        setEntries: Map<String, String>,
        deleteKeys: Set<String>,
        onResult: (Result<Unit>) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                adminClient?.updateTopicConfig(topicName, setEntries, deleteKeys)
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                logger.error("Failed to update config for topic '{}' on cluster '{}'", topicName, profileId, e)
                onResult(Result.failure(e))
            }
        }
    }

    override fun close() {
        try {
            adminClient?.close()
        } catch (e: Exception) {
            logger.warn("Error closing AdminClient for cluster '{}'", profileId, e)
        }
        try {
            tunnelManager?.close()
        } catch (e: Exception) {
            logger.warn("Error closing SSH tunnel manager for cluster '{}'", profileId, e)
        }
        tunnelManager = null
        if (sshTunnelEnabled) MappingHostnameStore.setSshTunnelActive(false)
        MappingHostnameStore.removeMapping(profileId)
        logger.debug("ClusterViewModel closed for profile '{}'", profileId)
    }
}
