package com.github.zzwtsy.easytierkt.data.connection

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.easytier.jni.EasyTierJNI
import com.github.zzwtsy.easytierkt.data.profile.AclChain
import com.github.zzwtsy.easytierkt.data.profile.AclOptions
import com.github.zzwtsy.easytierkt.data.profile.AclRule
import com.github.zzwtsy.easytierkt.data.profile.AuthenticationMode
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.NativeSecureIdentityProvider
import com.github.zzwtsy.easytierkt.data.profile.SecurityOptions
import com.github.zzwtsy.easytierkt.data.profile.validKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EasyTierNativeConfigurationTest {
    @After
    fun cleanup() {
        EasyTierEngine.stop()
    }

    /** 原生生成两份独立身份，随后用同一私钥派生相同公钥；凭据 TOML 可由真实 JNI 解析。 */
    @Test
    fun generatesStableCredentialIdentity() =
        runBlocking {
            val identity = NativeSecureIdentityProvider.prepare("")
            val other = NativeSecureIdentityProvider.prepare("")
            assertTrue(validKey(identity.privateKey) && validKey(identity.publicKey))
            assertNotEquals(identity.privateKey, other.privateKey)
            assertEquals(identity, NativeSecureIdentityProvider.prepare(identity.privateKey))
            val profile =
                ConnectionProfile(
                    networkName = "native-test",
                    listeners = "",
                    security =
                        SecurityOptions(
                            mode = AuthenticationMode.CREDENTIAL,
                            privateKey = identity.privateKey,
                            publicKey = identity.publicKey,
                        ),
                )
            assertEquals(0, EasyTierJNI.parseConfig(profile.toEasyTierToml()))
        }

    /** 真实内核启动显式 /32 和静态 IPv6；运行信息应返回同一前缀与 IPv6，供 Android TUN 使用。 */
    @Test
    fun reportsStaticDualStackAndExplicitHostPrefix() =
        runBlocking {
            val profile =
                ConnectionProfile(
                    networkName = "native-test",
                    useDhcp = false,
                    ipv4Address = "10.0.0.2",
                    ipv4Prefix = 32,
                    virtualIpv6 = "fd00::2",
                    listeners = "",
                )
            EasyTierEngine.start(profile.toEasyTierToml())
            val info =
                withTimeout(10000) {
                    var info: EasyTierNetworkInfo? = null
                    while (info == null) {
                        info = EasyTierEngine.networkInfo(ConnectionProfile.INSTANCE_NAME)
                        if (info == null) delay(100)
                    }
                    info
                }
            assertEquals(32, info.networkLength)
            assertEquals("fd00::2/64", info.virtualIpv6)
        }

    /** 无效私钥只能抛出固定错误，异常文本不能包含用户输入。 */
    @Test
    fun invalidPrivateKeyErrorsAreRedacted() {
        val marker = "private-key-secret-marker"
        val error = runCatching { EasyTierJNI.prepareSecureIdentity(marker) }.exceptionOrNull()
        assertTrue(error is RuntimeException)
        assertTrue(error?.message?.contains(marker) == false)
    }

    /** ACL 表单输入双栈主机地址及带主机位网段后，真实 Android 编码器输出的 TOML 可由 JNI 解析。 */
    @Test
    fun parsesNormalizedDualStackAclFromProfile() {
        val profile =
            ConnectionProfile(
                networkName = "native-acl-test",
                acl =
                    AclOptions(
                        enabled = true,
                        chains =
                            listOf(
                                AclChain(
                                    name = "in",
                                    rules =
                                        listOf(
                                            AclRule(
                                                name = "restricted",
                                                sourceIps = "10.0.0.3/24\nfd00::3/64\n10.1.0.3\nfd01::3",
                                                destinationIps = "192.168.5.7/24\n2001:db8:1::7/64",
                                            ),
                                        ),
                                ),
                            ),
                    ),
            )
        assertTrue(profile.issues().isEmpty())
        assertEquals(0, EasyTierJNI.parseConfig(profile.toEasyTierToml()))
    }
}
