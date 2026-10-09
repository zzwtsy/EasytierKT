package com.github.zzwtsy.easytierkt.data.profile

import kotlinx.serialization.Serializable
import java.net.URI
import java.util.UUID

@Serializable
enum class AuthenticationMode { SHARED_SECRET, CREDENTIAL }

@Serializable
enum class RouteMode { AUTOMATIC, MANUAL }

@Serializable
enum class DnsMode { SYSTEM, MAGIC, CUSTOM }

@Serializable
enum class ApplicationMode { ALL, INCLUDE, EXCLUDE }

@Serializable
enum class P2pMode { AUTOMATIC, DIRECT_ONLY, DISABLED }

@Serializable
data class SecurityOptions(
    val mode: AuthenticationMode = AuthenticationMode.SHARED_SECRET,
    val secureMode: Boolean = false,
    val privateKey: String = "",
    val publicKey: String = "",
    val peerKeys: List<PeerKey> = emptyList(),
)

@Serializable
data class PeerKey(
    val id: String = UUID.randomUUID().toString(),
    val uri: String = "",
    val publicKey: String = "",
)

@Serializable
data class RoutingOptions(
    val mode: RouteMode = RouteMode.AUTOMATIC,
    val ipv6Routes: String = "",
    val exitNodes: String = "",
    val ipv6InternetBypass: Boolean = false,
    val applicationMode: ApplicationMode = ApplicationMode.ALL,
    val applications: Set<String> = emptySet(),
)

@Serializable
data class DnsOptions(
    val mode: DnsMode = DnsMode.SYSTEM,
    val servers: String = "",
    val zone: String = "et.net.",
)

@Serializable
data class TransportOptions(
    val defaultProtocol: String = "tcp",
    val ipv6Transport: Boolean = true,
    val mtu: Int = 1300,
    val p2pMode: P2pMode = P2pMode.AUTOMATIC,
    val lazyP2p: Boolean = false,
    val needP2p: Boolean = false,
    val latencyFirst: Boolean = false,
    val tcpHolePunching: Boolean = true,
    val udpHolePunching: Boolean = true,
    val symmetricHolePunching: Boolean = true,
    val upnp: Boolean = true,
    val bindDevice: Boolean = true,
    val encryption: Boolean = true,
    val encryptionAlgorithm: String = "aes-gcm",
    val compression: Boolean = false,
    val kcpProxy: Boolean = false,
    val kcpInput: Boolean = true,
    val quicProxy: Boolean = false,
    val quicInput: Boolean = true,
    val relayData: Boolean = true,
    val relayPeerRpc: Boolean = false,
    val relayKcp: Boolean = true,
    val relayQuic: Boolean = true,
    val foreignKcp: Boolean = false,
    val foreignQuic: Boolean = false,
    val relayWhitelist: String = "*",
    val privateMode: Boolean = false,
    val udpBroadcast: Boolean = false,
    val multiThread: Boolean = true,
    val threadCount: Int = 2,
    val receiveBytesPerSecond: Long? = null,
    val foreignRelayBytesPerSecond: Long? = null,
    val stunServers: String = "",
    val stunServersV6: String = "",
)

@Serializable
data class LocalProxyOptions(
    val socks5: Boolean = false,
    val bindAddress: String = "127.0.0.1",
    val port: Int = 1080,
    val forwards: List<PortForward> = emptyList(),
)

@Serializable
data class PortForward(
    val id: String = UUID.randomUUID().toString(),
    val protocol: String = "tcp",
    val bindAddress: String = "127.0.0.1",
    val bindPort: Int = 8080,
    val destination: String = "",
    val destinationPort: Int = 80,
)

@Serializable
enum class AclChainType { INBOUND, OUTBOUND, FORWARD }

@Serializable
enum class AclProtocol { TCP, UDP, ICMP, ICMPV6, ANY }

@Serializable
enum class AclAction { ALLOW, DROP }

@Serializable
data class AclOptions(
    val enabled: Boolean = false,
    val chains: List<AclChain> = emptyList(),
    val declares: List<AclGroup> = emptyList(),
    val members: String = "",
)

@Serializable
data class AclGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val secret: String = "",
)

@Serializable
data class AclChain(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: AclChainType = AclChainType.INBOUND,
    val enabled: Boolean = true,
    val defaultAction: AclAction = AclAction.ALLOW,
    val rules: List<AclRule> = emptyList(),
)

@Serializable
data class AclRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val description: String = "",
    val priority: Int = 100,
    val enabled: Boolean = true,
    val protocol: AclProtocol = AclProtocol.ANY,
    val action: AclAction = AclAction.ALLOW,
    val sourceIps: String = "",
    val destinationIps: String = "",
    val sourcePorts: String = "",
    val destinationPorts: String = "",
    val sourceGroups: String = "",
    val destinationGroups: String = "",
    val stateful: Boolean = false,
    val rateLimit: Long = 0,
    val burstLimit: Long = 0,
)

fun String.configEntries(): List<String> = split(',', '\n', '\r').map(String::trim).filter(String::isNotEmpty)

/** 32 字节的标准 Base64 固定为 44 字符，最后一个编码字符的两位填充必须为零。 */
fun validKey(value: String): Boolean = value.matches(Regex("[A-Za-z0-9+/]{42}[AEIMQUYcgkosw048]="))

fun validEndpoint(
    value: String,
    listener: Boolean = false,
): Boolean =
    runCatching {
        val uri = URI(value)
        val schemes =
            if (listener) {
                setOf(
                    "tcp",
                    "udp",
                    "wg",
                    "ws",
                    "wss",
                    "quic",
                )
            } else {
                setOf("tcp", "udp", "wg", "ws", "wss", "quic", "http", "https", "txt", "srv")
            }
        val host = uri.host ?: uri.rawAuthority?.takeIf { uri.scheme in setOf("txt", "srv") && it.matches(Regex("[A-Za-z0-9_.-]+")) }
        uri.scheme?.lowercase() in schemes &&
            !host.isNullOrBlank() &&
            uri.port in -1..65535 &&
            uri.port != 0 &&
            uri.rawUserInfo == null &&
            uri.rawFragment == null
    }.getOrDefault(false)
