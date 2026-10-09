package com.github.zzwtsy.easytierkt.data.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeNetworkInfoDecoderTest {
    private val payload =
        """
        {"map":{"test":{"running":true,"error_msg":null,
        "my_node_info":{"virtual_ipv4":{"address":{"addr":167772162},"network_length":32},
        "virtual_ipv6":{"address":{"part1":4244635648,"part2":0,"part3":0,"part4":2},"network_length":64}},
        "routes":[{"ipv4_addr":{"address":{"addr":167772163},"network_length":32},
        "ipv6_addr":null,"proxy_cidrs":["192.168.1.7/24","fd01::3/64"]}],
        "peers":[{"conns":[{}]},{"conns":[]}],"future_field":true}}}
        """.trimIndent()

    /** 固定数值协议的双栈地址与 /32 被完整解析，未知字段被忽略，网络路由清除主机位。 */
    @Test
    fun decodesCurrentNativeContractWithoutPrefixDefaults() {
        val info = (NativeNetworkInfoDecoder.decode(payload, "test") as NetworkInfoResult.Ready).info
        assertEquals("10.0.0.2", info.virtualIpv4)
        assertEquals(32, info.networkLength)
        assertEquals("fd00::2/64", info.virtualIpv6)
        assertEquals(1, info.peerCount)
        assertEquals(listOf("192.168.1.0/24", "fd01::/64"), info.proxyRoutes)
        assertEquals(listOf("10.0.0.3/32"), info.nodeRoutes)
    }

    /** 缺少实例或尚未分配 IPv4 返回 NotReady，不能发布为已连接。 */
    @Test
    fun unassignedAddressAndMissingInstanceRemainNotReady() {
        assertEquals(NetworkInfoResult.NotReady, NativeNetworkInfoDecoder.decode(payload, "missing"))
        assertEquals(NetworkInfoResult.NotReady, NativeNetworkInfoDecoder.decode(payload.replace("167772162", "0"), "test"))
    }

    /** 内核停止或明确报告错误返回 Error；错误字符串不会出现在异常或页面状态中。 */
    @Test
    fun explicitFailureIsDistinctFromNotReady() {
        assertEquals(
            NetworkInfoResult.Error,
            NativeNetworkInfoDecoder.decode(payload.replace("\"running\":true", "\"running\":false"), "test"),
        )
        assertEquals(
            NetworkInfoResult.Error,
            NativeNetworkInfoDecoder.decode(payload.replace("\"error_msg\":null", "\"error_msg\":\"secret-marker\""), "test"),
        )
    }

    /** 地址使用旧字符串格式、u32 溢出、缺失前缀或错误 JSON 时拒绝解析，不能默认为 /24。 */
    @Test
    fun malformedRequiredFieldsCannotBecomeValidDefaults() {
        listOf(
            payload.replace("{\"addr\":167772162}", "\"10.0.0.2\""),
            payload.replace("167772162", "4294967296"),
            payload.replace("\"network_length\":32", "\"network_length\":0"),
            payload.replace(",\"network_length\":32", ""),
            "{",
        ).forEach { assertTrue(runCatching { NativeNetworkInfoDecoder.decode(it, "test") }.isFailure) }
    }
}
