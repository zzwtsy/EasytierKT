package com.github.zzwtsy.easytierkt.data.profile

import dev.eav.tomlkt.Toml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 领域配置映射到固定 EasyTier 协议；语法、转义和表作用域交给 TOML 库。 */
internal fun encodeProfile(
    p: ConnectionProfile,
    instanceName: String,
): String {
    val t = p.transport
    val dto =
        EasyTierConfigDto(
            instanceName = instanceName,
            hostname = p.hostname.trim().takeIf(String::isNotBlank),
            dhcp = p.useDhcp,
            ipv4 = if (p.useDhcp) null else "${p.ipv4Address.trim()}/${p.ipv4Prefix}",
            ipv6 =
                p.virtualIpv6
                    .trim()
                    .takeIf(String::isNotBlank)
                    ?.let { "$it/${p.ipv6Prefix}" },
            listeners = p.listeners.configEntries(),
            mappedListeners = p.mappedListeners.configEntries(),
            routes = if (p.routing.mode == RouteMode.MANUAL) p.routeCidrs() else null,
            exitNodes = p.routing.exitNodes.configEntries(),
            stunServers = t.stunServers.configEntries().takeIf(List<String>::isNotEmpty),
            stunServersV6 = t.stunServersV6.configEntries().takeIf(List<String>::isNotEmpty),
            socks5Proxy = if (p.localProxy.socks5) "socks5://${socketHost(p.localProxy.bindAddress)}:${p.localProxy.port}" else null,
            networkIdentity = NetworkIdentityDto(p.networkName.trim(), p.networkSecret.takeUnless { p.credentialMode }),
            secureMode =
                if (p.security.secureMode ||
                    p.credentialMode
                ) {
                    SecureModeDto(true, p.security.privateKey.takeIf(String::isNotBlank), p.security.publicKey.takeIf(String::isNotBlank))
                } else {
                    null
                },
            flags =
                FlagsDto(
                    defaultProtocol = t.defaultProtocol,
                    enableIpv6 = t.ipv6Transport,
                    mtu = t.mtu,
                    disableP2P = t.p2pMode == P2pMode.DISABLED,
                    p2pOnly = t.p2pMode == P2pMode.DIRECT_ONLY,
                    lazyP2P = t.lazyP2p,
                    needP2P = t.needP2p,
                    latencyFirst = t.latencyFirst,
                    disableTcpHolePunching = !t.tcpHolePunching,
                    disableUdpHolePunching = !t.udpHolePunching,
                    disableSymHolePunching = !t.symmetricHolePunching,
                    disableUpnp = !t.upnp,
                    bindDevice = t.bindDevice,
                    enableEncryption = t.encryption,
                    encryptionAlgorithm = t.encryptionAlgorithm,
                    dataCompressAlgo = if (t.compression) 2 else 1,
                    enableKcpProxy = t.kcpProxy,
                    disableKcpInput = !t.kcpInput,
                    enableQuicProxy = t.quicProxy,
                    disableQuicInput = !t.quicInput,
                    disableRelayData = !t.relayData,
                    relayAllPeerRpc = t.relayPeerRpc,
                    disableRelayKcp = !t.relayKcp,
                    disableRelayQuic = !t.relayQuic,
                    enableRelayForeignNetworkKcp = t.foreignKcp,
                    enableRelayForeignNetworkQuic = t.foreignQuic,
                    relayNetworkWhitelist = t.relayWhitelist.trim(),
                    privateMode = t.privateMode,
                    enableUdpBroadcastRelay = t.udpBroadcast,
                    multiThread = t.multiThread,
                    multiThreadCount = t.threadCount,
                    acceptDns = p.magicDns,
                    instanceRecvBpsLimit = t.receiveBytesPerSecond,
                    foreignRelayBpsLimit = t.foreignRelayBytesPerSecond,
                    tldDnsZone = if (p.magicDns) p.dns.zone.trimEnd('.') + "." else null,
                ),
            peer =
                p.peerAddressList().map {
                    PeerDto(
                        it,
                        p.security.peerKeys
                            .find { key -> key.uri == it }
                            ?.publicKey,
                    )
                },
            portForward =
                p.localProxy.forwards.map {
                    PortForwardDto(
                        "${socketHost(it.bindAddress)}:${it.bindPort}",
                        "${socketHost(it.destination)}:${it.destinationPort}",
                        it.protocol,
                    )
                },
            acl =
                if (p.acl.enabled) {
                    AclDto(
                        AclV1Dto(
                            GroupDto(p.acl.members.configEntries(), p.acl.declares.map { GroupDeclarationDto(it.name, it.secret) }),
                            p.acl.chains.map { chain ->
                                ChainDto(
                                    chain.name,
                                    chain.type.protocolValue(),
                                    chain.enabled,
                                    chain.defaultAction.protocolValue(),
                                    chain.rules.sortedByDescending { it.priority }.map { rule ->
                                        RuleDto(
                                            rule.name,
                                            rule.description,
                                            rule.priority,
                                            rule.enabled,
                                            rule.protocol.protocolValue(),
                                            rule.action.protocolValue(),
                                            rule.sourceIps.configEntries().map { requireNotNull(normalizedAclAddress(it)) },
                                            rule.destinationIps.configEntries().map { requireNotNull(normalizedAclAddress(it)) },
                                            rule.sourcePorts.configEntries(),
                                            rule.destinationPorts.configEntries(),
                                            rule.sourceGroups.configEntries(),
                                            rule.destinationGroups.configEntries(),
                                            rule.stateful,
                                            rule.rateLimit,
                                            rule.burstLimit,
                                        )
                                    },
                                )
                            },
                        ),
                    )
                } else {
                    null
                },
        )
    return Toml { explicitNulls = false }.encodeToString(EasyTierConfigDto.serializer(), dto)
}

