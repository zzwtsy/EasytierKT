package com.github.zzwtsy.easytierkt.data.profile

enum class ProfileSection { NETWORK, ROUTING, SECURITY, DNS, TRANSPORT, PROXY, ACL }

enum class ProfileProperty(
    val path: String,
    val section: ProfileSection,
) {
    DISPLAY_NAME("displayName", ProfileSection.NETWORK),
    NETWORK_NAME("networkName", ProfileSection.NETWORK),
    NETWORK_SECRET("networkSecret", ProfileSection.NETWORK),
    HOSTNAME("hostname", ProfileSection.NETWORK),
    IPV4_ADDRESS("ipv4Address", ProfileSection.NETWORK),
    IPV4_PREFIX("ipv4Prefix", ProfileSection.NETWORK),
    VIRTUAL_IPV6("virtualIpv6", ProfileSection.NETWORK),
    IPV6_PREFIX("ipv6Prefix", ProfileSection.NETWORK),
    PEER_ADDRESSES("peerAddresses", ProfileSection.NETWORK),
    LISTENERS("listeners", ProfileSection.NETWORK),
    MAPPED_LISTENERS("mappedListeners", ProfileSection.NETWORK),
    ROUTES("routes", ProfileSection.ROUTING),
    ROUTING_IPV6_ROUTES("routing.ipv6Routes", ProfileSection.ROUTING),
    ROUTING_EXIT_NODES("routing.exitNodes", ProfileSection.ROUTING),
    ROUTING_APPLICATIONS("routing.applications", ProfileSection.ROUTING),
    SECURITY_PRIVATE_KEY("security.privateKey", ProfileSection.SECURITY),
    SECURITY_PUBLIC_KEY("security.publicKey", ProfileSection.SECURITY),
    SECURITY_PEER_KEYS("security.peerKeys", ProfileSection.SECURITY),
    TRANSPORT_RELAY_WHITELIST("transport.relayWhitelist", ProfileSection.TRANSPORT),
    TRANSPORT_ENCRYPTION("transport.encryption", ProfileSection.TRANSPORT),
    DNS_SERVERS("dns.servers", ProfileSection.DNS),
    DNS_ZONE("dns.zone", ProfileSection.DNS),
    TRANSPORT_MTU("transport.mtu", ProfileSection.TRANSPORT),
    TRANSPORT_DEFAULT_PROTOCOL("transport.defaultProtocol", ProfileSection.TRANSPORT),
    TRANSPORT_ENCRYPTION_ALGORITHM("transport.encryptionAlgorithm", ProfileSection.TRANSPORT),
    TRANSPORT_THREAD_COUNT("transport.threadCount", ProfileSection.TRANSPORT),
    TRANSPORT_RECEIVE_BYTES_PER_SECOND("transport.receiveBytesPerSecond", ProfileSection.TRANSPORT),
    TRANSPORT_FOREIGN_RELAY_BYTES_PER_SECOND("transport.foreignRelayBytesPerSecond", ProfileSection.TRANSPORT),
    TRANSPORT_LAZY_P2P("transport.lazyP2p", ProfileSection.TRANSPORT),
    TRANSPORT_STUN_SERVERS("transport.stunServers", ProfileSection.TRANSPORT),
    TRANSPORT_STUN_SERVERS_V6("transport.stunServersV6", ProfileSection.TRANSPORT),
    LOCAL_PROXY_BIND_ADDRESS("localProxy.bindAddress", ProfileSection.PROXY),
    LOCAL_PROXY_PORT("localProxy.port", ProfileSection.PROXY),
    ACL_CHAINS("acl.chains", ProfileSection.ACL),
    ACL_DECLARES("acl.declares", ProfileSection.ACL),
    ACL_MEMBERS("acl.members", ProfileSection.ACL),
}

enum class EntryKind(
    val prefix: String,
    val section: ProfileSection,
) {
    PEER_KEY("peerKey", ProfileSection.SECURITY),
    FORWARD("forward", ProfileSection.PROXY),
    GROUP("group", ProfileSection.ACL),
    CHAIN("chain", ProfileSection.ACL),
    RULE("rule", ProfileSection.ACL),
}

