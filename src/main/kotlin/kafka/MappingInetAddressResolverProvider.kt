package kafka

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.UnknownHostException
import java.net.spi.InetAddressResolver
import java.net.spi.InetAddressResolver.LookupPolicy
import java.net.spi.InetAddressResolverProvider
import java.util.stream.Stream

class MappingInetAddressResolverProvider : InetAddressResolverProvider() {
    private val logger: Logger = LoggerFactory.getLogger(MappingInetAddressResolverProvider::class.java)

    override fun get(configuration: Configuration): InetAddressResolver {
        logger.debug("Creating MappingInetAddressResolver")
        return MappingInetAddressResolver(configuration.builtinResolver())
    }

    override fun name(): String = "kafka-tool-mapping-resolver"
}

private class MappingInetAddressResolver(
    private val builtinResolver: InetAddressResolver
) : InetAddressResolver {
    private val logger: Logger = LoggerFactory.getLogger(MappingInetAddressResolver::class.java)

    override fun lookupByName(host: String, lookupPolicy: LookupPolicy): Stream<InetAddress> {
        val mapped: InetAddress? = MappingHostnameStore.resolve(host)
        if (mapped != null) {
            logger.debug("Resolved '{}' via mapping to {}", host, mapped)
            return Stream.of(mapped)
        }
        // When SSH tunneling is active, unmapped hosts are unreachable.
        // Fail fast instead of blocking on slow DNS timeout.
        if (MappingHostnameStore.sshTunnelActive) {
            logger.debug("Fast-fail DNS for unmapped host '{}' (SSH tunnel active)", host)
            throw UnknownHostException("$host: not in hostname mapping (SSH tunnel active)")
        }
        return builtinResolver.lookupByName(host, lookupPolicy)
    }

    override fun lookupByAddress(addr: ByteArray): String {
        return builtinResolver.lookupByAddress(addr)
    }
}
