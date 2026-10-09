package com.github.zzwtsy.easytierkt.data.connection

import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.DnsMode
import com.github.zzwtsy.easytierkt.data.profile.DnsOptions
import com.github.zzwtsy.easytierkt.data.profile.RouteMode
import com.github.zzwtsy.easytierkt.data.profile.RoutingOptions
import com.github.zzwtsy.easytierkt.data.profile.normalizedCidr
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnPlanBuilderTest {
    private val info = EasyTierNetworkInfo("10.1.0.2", 32, 1, listOf("192.168.1.0/24", "0.0.0.0/0"), "fd00::2/64", listOf("10.1.0.3/32"))
    private val profile = ConnectionProfile(networkName = "test", virtualIpv6 = "fd00::2")

    /** 自动模式保留学习网段、节点主机路由与 IPv6 子网，过滤远端默认路由并允许公网 IPv6 绕过。 */
    @Test
    fun automaticRoutesKeepNodesAndFilterDefault() {
        val plan = VpnPlanBuilder.build(profile, info)
        assertTrue("192.168.1.0/24" in plan.routes)
        assertTrue("10.1.0.3/32" in plan.routes)
        assertTrue(normalizedCidr("fd00::/64") in plan.routes)
        assertFalse("0.0.0.0/0" in plan.routes)
        assertTrue(plan.allowIpv6Bypass)
    }

    /** 手动业务路由替换学习网段，但不能移除连接所需的节点主机路由。 */
    @Test
    fun manualRoutesReplaceOnlyBusinessRoutes() {
        val plan = VpnPlanBuilder.build(profile.copy(routes = "172.16.1.4/16", routing = RoutingOptions(mode = RouteMode.MANUAL)), info)
        assertTrue("172.16.0.0/16" in plan.routes)
        assertFalse("192.168.1.0/24" in plan.routes)
        assertTrue("10.1.0.3/32" in plan.routes)
    }

    /** IPv4 出口默认接管 IPv4 公网并阻断 IPv6 绕过；显式允许绕过后移除 IPv6 默认路由。 */
    @Test
    fun exitBlocksIpv6UnlessExplicitlyBypassed() {
        val exit = profile.copy(routing = RoutingOptions(exitNodes = "10.1.0.3"))
        val blocked = VpnPlanBuilder.build(exit, info)
        assertTrue("0.0.0.0/0" in blocked.routes)
        assertTrue(normalizedCidr("::/0") in blocked.routes)
        assertFalse(blocked.allowIpv6Bypass)
        val bypassed = VpnPlanBuilder.build(exit.copy(routing = exit.routing.copy(ipv6InternetBypass = true)), info)
        assertFalse(normalizedCidr("::/0") in bypassed.routes)
        assertTrue(bypassed.allowIpv6Bypass)
    }

    /** 自定义 DNS 不在任何可达网段内时拒绝计划，不能通过补一条主机路由伪装可用。 */
    @Test(expected = UnreachableDnsException::class)
    fun unreachableDnsIsRejected() {
        VpnPlanBuilder.build(profile.copy(dns = DnsOptions(mode = DnsMode.CUSTOM, servers = "203.0.113.53")), info)
    }

    /** 自定义 DNS 在发布网段内时加入主机路由；开启出口后公网 IPv4 DNS 也可进入 TUN。 */
    @Test
    fun reachableDnsAddsHostRoute() {
        val custom = profile.copy(dns = DnsOptions(mode = DnsMode.CUSTOM, servers = "192.168.1.53"))
        assertTrue("192.168.1.53/32" in VpnPlanBuilder.build(custom, info).routes)
        val exit = custom.copy(dns = custom.dns.copy(servers = "203.0.113.53"), routing = RoutingOptions(exitNodes = "10.1.0.3"))
        assertTrue("203.0.113.53/32" in VpnPlanBuilder.build(exit, info).routes)
    }

    /** 阻断公网 IPv6 的默认路由用于防绕过，不能作为公网 IPv6 DNS 可用的依据。 */
    @Test(expected = UnreachableDnsException::class)
    fun ipv6SinkDoesNotMakePublicDnsReachable() {
        VpnPlanBuilder.build(
            profile.copy(
                routing = RoutingOptions(exitNodes = "10.1.0.3"),
                dns = DnsOptions(mode = DnsMode.CUSTOM, servers = "2001:db8:1234::53"),
            ),
            info,
        )
    }
}
