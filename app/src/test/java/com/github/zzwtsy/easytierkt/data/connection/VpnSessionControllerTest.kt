package com.github.zzwtsy.easytierkt.data.connection

import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfileRepository
import com.github.zzwtsy.easytierkt.data.profile.MemoryProfileStore
import com.github.zzwtsy.easytierkt.data.profile.ProfileDocument
import com.github.zzwtsy.easytierkt.data.profile.SavedProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VpnSessionControllerTest {
    private val info = EasyTierNetworkInfo("10.0.0.2", 24, 0, emptyList())

    /** 连续连接、取消、再连接时，第二个内核只在第一个内核停止并释放预留后启动。 */
    @Test
    fun cleanupPrecedesNextKernelStartAndOldCallbacksAreIgnored() =
        runTest {
            val h = Harness(this)
            h.controller.connect("a")
            h.controller.connect("a")
            runCurrent()
            assertEquals(1, h.launcher.starts.size)
            val first = Host()
            h.attach(first)
            runCurrent()
            assertEquals(ConnectionPhase.CONNECTED, h.controller.status.value.phase)
            h.controller.disconnect()
            h.controller.serviceStop(
                first,
                h.launcher.starts
                    .last()
                    .second,
            )
            h.controller.connect("b")
            runCurrent()
            assertEquals(listOf("start", "stop"), h.kernel.operations)
            assertTrue(first.handles.single().closed)
            val second = Host()
            h.attach(second)
            h.controller.serviceDestroyed(first)
            h.controller.serviceStop(
                first,
                h.launcher.starts
                    .first()
                    .second,
            )
            runCurrent()
            assertEquals(listOf("start", "stop", "start"), h.kernel.operations)
            assertEquals("b", h.controller.status.value.profileId)
            assertEquals("b", h.profiles.state.value.activeProfileId)
            h.controller.serviceStop(
                second,
                h.launcher.starts
                    .last()
                    .second,
            )
            runCurrent()
        }

    /** 明确的内核失败与瞬时读取异常不同，运行中的 Error 立即关闭 TUN、停止内核并清除恢复 ID。 */
    @Test
    fun explicitNativeErrorStopsConnectedSession() =
        runTest {
            val h = Harness(this)
            h.controller.connect("a")
            runCurrent()
            val host = Host()
            h.attach(host)
            runCurrent()
            h.kernel.result = NetworkInfoResult.Error
            advanceTimeBy(2_000)
            runCurrent()
            assertEquals(ConnectionError.START_FAILED, h.controller.status.value.error)
            assertTrue(host.handles.single().closed)
            assertNull(h.store.document.resumeProfileId)
            assertNull(h.profiles.state.value.activeProfileId)
        }

    /** 瞬时读取异常保留连接与旧 TUN，下一次就绪信息更新 peer 计数但不重置连接时刻。 */
    @Test
    fun transientReadFailureRecoversWithoutReplacingTunOrClock() =
        runTest {
            val h = Harness(this)
            h.controller.connect("a")
            runCurrent()
            val host = Host()
            h.attach(host)
            runCurrent()
            h.kernel.failRead = true
            advanceTimeBy(2_000)
            runCurrent()
            assertEquals(ConnectionPhase.CONNECTED, h.controller.status.value.phase)
            h.kernel.failRead = false
            h.kernel.result = NetworkInfoResult.Ready(info.copy(peerCount = 2))
            advanceTimeBy(2_000)
            runCurrent()
            assertEquals(2, h.controller.status.value.peerCount)
            assertEquals(123L, h.controller.status.value.connectedAtEpochMs)
            assertEquals(1, host.handles.size)
            h.controller.serviceStop(
                host,
                h.launcher.starts
                    .last()
                    .second,
            )
            runCurrent()
        }

    /** 监控发现新路由时，新 FD 成功交给内核后才关闭旧 FD，连接持续可观察。 */
    @Test
    fun tunHandoffClosesPreviousOnlyAfterAttachSucceeds() =
        runTest {
            val h = Harness(this)
            h.controller.connect("a")
            runCurrent()
            val host = Host()
            h.attach(host)
            runCurrent()
            h.kernel.onAttach = { assertFalse(host.handles.first().closed) }
            h.kernel.result = NetworkInfoResult.Ready(info.copy(proxyRoutes = listOf("192.168.0.0/24")))
            advanceTimeBy(2_000)
            runCurrent()
            assertEquals(2, host.handles.size)
            assertTrue(host.handles.first().closed)
            assertFalse(host.handles.last().closed)
            h.controller.serviceStop(
                host,
                h.launcher.starts
                    .last()
                    .second,
            )
            runCurrent()
        }

    /** 新 FD 交接失败时关闭新旧接口并停止内核，不留下可复用的半连接状态。 */
    @Test
    fun failedTunHandoffClosesBothInterfaces() =
        runTest {
            val h = Harness(this)
            h.controller.connect("a")
            runCurrent()
            val host = Host()
            h.attach(host)
            runCurrent()
            h.kernel.failAttach = true
            h.kernel.result = NetworkInfoResult.Ready(info.copy(proxyRoutes = listOf("192.168.0.0/24")))
            advanceTimeBy(2_000)
            runCurrent()
            assertEquals(ConnectionError.TUN_FAILED, h.controller.status.value.error)
            assertTrue(host.handles.all { it.closed })
            assertEquals("stop", h.kernel.operations.last())
        }

    /** 非主动服务销毁保留恢复 ID；sticky 恢复读取原 ID 的最新参数而非选中配置或旧快照。 */
    @Test
    fun destructionPreservesResumeAndRestoreLoadsLatestConfiguration() =
        runTest {
            val h = Harness(this)
            h.controller.connect("a")
            runCurrent()
            val first = Host()
            h.attach(first)
            runCurrent()
            h.controller.serviceDestroyed(first)
            runCurrent()
            assertEquals("a", h.store.document.resumeProfileId)
            assertNull(h.profiles.state.value.activeProfileId)
            h.profiles.update("a", "新版", ConnectionProfile(networkName = "changed"))
            val restored = Host()
            h.controller.serviceReady(restored, null, null, restore = true)
            runCurrent()
            assertEquals("a", h.controller.status.value.profileId)
            assertEquals("新版", h.controller.status.value.profileName)
            assertTrue(h.kernel.config.contains("network_name = \"changed\""))
            h.controller.serviceStop(restored, null)
            runCurrent()
            assertNull(h.store.document.resumeProfileId)
        }

    /** 启动取消后才送达的旧服务不能重新启动内核，旧令牌也不能影响新配置。 */
    @Test
    fun delayedServiceArrivalCannotReviveCancelledSession() =
        runTest {
            val h = Harness(this)
            h.controller.connect("a")
            runCurrent()
            val oldToken =
                h.launcher.starts
                    .last()
                    .second
            val oldHost = Host()
            h.controller.serviceStop(oldHost, oldToken)
            h.controller.connect("b")
            runCurrent()
            h.controller.serviceReady(oldHost, "a", oldToken, restore = false)
            runCurrent()
            assertTrue(h.kernel.operations.isEmpty())
            assertEquals("b", h.profiles.state.value.activeProfileId)
            val current = Host()
            h.attach(current)
            runCurrent()
            h.controller.serviceStop(
                current,
                h.launcher.starts
                    .last()
                    .second,
            )
            runCurrent()
        }

    /** JNI 启动部分成功后抛出异常，也需要停止内核并撤销配置预留。 */
    @Test
    fun partialStartFailureStillStopsKernel() =
        runTest {
            val h = Harness(this)
            h.kernel.failStart = true
            h.controller.connect("a")
            runCurrent()
            h.attach(Host())
            runCurrent()
            assertEquals(listOf("start", "stop"), h.kernel.operations)
            assertEquals(ConnectionError.CONFIG_REJECTED, h.controller.status.value.error)
            assertNull(h.profiles.state.value.activeProfileId)
        }

    /** 内核停止失败时继续锁定运行配置，禁止新连接；再次停止成功后才释放预留。 */
    @Test
    fun failedNativeStopKeepsReservationUntilRetrySucceeds() =
        runTest {
            val h = Harness(this)
            h.controller.connect("a")
            runCurrent()
            val host = Host()
            h.attach(host)
            runCurrent()
            h.kernel.failStop = true
            h.controller.serviceStop(
                host,
                h.launcher.starts
                    .last()
                    .second,
            )
            runCurrent()
            assertEquals(ConnectionError.STOP_FAILED, h.controller.status.value.error)
            assertEquals("a", h.profiles.state.value.activeProfileId)
            assertTrue(h.controller.status.value.kernelRunning)
            h.controller.connect("b")
            runCurrent()
            assertEquals(1, h.launcher.starts.size)
            h.kernel.failStop = false
            h.controller.serviceStop(
                host,
                h.launcher.starts
                    .last()
                    .second,
            )
            runCurrent()
            assertNull(h.profiles.state.value.activeProfileId)
            assertEquals(ConnectionPhase.DISCONNECTED, h.controller.status.value.phase)
        }

    /** 地址已分配但自定义 DNS 路由始终不可达时等待 60 次轮询，最终报 DNS 错误并清理内核。 */
    @Test
    fun startupDnsTimeoutIsDistinctFromAddressTimeout() =
        runTest {
            val h = Harness(this)
            h.store.document =
                h.store.document.copy(
                    profiles =
                        h.store.document.profiles.map { saved ->
                            saved.copy(
                                config =
                                    saved.config.copy(
                                        dns =
                                            com.github.zzwtsy.easytierkt.data.profile.DnsOptions(
                                                mode = com.github.zzwtsy.easytierkt.data.profile.DnsMode.CUSTOM,
                                                servers = "8.8.8.8",
                                            ),
                                    ),
                            )
                        },
                )
            h.controller.connect("a")
            runCurrent()
            val host = Host()
            h.attach(host)
            runCurrent()
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(60, h.kernel.readCount)
            assertEquals(ConnectionError.DNS_UNREACHABLE, h.controller.status.value.error)
            assertTrue(host.handles.isEmpty())
            assertNull(h.profiles.state.value.activeProfileId)
        }

    private inner class Harness(
        test: TestScope,
    ) {
        val store =
            MemoryProfileStore(
                ProfileDocument(
                    profiles =
                        listOf(
                            SavedProfile("a", "A", ConnectionProfile(networkName = "a")),
                            SavedProfile("b", "B", ConnectionProfile(networkName = "b")),
                        ),
                ),
            )
        val dispatcher = StandardTestDispatcher(test.testScheduler)
        val profiles = ConnectionProfileRepository(store, dispatcher)
        val launcher = Launcher()
        val kernel = Kernel()
        val controller = VpnSessionController(profiles, test.backgroundScope, launcher, kernel, dispatcher) { 123L }

        fun attach(host: Host) {
            val (id, token) = launcher.starts.last()
            controller.serviceReady(host, id, token, restore = false)
        }
    }

    private class Launcher : VpnServiceLauncher {
        val starts = mutableListOf<Pair<String, Long>>()

        override fun connect(
            id: String,
            token: Long,
        ) {
            starts += id to token
        }

        override fun disconnect(token: Long) = Unit
    }

    private inner class Kernel : VpnKernel {
        val operations = mutableListOf<String>()
        var result: NetworkInfoResult = NetworkInfoResult.Ready(info)
        var failRead = false
        var readCount = 0
        var failAttach = false
        var failStart = false
        var failStop = false
        var config = ""
        var onAttach: () -> Unit = {}

        override fun start(config: String) {
            operations += "start"
            this.config = config
            check(!failStart)
        }

        override fun setTunFd(
            instanceName: String,
            fd: Int,
        ) {
            onAttach()
            check(!failAttach)
        }

        override fun stop() {
            operations += "stop"
            check(!failStop)
        }

        override fun readInfo(instanceName: String): NetworkInfoResult {
            readCount += 1
            check(!failRead)
            return result
        }
    }

    private class Host : VpnServiceHost {
        val handles = mutableListOf<Handle>()

        override fun establish(
            plan: VpnPlan,
            profileName: String,
        ): TunHandle = Handle().also { handles += it }

        override fun showProfile(
            name: String,
            token: Long,
        ) = Unit

        override fun finish() = Unit
    }

    private class Handle : TunHandle {
        override val fd = 7
        var closed = false

        override fun close() {
            closed = true
        }
    }
}
