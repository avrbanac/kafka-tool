package kafka

import model.BrokerNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Fetches Kafka cluster metadata via a raw socket connection,
 * bypassing the Kafka AdminClient's node management.
 * Used during SSH tunnel setup to discover broker addresses
 * before tunnels for all brokers are established.
 */
object KafkaMetadataFetcher {
    private val logger: Logger = LoggerFactory.getLogger(KafkaMetadataFetcher::class.java)

    private const val API_KEY_METADATA: Short = 3
    private const val API_VERSION: Short = 1
    private const val CORRELATION_ID: Int = 1
    private const val CLIENT_ID: String = "kafka-tool-metadata"

    fun fetchBrokerNodes(bootstrapHost: String, bootstrapPort: Int, timeoutMs: Int = 10000): List<BrokerNode> {
        logger.info("Fetching metadata from {}:{}", bootstrapHost, bootstrapPort)

        val socket = Socket()
        socket.connect(InetSocketAddress(bootstrapHost, bootstrapPort), timeoutMs)
        socket.soTimeout = timeoutMs

        try {
            val output = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())

            // Build Metadata request (API v1, null topics = all)
            val requestBody: ByteArray = buildMetadataRequest()
            // Send: size (4 bytes) + request
            output.writeInt(requestBody.size)
            output.write(requestBody)
            output.flush()

            // Read response: size (4 bytes) + response
            val responseSize: Int = input.readInt()
            val responseBytes = ByteArray(responseSize)
            input.readFully(responseBytes)

            val brokers: List<BrokerNode> = parseMetadataResponse(responseBytes)
            logger.info("Discovered {} broker(s): {}", brokers.size, brokers)
            return brokers
        } finally {
            socket.close()
        }
    }

    private fun buildMetadataRequest(): ByteArray {
        // Request header v1: api_key(2) + api_version(2) + correlation_id(4) + client_id(string)
        // Metadata request v1 body: topics array (null = all topics, we send empty array)
        val clientIdBytes: ByteArray = CLIENT_ID.toByteArray(Charsets.UTF_8)
        val size: Int = 2 + 2 + 4 + 2 + clientIdBytes.size + 4  // header + topics array length
        val buffer = ByteArray(size)
        var offset = 0

        // API key
        buffer[offset++] = (API_KEY_METADATA.toInt() shr 8).toByte()
        buffer[offset++] = API_KEY_METADATA.toByte()

        // API version
        buffer[offset++] = (API_VERSION.toInt() shr 8).toByte()
        buffer[offset++] = API_VERSION.toByte()

        // Correlation ID
        buffer[offset++] = (CORRELATION_ID shr 24).toByte()
        buffer[offset++] = (CORRELATION_ID shr 16).toByte()
        buffer[offset++] = (CORRELATION_ID shr 8).toByte()
        buffer[offset++] = CORRELATION_ID.toByte()

        // Client ID (string: length(2) + bytes)
        buffer[offset++] = (clientIdBytes.size shr 8).toByte()
        buffer[offset++] = clientIdBytes.size.toByte()
        System.arraycopy(clientIdBytes, 0, buffer, offset, clientIdBytes.size)
        offset += clientIdBytes.size

        // Topics array: empty array (length = 0) to get only broker metadata
        buffer[offset++] = 0
        buffer[offset++] = 0
        buffer[offset++] = 0
        buffer[offset] = 0

        return buffer
    }

    private fun parseMetadataResponse(data: ByteArray): List<BrokerNode> {
        var offset = 0

        // Response header: correlation_id (4 bytes)
        offset += 4

        // Broker array: count (4 bytes)
        val brokerCount: Int = readInt(data, offset)
        offset += 4

        val brokers: MutableList<BrokerNode> = mutableListOf()

        for (i in 0 until brokerCount) {
            // node_id (4 bytes)
            offset += 4

            // host (string: length(2) + bytes)
            val hostLength: Int = readShort(data, offset)
            offset += 2
            val host = String(data, offset, hostLength, Charsets.UTF_8)
            offset += hostLength

            // port (4 bytes)
            val port: Int = readInt(data, offset)
            offset += 4

            // rack (nullable string, v1+): length(2), -1 = null
            val rackLength: Int = readShort(data, offset)
            offset += 2
            if (rackLength >= 0) {
                offset += rackLength
            }

            brokers.add(BrokerNode(host = host, port = port))
        }

        return brokers
    }

    private fun readInt(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 24) or
                ((data[offset + 1].toInt() and 0xFF) shl 16) or
                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)
    }

    private fun readShort(data: ByteArray, offset: Int): Int {
        val value: Int = ((data[offset].toInt() and 0xFF) shl 8) or
                (data[offset + 1].toInt() and 0xFF)
        // Sign-extend for nullable strings (0xFFFF = -1)
        return if (value >= 0x8000) value - 0x10000 else value
    }
}