enum class EntryProperty(
    val path: String,
) {
    VALUE(""),
    NAME("name"),
    SECRET("secret"),
    URI("uri"),
    PUBLIC_KEY("publicKey"),
    PROTOCOL("protocol"),
    BIND_ADDRESS("bindAddress"),
    BIND_PORT("bindPort"),
    DESTINATION("destination"),
    DESTINATION_PORT("destinationPort"),
    DESCRIPTION("description"),
    PRIORITY("priority"),
    RATE_LIMIT("rateLimit"),
    BURST_LIMIT("burstLimit"),
    SOURCE_IPS("sourceIps"),
    DESTINATION_IPS("destinationIps"),
    SOURCE_PORTS("sourcePorts"),
    DESTINATION_PORTS("destinationPorts"),
    SOURCE_GROUPS("sourceGroups"),
    DESTINATION_GROUPS("destinationGroups"),
}

/** 字段标识不包含文本或密钥；动态规则记录所属链，删除条目可精确清理输入状态。 */
sealed interface ProfileField {
    val section: ProfileSection
    val path: String

    data class Scalar(
        val property: ProfileProperty,
    ) : ProfileField {
        override val section get() = property.section
        override val path get() = property.path
    }

    data class Entry(
        val kind: EntryKind,
        val id: String,
        val property: EntryProperty = EntryProperty.VALUE,
        val chainId: String? = null,
    ) : ProfileField {
        override val section get() = kind.section
        override val path get() =
            "${kind.prefix}.$id" +
                property.path
                    .takeIf(String::isNotEmpty)
                    ?.let { ".$it" }
                    .orEmpty()
    }

    companion object {
        fun fromPath(
            path: String,
            profile: ConnectionProfile? = null,
        ): ProfileField {
            ProfileProperty.entries.find { it.path == path }?.let { return Scalar(it) }
            val parts = path.split('.')
            val kind = EntryKind.entries.first { it.prefix == parts[0] }
            val id = parts[1]
            val property = EntryProperty.entries.first { it.path == parts.drop(2).joinToString(".") }
            val chainId =
                if (kind == EntryKind.RULE) {
                    profile
                        ?.acl
                        ?.chains
                        ?.find { chain -> chain.rules.any { it.id == id } }
                        ?.id
                } else {
                    null
                }
            return Entry(kind, id, property, chainId)
        }
    }
}

enum class ProfileIssueCode {
    CIDR,
    ENDPOINT,
    PORT_RANGE,
    NETWORK_NAME,
    NETWORK_SECRET,
    HOSTNAME,
    STATIC_IPV4,
    IPV4_PREFIX,
    VIRTUAL_IPV6,
    IPV6_PREFIX,
    DUPLICATE_LISTENER,
    IPV6_ROUTE_WITHOUT_ADDRESS,
    EXIT_NODE,
    EMPTY_APPLICATIONS,
    APPLICATION,
    PRIVATE_KEY,
    PUBLIC_KEY,
    ENCRYPTION_REQUIRED,
    SECURE_HANDSHAKE_REQUIRED,
    PEER_PIN,
    DUPLICATE_PEER_PIN,
    DNS_SERVER,
    DNS_ZONE,
    MTU,
    TRANSPORT_PROTOCOL,
    ENCRYPTION_ALGORITHM,
    THREAD_COUNT,
    RECEIVE_LIMIT,
    RELAY_LIMIT,
    LAZY_P2P,
    STUN_SERVER,
    BIND_ADDRESS,
    BIND_PORT,
    PORT_FORWARD,
    BIND_CONFLICT,
    DUPLICATE_CHAIN,
    GROUP,
    DUPLICATE_GROUP,
    CHAIN_NAME,
    RULE_NAME,
    PRIORITY,
    DUPLICATE_PRIORITY,
    RATE_LIMIT,
    BURST_LIMIT,
    ACL_ADDRESS,
    ICMP_PORTS,
}

data class ProfileIssue(
    val field: ProfileField,
    val code: ProfileIssueCode,
)
