package kafka

import model.TopicInfo
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.AlterConfigOp
import org.apache.kafka.clients.admin.ConfigEntry
import org.apache.kafka.clients.admin.OffsetSpec
import org.apache.kafka.clients.admin.RecordsToDelete
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.config.ConfigResource
import org.apache.kafka.clients.admin.NewTopic
import java.util.Properties
import java.util.concurrent.TimeUnit

class AdminClientWrapper(bootstrapServers: String, profileId: String = "", hostnameMapping: String = "") : AutoCloseable {
    private val adminClient: AdminClient

    init {
        MappingHostnameStore.applyMapping(profileId, hostnameMapping)
        val props = Properties()
        props[AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG] = "10000"
        props[AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG] = "10000"
        adminClient = AdminClient.create(props)
    }

    fun testConnection() {
        adminClient.listTopics().names().get(10, TimeUnit.SECONDS)
    }

    fun listTopics(): List<TopicInfo> {
        val names: Set<String> = adminClient.listTopics().names().get(10, TimeUnit.SECONDS)
        val descriptions = adminClient.describeTopics(names).allTopicNames().get(10, TimeUnit.SECONDS)
        return descriptions.map { (name, desc) ->
            TopicInfo(
                name = name,
                partitionCount = desc.partitions().size,
                replicationFactor = desc.partitions().firstOrNull()?.replicas()?.size ?: 0,
                isInternal = desc.isInternal
            )
        }.sortedBy { it.name }
    }

    fun createTopic(name: String, partitions: Int, replicationFactor: Short) {
        adminClient.createTopics(listOf(NewTopic(name, partitions, replicationFactor)))
            .all().get(10, TimeUnit.SECONDS)
    }

    fun deleteTopic(name: String) {
        adminClient.deleteTopics(listOf(name)).all().get(10, TimeUnit.SECONDS)
    }

    fun getTopicConfig(topicName: String): Map<String, String> {
        val resource = ConfigResource(ConfigResource.Type.TOPIC, topicName)
        val result = adminClient.describeConfigs(listOf(resource)).all().get(10, TimeUnit.SECONDS)
        return result[resource]?.entries()?.associate { it.name() to (it.value() ?: "") } ?: emptyMap()
    }

    fun getTopicConfigOverrides(topicName: String): Map<String, String> {
        val resource = ConfigResource(ConfigResource.Type.TOPIC, topicName)
        val result = adminClient.describeConfigs(listOf(resource)).all().get(10, TimeUnit.SECONDS)
        return result[resource]?.entries()
            ?.filter { it.source() == ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG }
            ?.associate { it.name() to (it.value() ?: "") }
            ?: emptyMap()
    }

    fun updateTopicConfig(topicName: String, setEntries: Map<String, String>, deleteKeys: Set<String>) {
        val resource = ConfigResource(ConfigResource.Type.TOPIC, topicName)
        val ops: MutableList<AlterConfigOp> = mutableListOf()
        for ((key, value) in setEntries) {
            ops.add(AlterConfigOp(ConfigEntry(key, value), AlterConfigOp.OpType.SET))
        }
        for (key in deleteKeys) {
            ops.add(AlterConfigOp(ConfigEntry(key, ""), AlterConfigOp.OpType.DELETE))
        }
        adminClient.incrementalAlterConfigs(mapOf(resource to ops)).all().get(10, TimeUnit.SECONDS)
    }

    fun truncateTopic(topicName: String) {
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
    }

    override fun close() {
        try {
            adminClient.close()
        } catch (_: Exception) {}
    }
}
