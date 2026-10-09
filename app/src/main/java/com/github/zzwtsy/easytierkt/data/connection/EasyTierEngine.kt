package com.github.zzwtsy.easytierkt.data.connection

import com.easytier.jni.EasyTierJNI
import com.github.zzwtsy.easytierkt.data.profile.IpAddresses
import com.github.zzwtsy.easytierkt.data.profile.normalizedCidr
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal data class EasyTierNetworkInfo(
    val virtualIpv4: String,
    val networkLength: Int,
    val peerCount: Int,
    val proxyRoutes: List<String>,
    val virtualIpv6: String? = null,
    val nodeRoutes: List<String> = emptyList(),
)

internal sealed interface NetworkInfoResult {
    data class Ready(
        val info: EasyTierNetworkInfo,
    ) : NetworkInfoResult

    data object NotReady : NetworkInfoResult

    data object Error : NetworkInfoResult
}

/** 隔离内核接口，供会话控制器注入可控替身。 */
internal interface VpnKernel {
    fun start(config: String)

    fun setTunFd(
        instanceName: String,
        fd: Int,
    )

    fun stop()

    fun readInfo(instanceName: String): NetworkInfoResult
}

/** 只接收当前 JNI 的数值地址协议，拒绝以默认前缀掩盖格式错误。 */
internal object EasyTierEngine : VpnKernel {
    override fun start(config: String) {
        check(EasyTierJNI.parseConfig(config) == 0) { "EasyTier rejected the configuration" }
        check(EasyTierJNI.runNetworkInstance(config) == 0) { "EasyTier failed to start" }
    }

    override fun setTunFd(
        instanceName: String,
        fd: Int,
    ) {
        check(EasyTierJNI.setTunFd(instanceName, fd) == 0) { "EasyTier failed to attach the VPN interface" }
    }

    override fun stop() {
        check(EasyTierJNI.stopAllInstances() == 0) { "EasyTier failed to stop" }
    }

    override fun readInfo(instanceName: String): NetworkInfoResult =
        EasyTierJNI.collectNetworkInfos()?.let { NativeNetworkInfoDecoder.decode(it, instanceName) } ?: NetworkInfoResult.NotReady

    /** 供原生契约测试读取已就绪数据；明确的内核失败不能当作尚未分配地址。 */
    fun networkInfo(instanceName: String): EasyTierNetworkInfo? =
        when (val result = readInfo(instanceName)) {
            is NetworkInfoResult.Ready -> result.info
            NetworkInfoResult.NotReady -> null
            NetworkInfoResult.Error -> error("EasyTier instance reported an error")
        }
}

internal object NativeNetworkInfoDecoder {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(
        payload: String,
        instanceName: String,
    ): NetworkInfoResult {
        val instance = json.decodeFromString<NativeInfoMap>(payload).map[instanceName] ?: return NetworkInfoResult.NotReady
        if (!instance.errorMessage.isNullOrBlank() || !instance.running) return NetworkInfoResult.Error
        val node = instance.node ?: return NetworkInfoResult.NotReady
        val ipv4 = node.ipv4 ?: return NetworkInfoResult.NotReady
        val address = ipv4.text()
        if (address == "0.0.0.0") return NetworkInfoResult.NotReady
        val proxies = instance.routes.flatMap { it.proxyCidrs }.map { requireNotNull(normalizedCidr(it)) { "Invalid native route" } }
        val nodes =
            instance.routes.flatMap { route ->
                buildList {
                    route.ipv4
                        ?.text()
                        ?.takeUnless { it == "0.0.0.0" }
                        ?.let { add("$it/32") }
                    route.ipv6?.text()?.let { add("${it.substringBefore('/')}/128") }
                }
            }
        return NetworkInfoResult.Ready(
            EasyTierNetworkInfo(
                virtualIpv4 = address,
                networkLength = ipv4.prefix,
                peerCount = instance.peers.count { it.connections.isNotEmpty() },
                proxyRoutes = proxies.distinct().sorted(),
                virtualIpv6 = node.ipv6?.text(),
                nodeRoutes = nodes.distinct().sorted(),
            ),
        )
    }
}

@Serializable
private data class NativeInfoMap(
    val map: Map<String, NativeInstance>,
)

@Serializable
private data class NativeInstance(
    val running: Boolean,
    @SerialName("error_msg") val errorMessage: String? = null,
    @SerialName("my_node_info") val node: NativeNode? = null,
    val routes: List<NativeRoute>,
    val peers: List<NativePeer>,
)

@Serializable
private data class NativeNode(
    @SerialName("virtual_ipv4") val ipv4: NativeIpv4Inet? = null,
    @SerialName("virtual_ipv6") val ipv6: NativeIpv6Inet? = null,
)

@Serializable
private data class NativeRoute(
    @SerialName("ipv4_addr") val ipv4: NativeIpv4Inet? = null,
    @SerialName("ipv6_addr") val ipv6: NativeIpv6Inet? = null,
    @SerialName("proxy_cidrs") val proxyCidrs: List<String>,
)

@Serializable
private data class NativePeer(
    @SerialName("conns") val connections: List<JsonObject>,
)

@Serializable
private data class NativeIpv4Address(
    val addr: Long,
)

@Serializable
private data class NativeIpv4Inet(
    val address: NativeIpv4Address,
    @SerialName("network_length") val prefix: Int,
) {
    fun text(): String {
        require(prefix in 1..32 && address.addr in 0..0xffff_ffffL) { "Invalid native IPv4" }
        return listOf(24, 16, 8, 0).joinToString(".") { ((address.addr ushr it) and 255).toString() }
    }
}

@Serializable
private data class NativeIpv6Address(
    val part1: Long,
    val part2: Long,
    val part3: Long,
    val part4: Long,
)

@Serializable
private data class NativeIpv6Inet(
    val address: NativeIpv6Address,
    @SerialName("network_length") val prefix: Int,
) {
    fun text(): String? {
        require(prefix in 1..128) { "Invalid native IPv6 prefix" }
        val parts = listOf(address.part1, address.part2, address.part3, address.part4)
        require(parts.all { it in 0..0xffff_ffffL }) { "Invalid native IPv6" }
        if (parts.all { it == 0L }) return null
        val raw = parts.flatMap { listOf((it ushr 16).toString(16), (it and 65535).toString(16)) }.joinToString(":")
        return "${requireNotNull(IpAddresses.parse(raw)).toCompressedString()}/$prefix"
    }
}
