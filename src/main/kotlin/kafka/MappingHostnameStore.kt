package kafka

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object MappingHostnameStore {
    private val logger: Logger = LoggerFactory.getLogger(MappingHostnameStore::class.java)

    private val mappingsByProfile: ConcurrentHashMap<String, Map<String, InetAddress>> = ConcurrentHashMap()
    private val _sshTunnelActive: AtomicBoolean = AtomicBoolean(false)

    val sshTunnelActive: Boolean get() = _sshTunnelActive.get()

    fun setSshTunnelActive(active: Boolean) {
        _sshTunnelActive.set(active)
        logger.debug("SSH tunnel active flag set to {}", active)
    }

    fun applyMapping(profileId: String, hostnameMapping: String) {
        val parsed: Map<String, InetAddress> = parseMapping(hostnameMapping)
        mappingsByProfile[profileId] = parsed
        if (parsed.isNotEmpty()) {
            logger.debug("Applied {} hostname mapping(s) for profile '{}'", parsed.size, profileId)
        }
    }

    fun removeMapping(profileId: String) {
        mappingsByProfile.remove(profileId)
        logger.debug("Removed hostname mappings for profile '{}'", profileId)
    }

    fun resolve(host: String): InetAddress? {
        for ((_, entries) in mappingsByProfile) {
            val found: InetAddress? = entries[host]
            if (found != null) {
                logger.debug("Resolved hostname '{}' to mapped address {}", host, found)
                return found
            }
        }
        return null
    }

    private fun parseMapping(hostnameMapping: String): Map<String, InetAddress> {
        val result: MutableMap<String, InetAddress> = mutableMapOf()
        for (line in hostnameMapping.lines()) {
            val trimmed: String = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) continue
            val tokens: List<String> = trimmed.split(Regex("\\s+"))
            if (tokens.size < 2) continue
            val ipString: String = tokens[0]
            val ipAddress: InetAddress = try {
                InetAddress.getByName(ipString)
            } catch (e: Exception) {
                logger.warn("Failed to parse IP address '{}' in hostname mapping", ipString, e)
                continue
            }
            for (i in 1 until tokens.size) {
                val hostname: String = tokens[i]
                val addressWithHostname: InetAddress = try {
                    InetAddress.getByAddress(hostname, ipAddress.address)
                } catch (e: Exception) {
                    logger.warn("Failed to create address for hostname '{}' with IP '{}'", hostname, ipString, e)
                    continue
                }
                result[hostname] = addressWithHostname
            }
        }
        return result
    }
}
