package state

import kafka.AdminClientWrapper
import kafka.KafkaMetadataFetcher
import kafka.MappingHostnameStore
import kafka.SshTunnelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import model.BrokerNode
import model.SshAuthType
import model.TopicInfo
import model.TopicMetrics
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

    private val _topicMetrics: MutableStateFlow<TopicMetrics?> = MutableStateFlow(null)
    val topicMetrics: StateFlow<TopicMetrics?> = _topicMetrics.asStateFlow()

    private val _metricsLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val metricsLoading: StateFlow<Boolean> = _metricsLoading.asStateFlow()

    private val _metricsError: MutableStateFlow<String?> = MutableStateFlow(null)
    val metricsError: StateFlow<String?> = _metricsError.asStateFlow()

    private var metricsJob: Job? = null
    private var currentMetricsTopic: String? = null
    private var lastMetricsSample: TopicMetrics? = null

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
        stopTopicMetrics()
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

    fun startTopicMetrics(topicName: String) {
        metricsJob?.cancel()
        currentMetricsTopic = topicName
        lastMetricsSample = null
        _topicMetrics.value = null
        _metricsError.value = null
        metricsJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                sampleTopicMetrics(topicName)
                delay(15_000)
            }
        }
    }

    fun refreshTopicMetrics() {
        val topicName: String = currentMetricsTopic ?: return
        startTopicMetrics(topicName)
    }

    fun stopTopicMetrics() {
        metricsJob?.cancel()
        metricsJob = null
        currentMetricsTopic = null
        lastMetricsSample = null
        _topicMetrics.value = null
        _metricsError.value = null
        _metricsLoading.value = false
    }

    private fun sampleTopicMetrics(topicName: String) {
        _metricsLoading.value = true
        try {
            val client: AdminClientWrapper = adminClient ?: run {
                _metricsError.value = "Not connected"
                return
            }
            val topicInfo: TopicInfo? = _topics.value.firstOrNull { it.name == topicName }
            val offsets: Map<Int, AdminClientWrapper.PartitionOffsets> = client.getTopicOffsets(topicName)
            val logDirStats: AdminClientWrapper.TopicLogDirStats = client.getTopicLogDirStats(topicName)
            val totalMessages: Long = offsets.values.sumOf { it.endOffset - it.beginningOffset }
            val avgStoredBytesPerMessage: Double = if (totalMessages > 0L)
                logDirStats.leaderBytes.toDouble() / totalMessages.toDouble()
            else 0.0

            val now: Long = System.currentTimeMillis()
            val previous: TopicMetrics? = lastMetricsSample
            val messageRatePerSec: Double
            val bytesRatePerSec: Double
            if (previous != null && previous.topicName == topicName) {
                val dtSec: Double = (now - previous.sampleTimestampMs).toDouble() / 1000.0
                if (dtSec > 0.0) {
                    val dMessages: Long = totalMessages - previous.totalMessages
                    val dBytes: Long = logDirStats.leaderBytes - previous.leaderBytesOnDisk
                    messageRatePerSec = if (dMessages >= 0L) dMessages.toDouble() / dtSec else 0.0
                    bytesRatePerSec = if (dBytes >= 0L) dBytes.toDouble() / dtSec else 0.0
                } else {
                    messageRatePerSec = 0.0
                    bytesRatePerSec = 0.0
                }
            } else {
                messageRatePerSec = 0.0
                bytesRatePerSec = 0.0
            }

            val metrics = TopicMetrics(
                topicName = topicName,
                partitionCount = topicInfo?.partitionCount ?: offsets.size,
                replicationFactor = topicInfo?.replicationFactor ?: 0,
                totalMessages = totalMessages,
                leaderBytesOnDisk = logDirStats.leaderBytes,
                totalBytesOnDisk = logDirStats.totalBytes,
                avgStoredBytesPerMessage = avgStoredBytesPerMessage,
                messageRatePerSec = messageRatePerSec,
                bytesRatePerSec = bytesRatePerSec,
                sampleTimestampMs = now
            )
            lastMetricsSample = metrics
            _topicMetrics.value = metrics
            _metricsError.value = null
        } catch (e: Exception) {
            logger.error("Failed to sample metrics for topic '{}' on cluster '{}'", topicName, profileId, e)
            _metricsError.value = "Metrics failed: ${e.message}"
        } finally {
            _metricsLoading.value = false
        }
    }

    override fun close() {
        metricsJob?.cancel()
        metricsJob = null
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