internal fun AclChainType.protocolValue(): Int =
    when (this) {
        AclChainType.INBOUND -> 1
        AclChainType.OUTBOUND -> 2
        AclChainType.FORWARD -> 3
    }

internal fun AclProtocol.protocolValue(): Int =
    when (this) {
        AclProtocol.TCP -> 1
        AclProtocol.UDP -> 2
        AclProtocol.ICMP -> 3
        AclProtocol.ICMPV6 -> 4
        AclProtocol.ANY -> 5
    }

internal fun AclAction.protocolValue(): Int =
    when (this) {
        AclAction.ALLOW -> 1
        AclAction.DROP -> 2
    }

@Serializable
internal data class EasyTierConfigDto(
    @SerialName("instance_name") val instanceName: String,
    val hostname: String?,
    val dhcp: Boolean,
    val ipv4: String?,
    val ipv6: String?,
    val listeners: List<String>,
    @SerialName("mapped_listeners") val mappedListeners: List<String>,
    val routes: List<String>?,
    @SerialName("exit_nodes") val exitNodes: List<String>,
    @SerialName("stun_servers") val stunServers: List<String>?,
    @SerialName("stun_servers_v6") val stunServersV6: List<String>?,
    @SerialName("socks5_proxy") val socks5Proxy: String?,
    @SerialName("network_identity") val networkIdentity: NetworkIdentityDto,
    @SerialName("secure_mode") val secureMode: SecureModeDto?,
    val flags: FlagsDto,
    val peer: List<PeerDto>,
    @SerialName("port_forward") val portForward: List<PortForwardDto>,
    val acl: AclDto?,
)

