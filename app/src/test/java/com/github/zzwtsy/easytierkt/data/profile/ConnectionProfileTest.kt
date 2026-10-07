package com.github.zzwtsy.easytierkt.data.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionProfileTest {
    /** 验证配置含转义字符、peer、路由和 Magic DNS 时，生成的 TOML 包含对应字段与条目。 */
    @Test
    fun buildsV264TomlWithEscapedSecretPeersRoutesAndMagicDnsFlag() {
        val profile =
            ConnectionProfile(
                networkName = "office",
                networkSecret = "key\"with\\symbols",
                peerAddresses = "udp://peer.example:11010\ntcp://192.0.2.10:11010",
                routes = "192.168.10.0/24\n10.10.0.0/16",
                enableMagicDns = true,
            )

        val config = profile.toEasyTierToml()

        assertTrue(config.contains("instance_name = \"easytier-android\""))
        assertTrue(config.contains("dhcp = true"))
        assertFalse(config.contains("ipv4 ="))
        assertTrue(config.contains("accept_dns = true"))
        assertTrue(config.contains("network_name = \"office\""))
        assertTrue(config.contains("network_secret = \"key\\\"with\\\\symbols\""))
        assertEquals(2, Regex("\\[\\[peer]]").findAll(config).count())
        assertTrue(config.contains("routes = [\"192.168.10.0/24\", \"10.10.0.0/16\"]"))
    }

    /** 验证默认路由被拒绝，改用合法路由后静态 IPv4 地址会写入 TOML。 */
    @Test
    fun staticAddressIsWrittenAndDefaultRouteIsRejected() {
        val profile =
            ConnectionProfile(
                networkName = "office",
                useDhcp = false,
                ipv4Address = "10.20.0.7",
                routes = "0.0.0.0/0",
            )

        assertEquals(ProfileValidationError.INVALID_ROUTE, profile.validationError())

        val validProfile = profile.copy(routes = "192.168.1.0/24")
        assertTrue(validProfile.toEasyTierToml().contains("ipv4 = \"10.20.0.7\""))
    }

    /** 验证 peer 地址使用不支持的协议时，配置校验返回地址错误。 */
    @Test
    fun rejectsPeerAddressesWithoutSupportedSchemesOrHosts() {
        val profile =
            ConnectionProfile(
                networkName = "office",
                peerAddresses = "https://peer.example:11010",
            )

        assertEquals(ProfileValidationError.INVALID_PEER_ADDRESS, profile.validationError())
    }
}
