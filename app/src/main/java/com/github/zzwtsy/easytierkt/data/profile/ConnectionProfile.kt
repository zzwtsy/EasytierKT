package com.github.zzwtsy.easytierkt.data.profile

import kotlinx.serialization.Serializable
import java.net.URI

/** 单个 EasyTier 网络的可编辑配置；peer 地址和路由字段支持逗号或换行分隔。 */
@Serializable
data class ConnectionProfile(
    val networkName: String = "",
    val networkSecret: String = "",
    val peerAddresses: String = "",
    val useDhcp: Boolean = true,
    val ipv4Address: String = "",
    val routes: String = "",
    val enableMagicDns: Boolean = false,
) {
    /**
     * 按顺序校验网络名、密钥、静态 IPv4、peer URI 和 IPv4 路由，并返回首个错误。
     * 网络名非空白且长度不超过 128，密钥可为空且长度不超过 1024（长度按 Kotlin `String.length` 计算）；两者均拒绝控制字符。
     * 关闭 DHCP 时静态地址必须是非 `0.0.0.0` IPv4。peer URI 必须有主机，仅接受 `tcp`、`udp`、`ws`、`wss`、`quic` 协议；
     * 端口可省略或为 1 到 65535，且 URI 不得含用户信息或片段。路由必须是非 `0.0.0.0` IPv4 CIDR，前缀范围为 1 到 32。
     * 所有字段有效时返回 null。
     */
    fun validationError(): ProfileValidationError? {
        if (networkName.isBlank() || networkName.length > MAX_NETWORK_NAME_LENGTH || networkName.any(Char::isISOControl)) {
            return ProfileValidationError.INVALID_NETWORK_NAME
        }

        if (networkSecret.length > MAX_NETWORK_SECRET_LENGTH || networkSecret.any(Char::isISOControl)) {
            return ProfileValidationError.INVALID_NETWORK_SECRET
        }

        if (!useDhcp && !isIpv4Address(ipv4Address.trim())) {
            return ProfileValidationError.INVALID_STATIC_ADDRESS
        }

        if (peerAddresses.entries().any { !isPeerAddress(it) }) {
            return ProfileValidationError.INVALID_PEER_ADDRESS
        }

        if (routes.entries().any { parseRoute(it) == null }) {
            return ProfileValidationError.INVALID_ROUTE
        }

        return null
    }

    /**
     * 生成 EasyTier v2.6.4 使用的 TOML 配置。
     *
     * 调用前必须通过 [validationError] 校验，否则抛出 [IllegalArgumentException]。
     */
    fun toEasyTierToml(instanceName: String = INSTANCE_NAME): String {
        require(validationError() == null) { "Connection profile is invalid" }

        return buildString {
            appendLine("instance_name = ${tomlString(instanceName)}")
            appendLine("dhcp = $useDhcp")
            if (!useDhcp) {
                appendLine("ipv4 = ${tomlString(ipv4Address.trim())}")
            }

            val routeEntries = routes.entries().mapNotNull(::parseRoute).distinct()
            if (routeEntries.isNotEmpty()) {
                appendLine("routes = [${routeEntries.joinToString(", ") { tomlString(it) }}]")
            }

            if (enableMagicDns) {
                appendLine()
                appendLine("[flags]")
                appendLine("accept_dns = true")
            }

            peerAddresses.entries().forEach { peer ->
                appendLine()
                appendLine("[[peer]]")
                appendLine("uri = ${tomlString(peer)}")
            }

            appendLine()
            appendLine("[network_identity]")
            appendLine("network_name = ${tomlString(networkName.trim())}")
            if (networkSecret.isNotEmpty()) {
                appendLine("network_secret = ${tomlString(networkSecret)}")
            }
        }
    }

    /** 返回可解析路由的 CIDR 列表，去重并保留首次出现的顺序。 */
    fun routeCidrs(): List<String> = routes.entries().mapNotNull(::parseRoute).distinct()

    /** 返回去除空白后的非空 peer 条目；地址格式由 [validationError] 校验。 */
    fun peerAddressList(): List<String> = peerAddresses.entries()

    private fun String.entries(): List<String> =
        split(',', '\n', '\r')
            .map(String::trim)
            .filter(String::isNotEmpty)

    private fun isPeerAddress(value: String): Boolean =
        runCatching {
            val uri = URI(value)
            uri.isAbsolute &&
                uri.scheme.lowercase() in PEER_SCHEMES &&
                !uri.host.isNullOrBlank() &&
                uri.port in -1..MAX_PORT &&
                uri.port != 0 &&
                uri.rawUserInfo == null &&
                uri.rawFragment == null
        }.getOrDefault(false)

    private fun tomlString(value: String): String =
        "\"" +
            value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\""

    private fun parseRoute(value: String): String? {
        val parts = value.split('/')
        if (parts.size != 2 || !isIpv4Address(parts[0])) return null
        val prefix = parts[1].toIntOrNull() ?: return null
        if (prefix !in 1..32) return null
        return "${parts[0]}/$prefix"
    }

    private fun isIpv4Address(value: String): Boolean {
        val parts = value.split('.')
        return value != "0.0.0.0" &&
            parts.size == 4 &&
            parts.all { part ->
                part.isNotEmpty() &&
                    part.all(Char::isDigit) &&
                    part.toIntOrNull()?.let { it in 0..255 } == true
            }
    }

    companion object {
        const val INSTANCE_NAME = "easytier-android"

        private const val MAX_NETWORK_NAME_LENGTH = 128
        private const val MAX_NETWORK_SECRET_LENGTH = 1_024
        private const val MAX_PORT = 65_535
        private val PEER_SCHEMES = setOf("tcp", "udp", "ws", "wss", "quic")
    }
}

enum class ProfileValidationError {
    INVALID_NETWORK_NAME,
    INVALID_NETWORK_SECRET,
    INVALID_STATIC_ADDRESS,
    INVALID_PEER_ADDRESS,
    INVALID_ROUTE,
}
