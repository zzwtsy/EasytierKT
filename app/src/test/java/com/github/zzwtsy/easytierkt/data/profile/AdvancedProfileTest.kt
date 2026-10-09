package com.github.zzwtsy.easytierkt.data.profile

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class AdvancedProfileTest {
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { 7 })
    private val profile = ConnectionProfile(networkName = "test")

    /** 凭据认证必须省略共享密钥，并保留安全身份；空共享密钥认证则必须显式编码空字符串。 */
    @Test
    fun credentialAndEmptySharedSecretAreDistinct() {
        val credential = profile.copy(security = SecurityOptions(mode = AuthenticationMode.CREDENTIAL, privateKey = key))
        assertFalse(credential.toEasyTierToml().contains("network_secret ="))
        assertTrue(credential.toEasyTierToml().contains("local_private_key ="))
        assertTrue(profile.toEasyTierToml().contains("network_secret = \"\""))
    }

    /** 根字段在表之前编码；显式 /32、静态 IPv6 和手动路由不会落入 network_identity 表作用域。 */
    @Test
    fun rootFieldsPreservePrefixesAndScope() {
        val toml =
            profile
                .copy(
                    useDhcp = false,
                    ipv4Address = "10.0.0.2",
                    ipv4Prefix = 32,
                    virtualIpv6 = "fd00::2",
                    routes = "192.168.5.7/24",
                    routing = RoutingOptions(mode = RouteMode.MANUAL),
                ).toEasyTierToml()
        assertTrue(toml.contains("ipv4 = \"10.0.0.2/32\""))
        assertTrue(toml.contains("ipv6 = \"fd00::2/64\""))
        assertTrue(toml.indexOf("routes =") < toml.indexOf("[network_identity]"))
        assertTrue(toml.contains("192.168.5.0/24"))
    }

    /** 双栈需要至少 1280 MTU；分应用白名单不能为空，两个问题均应返回供表单定位。 */
    @Test
    fun collectsAllActiveFieldIssues() {
        val invalid =
            profile.copy(
                virtualIpv6 = "fd00::2",
                transport = TransportOptions(mtu = 1200),
                routing = RoutingOptions(applicationMode = ApplicationMode.INCLUDE),
            )
        val fields = invalid.issues().map { it.field.path }
        assertTrue("transport.mtu" in fields)
        assertTrue("routing.applications" in fields)
    }

    /** ACL 允许同一链按不同优先级排序；重复优先级被拒绝，禁用 ACL 时不阻止保存其他配置。 */
    @Test
    fun aclPrioritiesMustBeUniqueWithinChain() {
        val rules = listOf(AclRule(name = "A", priority = 100), AclRule(name = "B", priority = 100))
        val invalid = profile.copy(acl = AclOptions(enabled = true, chains = listOf(AclChain(name = "in", rules = rules))))
        assertTrue(invalid.issues().any { it.field.path.endsWith(".priority") })
        assertTrue(invalid.copy(acl = invalid.acl.copy(enabled = false)).issues().isEmpty())
    }

    /** TXT/SRV/WireGuard/HTTPS 均接受，非法协议与非数字 IP 不接受，校验不需要联网查询。 */
    @Test
    fun validatesAdditionalEndpointsWithoutDnsLookup() {
        listOf("txt://peers.example", "srv://_easytier._tcp.example", "wg://peer.example:11010", "https://peer.example/config").forEach {
            assertTrue(validEndpoint(it))
        }
        assertFalse(validEndpoint("ftp://peer.example"))
        assertNull(numericIp("peer.example"))
        assertNull(numericIp("fe80::1%wlan0"))
    }

    /** 序列化后嵌套 ACL、端口转发和稳定条目 ID 完整恢复，确保重启后表单与路由参数不丢失。 */
    @Test
    fun nestedConfigurationRoundTrips() {
        val full =
            profile.copy(
                acl = AclOptions(chains = listOf(AclChain(name = "in", rules = listOf(AclRule(name = "allow"))))),
                localProxy = LocalProxyOptions(forwards = listOf(PortForward(destination = "10.0.0.3"))),
            )
        assertEquals(full, Json.decodeFromString<ConnectionProfile>(Json.encodeToString(full)))
    }

    /** 标准 32 字节密钥接受，缺少填充、错误长度和非零填充位被拒绝，与原生 Base64 解码契约一致。 */
    @Test
    fun acceptsOnlyCanonical32ByteKeys() {
        assertTrue(validKey(key))
        assertFalse(validKey(key.dropLast(1)))
        assertFalse(validKey(Base64.getEncoder().encodeToString(ByteArray(31))))
        assertFalse(validKey(key.take(42) + "B="))
        assertFalse(validKey(" $key"))
    }

    /** 通配 TCP 监听与同端口本地 SOCKS5 冲突，但相同端口的 TCP 与 UDP 转发可以并存。 */
    @Test
    fun detectsLocalPortConflictsAcrossFeatures() {
        val conflict = profile.copy(localProxy = LocalProxyOptions(socks5 = true, port = 11010))
        assertTrue(conflict.issues().any { it.field.path == "localProxy.port" })
        val forwards = listOf(PortForward(destination = "10.0.0.3"), PortForward(protocol = "udp", destination = "10.0.0.3"))
        assertTrue(profile.copy(localProxy = LocalProxyOptions(forwards = forwards)).issues().isEmpty())
    }

    /** ACL 输入带主机位的双栈网段和裸地址时，编码为内核可解析的网络或主机 CIDR，保留全部条件。 */
    @Test
    fun aclAddressesAreNormalizedWithoutDroppingConditions() {
        val rule =
            AclRule(
                name = "restricted",
                sourceIps = "10.0.0.3/24\n10.1.0.3\nfd00::3/64\nfd01::3",
                destinationIps = "192.168.5.7/24\n2001:db8:1::7/64",
            )
        val full = profile.copy(acl = AclOptions(enabled = true, chains = listOf(AclChain(name = "in", rules = listOf(rule)))))
        assertTrue(full.issues().isEmpty())
        val toml = full.toEasyTierToml()
        // 库允许数组换行；逐项核对规范化值，保留每一个地址条件。
        listOf("10.0.0.0/24", "10.1.0.3/32", "fd00::/64", "fd01::3/128", "192.168.5.0/24", "2001:db8:1::/64")
            .forEach { assertTrue(toml.contains("\"$it\"")) }
    }

    /** ACL 列表混有无效地址时，校验定位到对应规则且拒绝编码，不能删除错误条件后输出可用配置。 */
    @Test
    fun invalidAclAddressRejectsWholeConfiguration() {
        val rule = AclRule(name = "restricted", sourceIps = "10.0.0.3/24\ninvalid-address", destinationIps = "fd00::3/129")
        val full = profile.copy(acl = AclOptions(enabled = true, chains = listOf(AclChain(name = "in", rules = listOf(rule)))))
        assertTrue(full.issues().any { it.field.path == "rule.${rule.id}.sourceIps" })
        assertTrue(full.issues().any { it.field.path == "rule.${rule.id}.destinationIps" })
        assertThrows(IllegalArgumentException::class.java) { full.toEasyTierToml() }
    }

    /** 共享密钥模式关闭安全握手却配置公钥绑定时，校验报错且不能生成绕过公钥检查的 TOML。 */
    @Test
    fun peerPinRequiresSecureHandshake() {
        val full =
            profile.copy(
                peerAddresses = "tcp://peer.example:11010",
                security = SecurityOptions(peerKeys = listOf(PeerKey(uri = "tcp://peer.example:11010", publicKey = key))),
            )
        assertTrue(full.issues().any { it.field.path == "security.peerKeys" && it.code == ProfileIssueCode.SECURE_HANDSHAKE_REQUIRED })
        assertThrows(IllegalArgumentException::class.java) { full.toEasyTierToml() }
    }

    /** 共享密钥安全握手和客户端凭据模式配置公钥绑定时，均通过校验并同时编码安全握手及绑定公钥。 */
    @Test
    fun secureAuthenticationModesPreservePeerPin() {
        AuthenticationMode.entries.forEach { mode ->
            val full =
                profile.copy(
                    peerAddresses = "tcp://peer.example:11010",
                    security =
                        SecurityOptions(
                            mode = mode,
                            secureMode = mode == AuthenticationMode.SHARED_SECRET,
                            privateKey = key,
                            peerKeys = listOf(PeerKey(uri = "tcp://peer.example:11010", publicKey = key)),
                        ),
                )
            assertTrue(full.issues().isEmpty())
            val toml = full.toEasyTierToml()
            assertTrue(toml.contains("[secure_mode]\nenabled = true"))
            assertTrue(toml.contains("peer_public_key = \"$key\""))
        }
    }
}
