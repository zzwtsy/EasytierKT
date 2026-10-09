package com.github.zzwtsy.easytierkt.data.connection

import com.github.zzwtsy.easytierkt.data.profile.ApplicationMode
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.DnsMode
import com.github.zzwtsy.easytierkt.data.profile.IpAddresses
import com.github.zzwtsy.easytierkt.data.profile.RouteMode
import com.github.zzwtsy.easytierkt.data.profile.configEntries
import com.github.zzwtsy.easytierkt.data.profile.normalizedCidr

internal data class VpnPlan(
    val addresses: List<String>,
    val routes: List<String>,
    val dnsServers: List<String>,
    val mtu: Int,
    val applicationMode: ApplicationMode,
    val applications: Set<String>,
    val allowIpv6Bypass: Boolean,
)

/** 必要节点路由独立于业务网段；出口模式的公网 IPv6 默认进入 TUN 丢弃，避免绕过 IPv4 出口。 */
internal object VpnPlanBuilder {
    fun build(
        profile: ConnectionProfile,
        info: EasyTierNetworkInfo,
    ): VpnPlan {
        val addresses = listOf("${info.virtualIpv4}/${info.networkLength}") + listOfNotNull(info.virtualIpv6)
        val exit = profile.routing.exitNodes.isNotBlank()
        val routes =
            buildList {
                addAll(addresses.mapNotNull(::normalizedCidr))
                addAll(info.nodeRoutes)
                if (profile.routing.mode ==
                    RouteMode.AUTOMATIC
                ) {
                    addAll(info.proxyRoutes.filter { !it.endsWith("/0") && (':' !in it || info.virtualIpv6 != null) })
                }
                addAll(profile.routeCidrs())
                if (info.virtualIpv6 != null) addAll(profile.routing.ipv6Routes.configEntries())
                if (exit) add("0.0.0.0/0")
                if (exit && !profile.routing.ipv6InternetBypass) add("::/0")
                if (profile.magicDns) add("100.100.100.101/32")
            }.mapNotNull(::normalizedCidr).distinct().sorted()
        val dns =
            when {
                profile.magicDns -> listOf("100.100.100.101")
                profile.dns.mode == DnsMode.CUSTOM -> profile.dns.servers.configEntries()
                else -> emptyList()
            }
        if (profile.dns.mode == DnsMode.CUSTOM && !profile.magicDns) {
            if (dns.any { server ->
                    routes.none { !(it.substringAfter('/') == "0" && ':' in it) && IpAddresses.contains(it, server) }
                }
            ) {
                throw UnreachableDnsException()
            }
        }
        // 显式 DNS 主机路由让私有 DNS 进入虚拟网络；服务端需有对应代理网段或出口。
        val dnsRoutes = dns.map { "$it/${if (':' in it) 128 else 32}" }
        return VpnPlan(
            addresses,
            (routes + dnsRoutes).mapNotNull(::normalizedCidr).distinct().sorted(),
            dns,
            profile.transport.mtu,
            profile.routing.applicationMode,
            profile.routing.applications,
            !exit || profile.routing.ipv6InternetBypass,
        )
    }
}

internal class UnreachableDnsException : IllegalArgumentException("Custom DNS has no VPN route")
