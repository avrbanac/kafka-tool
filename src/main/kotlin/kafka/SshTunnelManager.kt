package kafka

import model.SshAuthType
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder
import net.schmizz.sshj.connection.channel.direct.Parameters
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class SshTunnelManager(
    private val sshHost: String,
    private val sshPort: Int,
    private val sshUsername: String,
    private val sshAuthType: SshAuthType,
    private val sshKeyPath: String,
    private val sshPassword: String,
    private val proxyJumpEnabled: Boolean = false,
    private val proxyJumpHost: String = "",
    private val proxyJumpPort: Int = 22,
    private val proxyJumpUsername: String = "",
    private val proxyJumpKeyPath: String = ""
) : Closeable {
    private val logger: Logger = LoggerFactory.getLogger(SshTunnelManager::class.java)

    private var jumpClient: SSHClient? = null
    private var sshClient: SSHClient? = null
    private val tunnels: MutableList<TunnelEntry> = mutableListOf()
    private val hostnameToLoopback: ConcurrentHashMap<String, InetAddress> = ConcurrentHashMap()

    fun connect() {
        if (proxyJumpEnabled && proxyJumpHost.isNotBlank()) {
            connectViaJumpHost()
        } else {
            connectDirect()
        }
    }

    private fun connectDirect() {
        logger.info("Connecting SSH to {}@{}:{}", sshUsername, sshHost, sshPort)
        val client = SSHClient()
        client.addHostKeyVerifier(PromiscuousVerifier())
        client.connect(sshHost, sshPort)
        authenticateClient(client, sshUsername, sshKeyPath, sshAuthType)
        sshClient = client
        logger.info("SSH connection established to {}:{}", sshHost, sshPort)
    }

    private fun connectViaJumpHost() {
        logger.info("Connecting SSH via jump host: {}@{}:{} -> {}@{}:{}",
            proxyJumpUsername, proxyJumpHost, proxyJumpPort,
            sshUsername, sshHost, sshPort)

        val jump = SSHClient()
        jump.addHostKeyVerifier(PromiscuousVerifier())
        jump.connect(proxyJumpHost, proxyJumpPort)
        authenticateClient(jump, proxyJumpUsername, proxyJumpKeyPath, SshAuthType.KEY_FILE)
        jumpClient = jump
        logger.info("Connected to jump host {}:{}", proxyJumpHost, proxyJumpPort)

        val directConnection = jump.newDirectConnection(sshHost, sshPort)

        val target = SSHClient()
        target.addHostKeyVerifier(PromiscuousVerifier())
        target.connectVia(directConnection)
        authenticateClient(target, sshUsername, sshKeyPath, sshAuthType)
        sshClient = target
        logger.info("SSH connection established to {}:{} via jump host", sshHost, sshPort)
    }

    private fun authenticateClient(client: SSHClient, username: String, keyPath: String, authType: SshAuthType) {
        when (authType) {
            SshAuthType.KEY_FILE -> {
                val resolvedPath: String = resolveKeyPath(keyPath)
                logger.debug("Using SSH key: {}", resolvedPath)
                val keyProvider: KeyProvider = if (sshPassword.isNotBlank() && keyPath == sshKeyPath) {
                    client.loadKeys(resolvedPath, sshPassword)
                } else {
                    client.loadKeys(resolvedPath)
                }
                client.authPublickey(username, keyProvider)
            }
            SshAuthType.PASSWORD -> {
                client.authPassword(username, sshPassword)
            }
        }
    }

    fun createTunnel(remoteHost: String, remotePort: Int): InetAddress {
        val client: SSHClient = sshClient ?: throw IllegalStateException("SSH not connected")

        val existing: InetAddress? = hostnameToLoopback[remoteHost]
        if (existing != null) {
            logger.debug("Tunnel already exists for {}", remoteHost)
            return existing
        }

        val bindResult: Pair<InetAddress, ServerSocket> = bindAvailableLoopback(remotePort)
        val loopbackAddress: InetAddress = bindResult.first
        val serverSocket: ServerSocket = bindResult.second
        logger.info("Creating tunnel: {}:{} -> {}:{}", loopbackAddress.hostAddress, remotePort, remoteHost, remotePort)

        val params = Parameters(
            loopbackAddress.hostAddress,
            remotePort,
            remoteHost,
            remotePort
        )
        val forwarder: LocalPortForwarder = client.newLocalPortForwarder(params, serverSocket)

        val forwarderThread = Thread({
            try {
                forwarder.listen()
            } catch (e: Exception) {
                if (sshClient != null) {
                    logger.warn("Tunnel listener stopped for {}:{} - {}", remoteHost, remotePort, e.message)
                }
            }
        }, "ssh-tunnel-$remoteHost-$remotePort")
        forwarderThread.isDaemon = true
        forwarderThread.start()

        hostnameToLoopback[remoteHost] = loopbackAddress
        tunnels.add(TunnelEntry(remoteHost, remotePort, loopbackAddress, serverSocket, forwarderThread))
        logger.info("Tunnel active: {}:{} -> {}:{}", loopbackAddress.hostAddress, remotePort, remoteHost, remotePort)
        return loopbackAddress
    }

    fun generateHostnameMapping(): String {
        val builder: StringBuilder = StringBuilder()
        for ((hostname, loopback) in hostnameToLoopback) {
            builder.appendLine("${loopback.hostAddress} $hostname")
        }
        return builder.toString().trim()
    }

    fun getTunneledHosts(): Set<String> {
        return hostnameToLoopback.keys.toSet()
    }

    override fun close() {
        logger.info("Closing SSH tunnel manager ({} tunnel(s))", tunnels.size)
        for (entry in tunnels) {
            try {
                entry.serverSocket.close()
            } catch (e: Exception) {
                logger.debug("Error closing server socket for {}: {}", entry.remoteHost, e.message)
            }
        }
        tunnels.clear()
        hostnameToLoopback.clear()

        try {
            sshClient?.disconnect()
        } catch (e: Exception) {
            logger.debug("Error disconnecting SSH: {}", e.message)
        }
        sshClient = null

        try {
            jumpClient?.disconnect()
        } catch (e: Exception) {
            logger.debug("Error disconnecting jump host SSH: {}", e.message)
        }
        jumpClient = null

        logger.info("SSH tunnel manager closed")
    }

    private fun bindAvailableLoopback(port: Int): Pair<InetAddress, ServerSocket> {
        val maxAttempts = 10
        for (attempt in 0 until maxAttempts) {
            val address: InetAddress = allocateLoopbackAddress()
            val serverSocket = ServerSocket()
            serverSocket.reuseAddress = true
            try {
                serverSocket.bind(InetSocketAddress(address, port))
                return Pair(address, serverSocket)
            } catch (e: java.net.BindException) {
                logger.warn("Address {}:{} already in use, trying next", address.hostAddress, port)
                serverSocket.close()
            }
        }
        throw IllegalStateException("Could not find an available loopback address after $maxAttempts attempts")
    }

    private fun allocateLoopbackAddress(): InetAddress {
        val index: Int = loopbackCounter.getAndIncrement()
        val thirdOctet: Int = (index / 254)
        val fourthOctet: Int = (index % 254) + 2
        val addressBytes: ByteArray = byteArrayOf(127, thirdOctet.toByte(), 0, fourthOctet.toByte())
        return InetAddress.getByAddress(addressBytes)
    }

    private fun resolveKeyPath(configuredPath: String): String {
        if (configuredPath.isNotBlank()) {
            return expandTilde(configuredPath)
        }
        val sshDir = File(System.getProperty("user.home"), ".ssh")
        val defaultKeyNames: List<String> = listOf("id_ed25519", "id_ecdsa", "id_rsa")
        for (name in defaultKeyNames) {
            val keyFile = File(sshDir, name)
            if (keyFile.exists()) {
                logger.debug("Auto-detected SSH key: {}", keyFile.absolutePath)
                return keyFile.absolutePath
            }
        }
        throw IllegalStateException("No SSH key found. Specify a key path or create a key in ~/.ssh/")
    }

    private fun expandTilde(path: String): String {
        return if (path.startsWith("~/")) {
            System.getProperty("user.home") + path.substring(1)
        } else {
            path
        }
    }

    private data class TunnelEntry(
        val remoteHost: String,
        val remotePort: Int,
        val localAddress: InetAddress,
        val serverSocket: ServerSocket,
        val forwarderThread: Thread
    )

    companion object {
        private val loopbackCounter: AtomicInteger = AtomicInteger(0)
    }
}
