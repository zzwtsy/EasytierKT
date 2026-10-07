package com.github.zzwtsy.easytierkt.data.connection

import com.easytier.jni.EasyTierJNI
import org.json.JSONObject

internal data class EasyTierNetworkInfo(
    val virtualIpv4: String,
    val networkLength: Int,
    val peerCount: Int,
    val proxyRoutes: List<String>,
)

/** 将启动、TUN 接入和停止操作转发给 EasyTier JNI；原生方法返回非零值时对应操作会失败。 */
internal object EasyTierEngine {
    /** 解析并启动配置中的网络实例；任一步骤返回非零值都会抛出 [IllegalStateException]。 */
    fun start(config: String) {
        check(EasyTierJNI.parseConfig(config) == 0) { "EasyTier rejected the configuration" }
        check(EasyTierJNI.runNetworkInstance(config) == 0) { "EasyTier failed to start" }
    }

    /** 将 VPN 文件描述符交给指定实例；原生方法返回非零值时抛出 [IllegalStateException]。 */
    fun setTunFd(
        instanceName: String,
        fd: Int,
    ) {
        check(EasyTierJNI.setTunFd(instanceName, fd) == 0) { "EasyTier failed to attach the VPN interface" }
    }

    /** 停止全部 EasyTier 网络实例；原生方法返回非零值时抛出 [IllegalStateException]。 */
    fun stop() {
        check(EasyTierJNI.stopAllInstances() == 0) { "EasyTier failed to stop" }
    }

    /**
     * 读取 [instanceName] 的运行信息。
     *
     * 缺少运行信息或虚拟 IPv4 为 `0.0.0.0` 时返回 null。代理路由会规范化、去重并排序；
     * peer 数量按至少包含一个连接的 peer 计算。实例报告有效的 `error_msg` 时抛出 [IllegalStateException]。
     */
    fun networkInfo(instanceName: String): EasyTierNetworkInfo? {
        val payload = EasyTierJNI.collectNetworkInfos() ?: return null
        val info =
            JSONObject(payload)
                .optJSONObject("map")
                ?.optJSONObject(instanceName)
                ?: return null
        if (!info.optBoolean("running", true)) return null

        val error = info.optString("error_msg").takeIf { it.isNotBlank() && it != "null" }
        check(error == null) { "EasyTier instance reported an error" }

        val nodeInfo = info.optJSONObject("my_node_info") ?: return null
        val virtualIpv4 = nodeInfo.optJSONObject("virtual_ipv4") ?: return null
        val address = parseIpv4(virtualIpv4.opt("address")) ?: return null
        if (address == "0.0.0.0") return null

        val routes = mutableListOf<String>()
        val routeArray = info.optJSONArray("routes")
        if (routeArray != null) {
            for (index in 0 until routeArray.length()) {
                val proxyCidrs = routeArray.optJSONObject(index)?.optJSONArray("proxy_cidrs") ?: continue
                for (routeIndex in 0 until proxyCidrs.length()) {
                    val route = proxyCidrs.optString(routeIndex).normalizeRoute() ?: continue
                    routes += route
                }
            }
        }

        return EasyTierNetworkInfo(
            virtualIpv4 = address,
            networkLength =
                virtualIpv4
                    .optInt("network_length", DEFAULT_NETWORK_LENGTH)
                    .takeIf { it in 1..32 } ?: DEFAULT_NETWORK_LENGTH,
            peerCount = connectedPeerCount(info),
            proxyRoutes = routes.distinct().sorted(),
        )
    }

    private fun connectedPeerCount(info: JSONObject): Int {
        val peers = info.optJSONArray("peers") ?: return 0
        return (0 until peers.length()).count { index ->
            (peers.optJSONObject(index)?.optJSONArray("conns")?.length() ?: 0) > 0
        }
    }

    private fun parseIpv4(value: Any?): String? =
        when (value) {
            is JSONObject -> parseIpv4(value.opt("address"))
            is Number -> value.toLong().toIpv4()
            is String -> {
                value.toLongOrNull()?.toIpv4() ?: value.takeIf(::isIpv4Address)
            }
            else -> null
        }

    private fun Long.toIpv4(): String? {
        val address = this and IPV4_MASK
        return listOf(24, 16, 8, 0)
            .joinToString(".") { shift -> ((address ushr shift) and 0xff).toString() }
    }

    private fun String.normalizeRoute(): String? {
        val parts = split('/')
        if (parts.size == 1 && isIpv4Address(this)) return "$this/32"
        if (parts.size != 2 || !isIpv4Address(parts[0])) return null
        val prefix = parts[1].toIntOrNull()?.takeIf { it in 1..32 } ?: return null
        return "${parts[0]}/$prefix"
    }

    private fun isIpv4Address(value: String): Boolean {
        val parts = value.split('.')
        return parts.size == 4 &&
            parts.all { part ->
                part.isNotEmpty() && part.all(Char::isDigit) && part.toIntOrNull()?.let { it in 0..255 } == true
            }
    }

    private const val DEFAULT_NETWORK_LENGTH = 24
    private const val IPV4_MASK = 0xffff_ffffL
}
