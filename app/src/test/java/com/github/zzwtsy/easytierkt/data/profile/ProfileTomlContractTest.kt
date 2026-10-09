package com.github.zzwtsy.easytierkt.data.profile

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

class ProfileTomlContractTest {
    /** 多链、多规则和转义文本使用真实编码器生成 fixture，供上游 Rust 解析器核对完整语义。 */
    @Test
    fun generatesFixturesForNativeSemanticVerification() {
        val full =
            ConnectionProfile(
                networkName = "contract",
                networkSecret = "shared\"\\key",
                listeners = "",
                useDhcp = false,
                ipv4Address = "10.0.0.2",
                ipv4Prefix = 32,
                virtualIpv6 = "FD00::2",
                peerAddresses = "tcp://192.0.2.1:11010\nudp://192.0.2.2:11010",
                routing = RoutingOptions(mode = RouteMode.MANUAL),
                localProxy =
                    LocalProxyOptions(
                        forwards =
                            listOf(
                                PortForward(bindPort = 8001, destination = "10.0.0.3", destinationPort = 80),
                                PortForward(protocol = "udp", bindPort = 8002, destination = "10.0.0.4", destinationPort = 53),
                            ),
                    ),
                transport = TransportOptions(multiThread = false, encryption = false, compression = true, receiveBytesPerSecond = 4096),
                acl =
                    AclOptions(
                        enabled = true,
                        declares = listOf(AclGroup(name = "office", secret = "synthetic-only")),
                        chains =
                            AclChainType.entries.map { type ->
                                AclChain(
                                    name = type.name,
                                    type = type,
                                    defaultAction = AclAction.DROP,
                                    rules =
                                        listOf(
                                            AclRule(
                                                name = "first",
                                                description = "line1\nline2\t\"\\",
                                                priority = 100,
                                                protocol = AclProtocol.TCP,
                                                sourceIps = "10.0.0.7/24\nfd00::7/64",
                                                destinationPorts = "80,443",
                                                rateLimit = 10,
                                                burstLimit = 20,
                                                stateful = true,
                                            ),
                                            AclRule(name = "second", priority = 101, protocol = AclProtocol.UDP),
                                        ),
                                )
                            },
                    ),
            )
        assertTrue(full.issues().isEmpty())
        val credential =
            ConnectionProfile(
                networkName = "credential",
                listeners = "",
                security =
                    SecurityOptions(
                        mode = AuthenticationMode.CREDENTIAL,
                        privateKey = Base64.getEncoder().encodeToString(ByteArray(32) { 7 }),
                    ),
            )
        val cases =
            mapOf(
                "full" to full,
                "automatic" to ConnectionProfile(networkName = "automatic", listeners = ""),
                "credential" to credential,
            )
        val directory = File("build/generated-contracts").apply { mkdirs() }
        cases.forEach { (name, profile) ->
            assertTrue(profile.issues().isEmpty())
            File(directory, "$name.toml").writeText(profile.toEasyTierToml())
        }
    }
}
