package kafka

import model.BrokerNode
import model.TopicInfo
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.AlterConfigOp
import org.apache.kafka.clients.admin.ConfigEntry
import org.apache.kafka.clients.admin.NewPartitionReassignment
import org.apache.kafka.clients.admin.NewPartitions
import org.apache.kafka.clients.admin.OffsetSpec
import org.apache.kafka.clients.admin.RecordsToDelete
import org.apache.kafka.common.TopicPartition
import java.util.Optional
import org.apache.kafka.common.config.ConfigResource
import org.apache.kafka.clients.admin.NewTopic
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Properties
import java.util.concurrent.TimeUnit

class AdminClientWrapper(bootstrapServers: String, profileId: String = "", hostnameMapping: String = "") : AutoCloseable {
    private val logger: Logger = LoggerFactory.getLogger(AdminClientWrapper::class.java)
    private val adminClient: AdminClient

    init {
        MappingHostnameStore.applyMapping(profileId, hostnameMapping)
        val props = Properties()
        props[AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG] = "10000"
        props[AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG] = "10000"
        adminClient = AdminClient.create(props)
        logger.info("AdminClient created for bootstrap servers: {}", bootstrapServers)
    }

    fun testConnection() {
        logger.debug("Testing connection...")
        adminClient.listTopics().names().get(10, TimeUnit.SECONDS)
        logger.info("Connection test successful")
    }

    fun listTopics(): List<TopicInfo> {
        logger.debug("Listing topics...")
        val names: Set<String> = adminClient.listTopics().names().get(10, TimeUnit.SECONDS)
        val descriptions = adminClient.describeTopics(names).allTopicNames().get(10, TimeUnit.SECONDS)
        val topics: List<TopicInfo> = descriptions.map { (name, desc) ->
            TopicInfo(
                name = name,
                partitionCount = desc.partitions().size,
                replicationFactor = desc.partitions().firstOrNull()?.replicas()?.size ?: 0,
                isInternal = desc.isInternal
            )
        }.sortedBy { it.name }
        logger.debug("Listed {} topic(s)", topics.size)
        return topics
    }

    fun createTopic(name: String, partitions: Int, replicationFactor: Short) {
        logger.info("Creating topic '{}' with {} partition(s), replication factor {}", name, partitions, replicationFactor)
        adminClient.createTopics(listOf(NewTopic(name, partitions, replicationFactor)))
            .all().get(10, TimeUnit.SECONDS)
        logger.info("Topic '{}' created successfully", name)
    }

    fun deleteTopic(name: String) {
        logger.info("Deleting topic '{}'", name)
        adminClient.deleteTopics(listOf(name)).all().get(10, TimeUnit.SECONDS)
        logger.info("Topic '{}' deleted successfully", name)
    }

    fun getTopicConfig(topicName: String): Map<String, String> {
        logger.debug("Fetching full config for topic '{}'", topicName)
        val resource = ConfigResource(ConfigResource.Type.TOPIC, topicName)
        val result = adminClient.describeConfigs(listOf(resource)).all().get(10, TimeUnit.SECONDS)
        return result[resource]?.entries()?.associate { it.name() to (it.value() ?: "") } ?: emptyMap()
    }

    fun getTopicConfigOverrides(topicName: String): Map<String, String> {
        logger.debug("Fetching config overrides for topic '{}'", topicName)
        val resource = ConfigResource(ConfigResource.Type.TOPIC, topicName)
        val result = adminClient.describeConfigs(listOf(resource)).all().get(10, TimeUnit.SECONDS)
        return result[resource]?.entries()
            ?.filter { it.source() == ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG }
            ?.associate { it.name() to (it.value() ?: "") }
            ?: emptyMap()
    }

    fun updateTopicConfig(topicName: String, setEntries: Map<String, String>, deleteKeys: Set<String>) {
        logger.info("Updating config for topic '{}': setting {} key(s), deleting {} key(s)", topicName, setEntries.size, deleteKeys.size)
        val resource = ConfigResource(ConfigResource.Type.TOPIC, topicName)
        val ops: MutableList<AlterConfigOp> = mutableListOf()
        for ((key, value) in setEntries) {
            ops.add(AlterConfigOp(ConfigEntry(key, value), AlterConfigOp.OpType.SET))
        }
        for (key in deleteKeys) {
            ops.add(AlterConfigOp(ConfigEntry(key, ""), AlterConfigOp.OpType.DELETE))
        }
        adminClient.incrementalAlterConfigs(mapOf(resource to ops)).all().get(10, TimeUnit.SECONDS)
        logger.info("Config updated for topic '{}'", topicName)
    }

    fun increasePartitions(topicName: String, newPartitionCount: Int) {
        logger.info("Increasing partitions for topic '{}' to {}", topicName, newPartitionCount)
        adminClient.createPartitions(mapOf(topicName to NewPartitions.increaseTo(newPartitionCount)))
            .all().get(10, TimeUnit.SECONDS)
        logger.info("Partitions increased for topic '{}'", topicName)
    }

