package kafka

import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

object MappingHostnameStore {

    private val mappingsByProfile: ConcurrentHashMap<String, Map<String, InetAddress>> = ConcurrentHashMap()

    fun applyMapping(profileId: String, hostnameMapping: String) {
        mappingsByProfile[profileId] = parseMapping(hostnameMapping)
    }

    fun removeMapping(profileId: String) {
        mappingsByProfile.remove(profileId)
    }

    fun resolve(host: String): InetAddress? {
        for ((_, entries) in mappingsByProfile) {
            val found: InetAddress? = entries[host]
            if (found != null) return found
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
            } catch (_: Exception) {
                continue
            }
            for (i in 1 until tokens.size) {
                val hostname: String = tokens[i]
                val addressWithHostname: InetAddress = try {
                    InetAddress.getByAddress(hostname, ipAddress.address)
                } catch (_: Exception) {
                    continue
                }
                result[hostname] = addressWithHostname
            }
        }
        return result
    }
}
