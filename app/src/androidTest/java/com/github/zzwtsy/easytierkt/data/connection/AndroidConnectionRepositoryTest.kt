package com.github.zzwtsy.easytierkt.data.connection

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.zzwtsy.easytierkt.TestProfileStore
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfileRepository
import com.github.zzwtsy.easytierkt.data.profile.ProfileActionError
import com.github.zzwtsy.easytierkt.data.profile.ProfileDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

@RunWith(AndroidJUnit4::class)
class AndroidConnectionRepositoryTest {
    private lateinit var scope: CoroutineScope
    private lateinit var context: RecordingContext
    private lateinit var store: TestProfileStore
    private lateinit var profiles: ConnectionProfileRepository
    private lateinit var repository: AndroidConnectionRepository

    @Before
    fun prepare() {
        ConnectionRuntime.update(ConnectionStatus())
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        context = RecordingContext(ApplicationProvider.getApplicationContext())
        store =
            TestProfileStore(
                ProfileDocument(
                    profiles =
                        listOf(
                            com.github.zzwtsy.easytierkt.data.profile
                                .SavedProfile("a", "test", ConnectionProfile(networkName = "test")),
                        ),
                    selectedProfileId = "a",
                ),
            )
        profiles = ConnectionProfileRepository(store)
        runBlocking { profiles.refresh() }
        repository = AndroidConnectionRepository(context, profiles, scope)
    }

    @After
    fun cleanup() {
        scope.cancel()
        ConnectionRuntime.update(ConnectionStatus())
    }

    /** 连续请求同一配置三次时，仅发送一个包含原 ID 的启动 Intent，并持有配置预留。 */
    @Test
    fun duplicateConnectRequestsDispatchOnlyOneExplicitIntent() {
        repeat(3) { repository.connect("a") }
        await { context.started.size == 1 }
        val intent = context.started.single()
        assertEquals(EasyTierVpnService.ACTION_CONNECT, intent.action)
        assertEquals("a", intent.getStringExtra(EasyTierVpnService.EXTRA_PROFILE_ID))
        assertEquals(EasyTierVpnService::class.java.name, intent.component?.className)
        assertEquals("a", profiles.state.value.activeProfileId)
        assertEquals("a", store.document.resumeProfileId)
    }

    /** 请求已不存在的配置时报告缺失错误，不发送 Service Intent，也不预留其他配置。 */
    @Test
    fun missingProfileCannotStartAnotherSelectedProfile() {
        repository.connect("missing")
        await { repository.status.value.error == ConnectionError.PROFILE_NOT_FOUND }
        assertEquals(0, context.started.size)
        assertNull(profiles.state.value.activeProfileId)
        assertNull(store.document.resumeProfileId)
    }

    /** 系统拒绝启动服务时释放预留并撤销恢复 ID，允许用户修正后重新连接。 */
    @Test
    fun rejectedServiceStartClearsReservationAndRecovery() {
        context.rejectStart = true
        repository.connect("a")
        await { repository.status.value.error == ConnectionError.START_FAILED }
        assertNull(profiles.state.value.activeProfileId)
        assertNull(store.document.resumeProfileId)
    }

    /** 恢复记录写入失败时不启动服务、不发布运行预留，并报告存储错误。 */
    @Test
    fun failedRecoveryWritePreventsServiceStart() {
        store.failWrite = true
        repository.connect("a")
        await { repository.status.value.error == ConnectionError.PROFILE_WRITE_FAILED }
        assertEquals(0, context.started.size)
        assertNull(profiles.state.value.activeProfileId)
        assertNull(store.document.resumeProfileId)
    }

    /** 断开 Intent 未能送达时保留运行身份和恢复记录，配置切换仍被拒绝。 */
    @Test
    fun rejectedStopDoesNotUnlockPossiblyRunningSession() {
        repository.connect("a")
        await { context.started.size == 1 }
        ConnectionRuntime.update(repository.status.value.copy(phase = ConnectionPhase.CONNECTED))
        context.rejectStop = true
        repository.disconnect()
        await { repository.status.value.error == ConnectionError.STOP_FAILED }
        assertEquals(ConnectionPhase.CONNECTED, repository.status.value.phase)
        assertEquals("a", profiles.state.value.activeProfileId)
        assertEquals("a", store.document.resumeProfileId)
        assertEquals(ProfileActionError.CONNECTION_BUSY, runBlocking { profiles.select("a") }.error)
    }

    private fun await(condition: () -> Boolean) {
        runBlocking { withTimeout(5_000) { while (!condition()) delay(10) } }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    /** 拦截 Android 分发边界，仅记录 Intent 或抛出错误，不启动 VPN 或原生内核。 */
    private class RecordingContext(
        context: Context,
    ) : ContextWrapper(context) {
        val started: MutableList<Intent> = Collections.synchronizedList(mutableListOf())
        var rejectStart = false
        var rejectStop = false

        override fun getApplicationContext(): Context = this

        override fun startForegroundService(service: Intent): ComponentName {
            if (rejectStart) throw SecurityException("Test start rejection")
            started += Intent(service)
            return requireNotNull(service.component)
        }

        override fun startService(service: Intent): ComponentName {
            if (rejectStop) throw IllegalStateException("Test stop rejection")
            return requireNotNull(service.component)
        }
    }
}