    fun changeReplicationFactor(topicName: String, newReplicationFactor: Int) {
        logger.info("Changing replication factor for topic '{}' to {}", topicName, newReplicationFactor)
        val brokerIds: List<Int> = adminClient.describeCluster().nodes()
            .get(10, TimeUnit.SECONDS)
            .map { it.id() }
            .sorted()
        logger.debug("Available broker IDs: {}", brokerIds)
        if (brokerIds.size < newReplicationFactor) {
            throw IllegalArgumentException(
                "Not enough brokers (${brokerIds.size}) for replication factor $newReplicationFactor"
            )
        }
        val description = adminClient.describeTopics(listOf(topicName))
            .allTopicNames().get(10, TimeUnit.SECONDS)[topicName]
            ?: throw IllegalStateException("Topic $topicName not found")
        val reassignments: Map<TopicPartition, Optional<NewPartitionReassignment>> =
            description.partitions().associate { partitionInfo ->
                val replicas: List<Int> = (0 until newReplicationFactor).map { i ->
                    brokerIds[(partitionInfo.partition() + i) % brokerIds.size]
                }
                TopicPartition(topicName, partitionInfo.partition()) to
                    Optional.of(NewPartitionReassignment(replicas))
            }
        adminClient.alterPartitionReassignments(reassignments).all().get(10, TimeUnit.SECONDS)
        logger.info("Replication factor changed for topic '{}'", topicName)
    }

    fun describeClusterNodes(): List<BrokerNode> {
        logger.debug("Discovering cluster broker nodes...")
        val nodes: List<BrokerNode> = adminClient.describeCluster().nodes()
            .get(10, TimeUnit.SECONDS)
            .map { node -> BrokerNode(host = node.host(), port = node.port()) }
        logger.debug("Discovered {} broker node(s): {}", nodes.size, nodes)
        return nodes
    }

    data class PartitionOffsets(val beginningOffset: Long, val endOffset: Long)

    fun getTopicOffsets(topicName: String): Map<Int, PartitionOffsets> {
        logger.debug("Fetching begin/end offsets for topic '{}'", topicName)
        val descriptions = adminClient.describeTopics(listOf(topicName)).allTopicNames().get(10, TimeUnit.SECONDS)
        val partitions: List<TopicPartition> = descriptions[topicName]
            ?.partitions()
            ?.map { TopicPartition(topicName, it.partition()) }
            ?: return emptyMap()
        val earliestSpec: Map<TopicPartition, OffsetSpec> = partitions.associateWith { OffsetSpec.earliest() }
        val latestSpec: Map<TopicPartition, OffsetSpec> = partitions.associateWith { OffsetSpec.latest() }
        val earliestResults = adminClient.listOffsets(earliestSpec).all().get(10, TimeUnit.SECONDS)
        val latestResults = adminClient.listOffsets(latestSpec).all().get(10, TimeUnit.SECONDS)
        return partitions.associate { tp ->
            val begin: Long = earliestResults[tp]?.offset() ?: 0L
            val end: Long = latestResults[tp]?.offset() ?: 0L
            tp.partition() to PartitionOffsets(begin, end)
        }
    }

    data class TopicLogDirStats(val leaderBytes: Long, val totalBytes: Long)

    fun getTopicLogDirStats(topicName: String): TopicLogDirStats {
        logger.debug("Fetching log dir stats for topic '{}'", topicName)
        val descriptions = adminClient.describeTopics(listOf(topicName)).allTopicNames().get(10, TimeUnit.SECONDS)
        val partitionLeaders: Map<Int, Int> = descriptions[topicName]
            ?.partitions()
            ?.associate { it.partition() to (it.leader()?.id() ?: -1) }
            ?: return TopicLogDirStats(0L, 0L)
        val brokerIds: List<Int> = adminClient.describeCluster().nodes().get(10, TimeUnit.SECONDS).map { it.id() }
        val logDirs = adminClient.describeLogDirs(brokerIds).allDescriptions().get(10, TimeUnit.SECONDS)
        var leaderBytes: Long = 0L
        var totalBytes: Long = 0L
        for ((brokerId, dirMap) in logDirs) {
            for ((_, logDirDescription) in dirMap) {
                for ((tp, replicaInfo) in logDirDescription.replicaInfos()) {
                    if (tp.topic() != topicName) continue
                    val size: Long = replicaInfo.size()
                    totalBytes += size
                    if (partitionLeaders[tp.partition()] == brokerId) {
                        leaderBytes += size
                    }
                }
            }
        }
        return TopicLogDirStats(leaderBytes, totalBytes)
    }

    fun truncateTopic(topicName: String) {
        logger.info("Truncating topic '{}'", topicName)
        val descriptions = adminClient.describeTopics(listOf(topicName)).allTopicNames().get(10, TimeUnit.SECONDS)
        val partitions: List<TopicPartition> = descriptions[topicName]
            ?.partitions()
            ?.map { TopicPartition(topicName, it.partition()) }
            ?: return
        val latestOffsets = adminClient.listOffsets(
            partitions.associateWith { OffsetSpec.latest() }
        ).all().get(10, TimeUnit.SECONDS)
        val recordsToDelete: Map<TopicPartition, RecordsToDelete> = latestOffsets
            .mapValues { (_, result) -> RecordsToDelete.beforeOffset(result.offset()) }
        adminClient.deleteRecords(recordsToDelete).all().get(10, TimeUnit.SECONDS)
        logger.info("Topic '{}' truncated across {} partition(s)", topicName, partitions.size)
    }

    override fun close() {
        try {
            adminClient.close()
            logger.debug("AdminClient closed")
        } catch (e: Exception) {
            logger.warn("Error closing AdminClient", e)
        }
    }
}
