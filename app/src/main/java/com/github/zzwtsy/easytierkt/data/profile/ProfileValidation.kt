package com.github.zzwtsy.easytierkt.data.profile

/** 所有启用项使用同一组纯校验器；无效列表条目不能静默丢弃。 */
internal fun validateProfile(p: ConnectionProfile): List<ProfileIssue> = ProfileValidator(p).validate()

private class ProfileValidator(
    private val p: ConnectionProfile,
) {
    private val issues = mutableListOf<ProfileIssue>()

    private fun add(issue: ProfileIssue) {
        issues += issue
    }

    fun validate(): List<ProfileIssue> {
        network()
        routing()
        security()
        dns()
        transport()
        proxy()
        acl()
        return issues.toList()
    }

    private fun check(
        field: String,
        valid: Boolean,
        code: ProfileIssueCode,
    ) {
        if (!valid) add(ProfileIssue(ProfileField.fromPath(field, p), code))
    }

    private fun cidrs(
        field: String,
        raw: String,
        size: Int,
        allowDefault: Boolean = false,
    ) {
        check(
            field,
            raw.configEntries().all { value ->
                normalizedCidr(value) != null &&
                    numericIp(value.substringBefore('/'))?.address?.size == size &&
                    (allowDefault || value.substringAfter('/').toIntOrNull() != 0)
            },
            ProfileIssueCode.CIDR,
        )
    }

    private fun endpoints(
        field: String,
        raw: String,
        listener: Boolean = false,
    ) = check(
        field,
        raw.configEntries().all {
            validEndpoint(it, listener)
        },
        ProfileIssueCode.ENDPOINT,
    )

    private fun ports(
        field: String,
        raw: String,
    ) = check(
        field,
        raw.configEntries().all {
            val pair = it.split('-')
            pair.size in 1..2 && pair.all { n -> n.toIntOrNull() in 1..65535 } && (pair.size == 1 || pair[0].toInt() <= pair[1].toInt())
        },
        ProfileIssueCode.PORT_RANGE,
    )

    private fun network() {
        check(
            "networkName",
            p.networkName.isNotBlank() && p.networkName.length <= 128 && p.networkName.none(Char::isISOControl),
            ProfileIssueCode.NETWORK_NAME,
        )
        if (!p.credentialMode) {
            check(
                "networkSecret",
                p.networkSecret.length <= 1024 && p.networkSecret.none(Char::isISOControl),
                ProfileIssueCode.NETWORK_SECRET,
            )
        }
        check(
            "hostname",
            p.hostname.isBlank() || p.hostname.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,30}[A-Za-z0-9])?")),
            ProfileIssueCode.HOSTNAME,
        )
        if (!p.useDhcp) {
            check(
                "ipv4Address",
                numericIp(
                    p.ipv4Address,
                )?.let { it.address.size == 4 && !it.isAnyLocalAddress && !it.isMulticastAddress && !it.isLoopbackAddress } ==
                    true,
                ProfileIssueCode.STATIC_IPV4,
            )
            check("ipv4Prefix", p.ipv4Prefix in 1..32, ProfileIssueCode.IPV4_PREFIX)
        }
        if (p.virtualIpv6.isNotBlank()) {
            check(
                "virtualIpv6",
                numericIp(p.virtualIpv6)?.let {
                    it.address.size == 16 &&
                        !it.isAnyLocalAddress &&
                        !it.isMulticastAddress &&
                        !it.isLoopbackAddress &&
                        !it.isLinkLocalAddress
                } ==
                    true,
                ProfileIssueCode.VIRTUAL_IPV6,
            )
            check("ipv6Prefix", p.ipv6Prefix in 1..128, ProfileIssueCode.IPV6_PREFIX)
        }
    }

    private fun routing() {
        endpoints("peerAddresses", p.peerAddresses)
        endpoints("listeners", p.listeners, true)
        endpoints("mappedListeners", p.mappedListeners, true)
        check(
            "listeners",
            p.listeners
                .configEntries()
                .distinct()
                .size == p.listeners.configEntries().size,
            ProfileIssueCode.DUPLICATE_LISTENER,
        )
        cidrs("routes", p.routes, 4)
        if (p.virtualIpv6.isNotBlank()) {
            cidrs("routing.ipv6Routes", p.routing.ipv6Routes, 16)
        } else {
            check("routing.ipv6Routes", p.routing.ipv6Routes.isBlank(), ProfileIssueCode.IPV6_ROUTE_WITHOUT_ADDRESS)
        }
        check(
            "routing.exitNodes",
            p.routing.exitNodes.configEntries().all {
                numericIp(it)?.let { ip ->
                    ip.address.size == 4 &&
                        !ip.isAnyLocalAddress &&
                        !ip.isMulticastAddress &&
                        !ip.isLoopbackAddress
                } ==
                    true
            },
            ProfileIssueCode.EXIT_NODE,
        )
        check(
            "routing.applications",
            p.routing.applicationMode != ApplicationMode.INCLUDE || p.routing.applications.isNotEmpty(),
            ProfileIssueCode.EMPTY_APPLICATIONS,
        )
        check(
            "routing.applications",
            p.routing.applications.all {
                it.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")) &&
                    it != "com.github.zzwtsy.easytierkt"
            },
            ProfileIssueCode.APPLICATION,
        )
    }

    private fun security() {
        val secure = p.credentialMode || p.security.secureMode
        if (secure) {
            check(
                "security.privateKey",
                if (p.credentialMode) {
                    validKey(p.security.privateKey)
                } else {
                    p.security.privateKey.isBlank() ||
                        validKey(p.security.privateKey)
                },
                ProfileIssueCode.PRIVATE_KEY,
            )
            check("security.publicKey", p.security.publicKey.isBlank() || validKey(p.security.publicKey), ProfileIssueCode.PUBLIC_KEY)
            check("transport.encryption", p.transport.encryption, ProfileIssueCode.ENCRYPTION_REQUIRED)
        }
        check("security.peerKeys", p.security.peerKeys.isEmpty() || secure, ProfileIssueCode.SECURE_HANDSHAKE_REQUIRED)
        p.security.peerKeys.forEach { key ->
            check("peerKey.${key.id}", key.uri in p.peerAddressList() && validKey(key.publicKey), ProfileIssueCode.PEER_PIN)
        }
        check(
            "security.peerKeys",
            p.security.peerKeys
                .map { it.uri }
                .distinct()
                .size == p.security.peerKeys.size,
            ProfileIssueCode.DUPLICATE_PEER_PIN,
        )
    }

    private fun dns() {
        if (!p.magicDns &&
            p.dns.mode == DnsMode.CUSTOM
        ) {
            check(
                "dns.servers",
                p.dns.servers
                    .configEntries()
                    .isNotEmpty() &&
                    p.dns.servers.configEntries().all {
                        numericIp(it)?.let { ip ->
                            !ip.isAnyLocalAddress &&
                                !ip.isMulticastAddress &&
                                !ip.isLoopbackAddress &&
                                (ip.address.size == 4 || p.virtualIpv6.isNotBlank())
                        } ==
                            true
                    },
                ProfileIssueCode.DNS_SERVER,
            )
        }
        if (p.magicDns) {
            check(
                "dns.zone",
                p.dns.zone
                    .trimEnd('.')
                    .split('.')
                    .all { it.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")) } &&
                    p.dns.zone.length <= 253,
                ProfileIssueCode.DNS_ZONE,
            )
        }
    }

    private fun transport() {
        val t = p.transport
        check(
            "transport.mtu",
            t.mtu in (if (p.virtualIpv6.isBlank()) 400 else 1280)..1380,
            ProfileIssueCode.MTU,
        )
        check(
            "transport.defaultProtocol",
            t.defaultProtocol in setOf("tcp", "udp", "wg", "ws", "wss", "quic"),
            ProfileIssueCode.TRANSPORT_PROTOCOL,
        )
        check(
            "transport.encryptionAlgorithm",
            t.encryptionAlgorithm in setOf("aes-gcm", "aes-256-gcm", "chacha20"),
            ProfileIssueCode.ENCRYPTION_ALGORITHM,
        )
        check("transport.threadCount", !t.multiThread || t.threadCount in 2..64, ProfileIssueCode.THREAD_COUNT)
        check(
            "transport.receiveBytesPerSecond",
            t.receiveBytesPerSecond == null || t.receiveBytesPerSecond > 0,
            ProfileIssueCode.RECEIVE_LIMIT,
        )
        check(
            "transport.foreignRelayBytesPerSecond",
            t.foreignRelayBytesPerSecond == null || t.foreignRelayBytesPerSecond > 0,
            ProfileIssueCode.RELAY_LIMIT,
        )
        check("transport.lazyP2p", t.p2pMode == P2pMode.AUTOMATIC || !t.lazyP2p, ProfileIssueCode.LAZY_P2P)
        listOf("transport.stunServers" to t.stunServers, "transport.stunServersV6" to t.stunServersV6).forEach { (field, raw) ->
            check(
                field,
                raw.configEntries().all { entry ->
                    if (entry.startsWith("txt:")) {
                        entry.removePrefix("txt:").matches(Regex("[A-Za-z0-9.-]+"))
                    } else {
                        runCatching {
                            val uri = java.net.URI("udp://$entry")
                            !uri.host.isNullOrBlank() &&
                                uri.port in -1..65535 &&
                                uri.port != 0 &&
                                uri.path.isNullOrEmpty() &&
                                uri.query == null &&
                                uri.fragment == null &&
                                uri.userInfo == null
                        }.getOrDefault(false)
                    }
                },
                ProfileIssueCode.STUN_SERVER,
            )
        }
    }

    private fun proxy() {
        if (p.localProxy.socks5) {
            check("localProxy.bindAddress", numericIp(p.localProxy.bindAddress) != null, ProfileIssueCode.BIND_ADDRESS)
            check("localProxy.port", p.localProxy.port in 1..65535, ProfileIssueCode.BIND_PORT)
        }
        p.localProxy.forwards.forEach { f ->
            check(
                "forward.${f.id}",
                f.protocol in setOf("tcp", "udp") &&
                    numericIp(f.bindAddress) != null &&
                    numericIp(f.destination)?.address?.size == 4 &&
                    f.bindPort in 1..65535 &&
                    f.destinationPort in 1..65535,
                ProfileIssueCode.PORT_FORWARD,
            )
        }
        val bindings =
            buildList {
                p.listeners.configEntries().forEach { endpoint ->
                    val uri = runCatching { java.net.URI(endpoint) }.getOrNull() ?: return@forEach
                    val protocol =
                        when (uri.scheme) {
                            "tcp", "ws", "wss" -> "tcp"
                            "udp", "wg", "quic" -> "udp"
                            else -> return@forEach
                        }
                    val host =
                        numericIp(
                            uri.host
                                .orEmpty()
                                .removePrefix("[")
                                .removeSuffix("]"),
                        ) ?: return@forEach
                    if (uri.port > 0) add(LocalBinding("listeners", protocol, host, uri.port))
                }
                if (p.localProxy.socks5) {
                    numericIp(
                        p.localProxy.bindAddress,
                    )?.let { add(LocalBinding("localProxy.port", "tcp", it, p.localProxy.port)) }
                }
                p.localProxy.forwards.forEach { f ->
                    numericIp(f.bindAddress)?.let { add(LocalBinding("forward.${f.id}", f.protocol, it, f.bindPort)) }
                }
            }
        bindings.forEachIndexed { index, binding ->
            check(
                binding.field,
                bindings.take(index).none { existing ->
                    existing.protocol == binding.protocol &&
                        existing.port == binding.port &&
                        existing.address.address.size == binding.address.address.size &&
                        (existing.address == binding.address || existing.address.isAnyLocalAddress || binding.address.isAnyLocalAddress)
                },
                ProfileIssueCode.BIND_CONFLICT,
            )
        }
    }

    private fun acl() {
        if (p.acl.enabled) {
            check(
                "acl.chains",
                p.acl.chains
                    .map { it.type }
                    .distinct()
                    .size == p.acl.chains.size,
                ProfileIssueCode.DUPLICATE_CHAIN,
            )
            p.acl.declares.forEach {
                check(
                    "group.${it.id}",
                    it.name.isNotBlank() &&
                        it.secret.isNotBlank() &&
                        it.name.none(
                            Char::isISOControl,
                        ) &&
                        it.secret.none(Char::isISOControl),
                    ProfileIssueCode.GROUP,
                )
            }
            check(
                "acl.declares",
                p.acl.declares
                    .map { it.name }
                    .distinct()
                    .size == p.acl.declares.size,
                ProfileIssueCode.DUPLICATE_GROUP,
            )
            p.acl.chains.forEach { chain ->
                check("chain.${chain.id}", chain.name.isNotBlank(), ProfileIssueCode.CHAIN_NAME)
                chain.rules.forEach { rule ->
                    val field = "rule.${rule.id}"
                    check(field, rule.name.isNotBlank(), ProfileIssueCode.RULE_NAME)
                    check("$field.priority", rule.priority in 0..65535, ProfileIssueCode.PRIORITY)
                    check("$field.priority", chain.rules.count { it.priority == rule.priority } == 1, ProfileIssueCode.DUPLICATE_PRIORITY)
                    check("$field.rateLimit", rule.rateLimit in 0..4294967295L, ProfileIssueCode.RATE_LIMIT)
                    check(
                        "$field.burstLimit",
                        rule.burstLimit in 0..4294967295L && (rule.rateLimit == 0L || rule.burstLimit > 0),
                        ProfileIssueCode.BURST_LIMIT,
                    )
                    listOf("sourceIps" to rule.sourceIps, "destinationIps" to rule.destinationIps).forEach { (suffix, raw) ->
                        check(
                            "$field.$suffix",
                            raw.configEntries().all { normalizedAclAddress(it) != null },
                            ProfileIssueCode.ACL_ADDRESS,
                        )
                    }
                    ports("$field.sourcePorts", rule.sourcePorts)
                    ports("$field.destinationPorts", rule.destinationPorts)
                    check(
                        "$field.protocol",
                        rule.protocol in setOf(AclProtocol.TCP, AclProtocol.UDP, AclProtocol.ANY) ||
                            (rule.sourcePorts.isBlank() && rule.destinationPorts.isBlank()),
                        ProfileIssueCode.ICMP_PORTS,
                    )
                }
            }
        }
    }
}

private data class LocalBinding(
    val field: String,
    val protocol: String,
    val address: java.net.InetAddress,
    val port: Int,
)
