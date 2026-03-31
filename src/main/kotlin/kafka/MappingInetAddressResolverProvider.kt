package kafka

import java.net.InetAddress
import java.net.spi.InetAddressResolver
import java.net.spi.InetAddressResolver.LookupPolicy
import java.net.spi.InetAddressResolverProvider
import java.util.stream.Stream

class MappingInetAddressResolverProvider : InetAddressResolverProvider() {

    override fun get(configuration: Configuration): InetAddressResolver {
        return MappingInetAddressResolver(configuration.builtinResolver())
    }

    override fun name(): String = "kafka-tool-mapping-resolver"
}

private class MappingInetAddressResolver(
    private val builtinResolver: InetAddressResolver
) : InetAddressResolver {

    override fun lookupByName(host: String, lookupPolicy: LookupPolicy): Stream<InetAddress> {
        val mapped: InetAddress? = MappingHostnameStore.resolve(host)
        return if (mapped != null) {
            Stream.of(mapped)
        } else {
            builtinResolver.lookupByName(host, lookupPolicy)
        }
    }

    override fun lookupByAddress(addr: ByteArray): String {
        return builtinResolver.lookupByAddress(addr)
    }
}
