package com.github.zzwtsy.easytierkt.data.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpAddressesTest {
    /** 数字地址边界拒绝 DNS、前导零、zone ID、映射地址及库的扩展范围语法，不调用主机名解析。 */
    @Test
    fun rejectsNonCanonicalOrExtendedAddressSyntax() {
        listOf(
            "localhost",
            "example.com",
            "010.0.0.1",
            "10.1",
            "10.*.*.*",
            "fe80::1%wlan0",
            "::ffff:192.0.2.1",
            "::ffff:c000:201",
            "10.0.0.256",
        ).forEach { assertNull(it, IpAddresses.parse(it)) }
    }

    /** 网段清除主机位且 IPv6 压缩小写；接口地址解析保留原有主机位。 */
    @Test
    fun distinguishesNetworkAndInterfaceAddresses() {
        assertEquals("10.0.0.0/24", normalizedCidr("10.0.0.7/24"))
        assertEquals("fd00::/64", normalizedCidr("FD00:0:0:0:0:0:0:7/64"))
        assertEquals("fd00::7", IpAddresses.parse("FD00:0:0:0:0:0:0:7")?.toCompressedString())
        assertEquals("10.0.0.7/32", normalizedCidr("10.0.0.7/32"))
        assertEquals("fd00::7/128", normalizedCidr("fd00::7/128"))
        assertEquals("::/0", normalizedCidr("fd00::7/0"))
    }

    /** 包含判断处理双栈前缀边界及不同地址族，DNS 可达性不会因字符串前缀相似而误判。 */
    @Test
    fun containsUsesNumericPrefixBoundaries() {
        assertTrue(IpAddresses.contains("10.0.0.7/24", "10.0.0.255"))
        assertFalse(IpAddresses.contains("10.0.0.7/24", "10.0.1.0"))
        assertTrue(IpAddresses.contains("fd00::/64", "fd00::ffff"))
        assertFalse(IpAddresses.contains("fd00::/64", "fd00:0:0:1::1"))
        assertFalse(IpAddresses.contains("::/0", "10.0.0.1"))
        assertNull(normalizedCidr("10.0.0.1/33"))
        assertNull(normalizedCidr("fd00::1/129"))
    }

    /** ACL 协议编号与上游枚举显式对应，调整 Kotlin 枚举顺序不会改变 TOML 语义。 */
    @Test
    fun aclEnumsUseExplicitProtocolValues() {
        assertEquals(listOf(1, 2, 3), AclChainType.entries.map { it.protocolValue() })
        assertEquals(listOf(1, 2, 3, 4, 5), AclProtocol.entries.map { it.protocolValue() })
        assertEquals(listOf(1, 2), AclAction.entries.map { it.protocolValue() })
    }
}
