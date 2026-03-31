package kafka

import model.TopicInfo
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
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

    override fun close() {
        try {
            adminClient.close()
        } catch (_: Exception) {}
    }
}
