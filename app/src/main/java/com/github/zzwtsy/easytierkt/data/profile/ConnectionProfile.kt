package com.github.zzwtsy.easytierkt.data.profile

import kotlinx.serialization.Serializable

/** 配置草稿允许字段暂时不完整；Repository 仅持久化通过完整校验的配置。 */
@Serializable
data class ConnectionProfile(
    val networkName: String = "",
    val networkSecret: String = "",
    val peerAddresses: String = "",
    val useDhcp: Boolean = true,
    val ipv4Address: String = "",
    val routes: String = "",
    val hostname: String = "",
    val ipv4Prefix: Int = 24,
    val virtualIpv6: String = "",
    val ipv6Prefix: Int = 64,
    val listeners: String = "tcp://0.0.0.0:11010\nudp://0.0.0.0:11010",
    val mappedListeners: String = "",
    val security: SecurityOptions = SecurityOptions(),
    val routing: RoutingOptions = RoutingOptions(),
    val dns: DnsOptions = DnsOptions(),
    val transport: TransportOptions = TransportOptions(),
    val localProxy: LocalProxyOptions = LocalProxyOptions(),
    val acl: AclOptions = AclOptions(),
) {
    fun issues(): List<ProfileIssue> = validateProfile(this)

    fun validationError(): ProfileValidationError? =
        issues().firstOrNull()?.let {
            when (it.field.path) {
                "networkName" -> ProfileValidationError.INVALID_NETWORK_NAME
                "networkSecret" -> ProfileValidationError.INVALID_NETWORK_SECRET
                "ipv4Address", "ipv4Prefix" -> ProfileValidationError.INVALID_STATIC_ADDRESS
                "peerAddresses" -> ProfileValidationError.INVALID_PEER_ADDRESS
                "routes" -> ProfileValidationError.INVALID_ROUTE
                else -> ProfileValidationError.INVALID_ADVANCED
            }
        }

    /** 只编码通过校验的配置；凭据模式仍需在原生启动边界重建无共享密钥身份。 */
    fun toEasyTierToml(instanceName: String = INSTANCE_NAME): String {
        require(issues().isEmpty()) { "Connection profile is invalid" }
        return encodeProfile(this, instanceName)
    }

    fun routeCidrs(): List<String> = routes.configEntries().mapNotNull(::normalizedCidr).distinct()

    fun peerAddressList(): List<String> = peerAddresses.configEntries()

    val magicDns: Boolean get() = dns.mode == DnsMode.MAGIC
    val credentialMode: Boolean get() = security.mode == AuthenticationMode.CREDENTIAL

    companion object {
        const val INSTANCE_NAME = "easytier-android"
    }
}

enum class ProfileValidationError {
    INVALID_NETWORK_NAME,
    INVALID_NETWORK_SECRET,
    INVALID_STATIC_ADDRESS,
    INVALID_PEER_ADDRESS,
    INVALID_ROUTE,
    INVALID_ADVANCED,
}