@Serializable
internal data class FlagsDto(
    @SerialName("default_protocol") val defaultProtocol: String,
    @SerialName("enable_ipv6") val enableIpv6: Boolean,
    val mtu: Int,
    @SerialName("disable_p2p") val disableP2P: Boolean,
    @SerialName("p2p_only") val p2pOnly: Boolean,
    @SerialName("lazy_p2p") val lazyP2P: Boolean,
    @SerialName("need_p2p") val needP2P: Boolean,
    @SerialName("latency_first") val latencyFirst: Boolean,
    @SerialName("disable_tcp_hole_punching") val disableTcpHolePunching: Boolean,
    @SerialName("disable_udp_hole_punching") val disableUdpHolePunching: Boolean,
    @SerialName("disable_sym_hole_punching") val disableSymHolePunching: Boolean,
    @SerialName("disable_upnp") val disableUpnp: Boolean,
    @SerialName("bind_device") val bindDevice: Boolean,
    @SerialName("enable_encryption") val enableEncryption: Boolean,
    @SerialName("encryption_algorithm") val encryptionAlgorithm: String,
    @SerialName("data_compress_algo") val dataCompressAlgo: Int,
    @SerialName("enable_kcp_proxy") val enableKcpProxy: Boolean,
    @SerialName("disable_kcp_input") val disableKcpInput: Boolean,
    @SerialName("enable_quic_proxy") val enableQuicProxy: Boolean,
    @SerialName("disable_quic_input") val disableQuicInput: Boolean,
    @SerialName("disable_relay_data") val disableRelayData: Boolean,
    @SerialName("relay_all_peer_rpc") val relayAllPeerRpc: Boolean,
    @SerialName("disable_relay_kcp") val disableRelayKcp: Boolean,
    @SerialName("disable_relay_quic") val disableRelayQuic: Boolean,
    @SerialName("enable_relay_foreign_network_kcp") val enableRelayForeignNetworkKcp: Boolean,
    @SerialName("enable_relay_foreign_network_quic") val enableRelayForeignNetworkQuic: Boolean,
    @SerialName("relay_network_whitelist") val relayNetworkWhitelist: String,
    @SerialName("private_mode") val privateMode: Boolean,
    @SerialName("enable_udp_broadcast_relay") val enableUdpBroadcastRelay: Boolean,
    @SerialName("multi_thread") val multiThread: Boolean,
    @SerialName("multi_thread_count") val multiThreadCount: Int,
    @SerialName("accept_dns") val acceptDns: Boolean,
    @SerialName("instance_recv_bps_limit") val instanceRecvBpsLimit: Long?,
    @SerialName("foreign_relay_bps_limit") val foreignRelayBpsLimit: Long?,
    @SerialName("tld_dns_zone") val tldDnsZone: String?,
)

@Serializable
internal data class NetworkIdentityDto(
    @SerialName("network_name") val networkName: String,
    @SerialName("network_secret") val networkSecret: String?,
)

@Serializable
internal data class SecureModeDto(
    val enabled: Boolean,
    @SerialName("local_private_key") val localPrivateKey: String?,
    @SerialName("local_public_key") val localPublicKey: String?,
)

@Serializable
internal data class PeerDto(
    val uri: String,
    @SerialName("peer_public_key") val peerPublicKey: String?,
)

@Serializable
internal data class PortForwardDto(
    @SerialName("bind_addr") val bindAddr: String,
    @SerialName("dst_addr") val dstAddr: String,
    val proto: String,
)

@Serializable
internal data class AclDto(
    @SerialName("acl_v1") val aclV1: AclV1Dto,
)

@Serializable
internal data class AclV1Dto(
    val group: GroupDto,
    val chains: List<ChainDto>,
)

@Serializable
internal data class GroupDto(
    val members: List<String>,
    val declares: List<GroupDeclarationDto>,
)

@Serializable
internal data class GroupDeclarationDto(
    @SerialName("group_name") val groupName: String,
    @SerialName("group_secret") val groupSecret: String,
)

@Serializable
internal data class ChainDto(
    val name: String,
    @SerialName("chain_type") val chainType: Int,
    val enabled: Boolean,
    @SerialName("default_action") val defaultAction: Int,
    val rules: List<RuleDto>,
)

@Serializable
internal data class RuleDto(
    val name: String,
    val description: String,
    val priority: Int,
    val enabled: Boolean,
    val protocol: Int,
    val action: Int,
    @SerialName("source_ips") val sourceIps: List<String>,
    @SerialName("destination_ips") val destinationIps: List<String>,
    @SerialName("source_ports") val sourcePorts: List<String>,
    val ports: List<String>,
    @SerialName("source_groups") val sourceGroups: List<String>,
    @SerialName("destination_groups") val destinationGroups: List<String>,
    val stateful: Boolean,
    @SerialName("rate_limit") val rateLimit: Long,
    @SerialName("burst_limit") val burstLimit: Long,
)

private fun socketHost(value: String): String = if (':' in value) "[$value]" else value
