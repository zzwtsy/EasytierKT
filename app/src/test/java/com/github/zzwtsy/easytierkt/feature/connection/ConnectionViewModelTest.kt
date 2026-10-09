package com.github.zzwtsy.easytierkt.feature.connection

import androidx.lifecycle.SavedStateHandle
import com.github.zzwtsy.easytierkt.data.connection.ConnectionPhase
import com.github.zzwtsy.easytierkt.data.connection.ConnectionRepository
import com.github.zzwtsy.easytierkt.data.connection.ConnectionStatus
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfileRepository
import com.github.zzwtsy.easytierkt.data.profile.MemoryProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** 存储为空时首页不能连接，并暴露无配置状态。 */
    @Test
    fun exposesMissingProfileStateWhenThereIsNoConnectableProfile() =
        runTest {
            val profiles = ConnectionProfileRepository(MemoryProfileStore(), UnconfinedTestDispatcher(testScheduler))
            profiles.refresh()
            val viewModel = ConnectionViewModel(FakeConnectionRepository(), profiles, SavedStateHandle())
            val state = viewModel.uiState.first { it.profiles.document != null }
            assertFalse(state.hasProfile)
            assertFalse(state.canConnect)
            assertNull(viewModel.beginPermission())
        }

    /** 有效配置授权期间改变选中项后，授权成功仍将原配置 ID 委托给连接 Repository。 */
    @Test
    fun permissionResultUsesOriginalIdEvenWhenSelectionChanges() =
        runTest {
            val profiles = ConnectionProfileRepository(MemoryProfileStore(), UnconfinedTestDispatcher(testScheduler))
            val first = requireNotNull(profiles.create("A", ConnectionProfile(networkName = "office")).profile)
            val second = requireNotNull(profiles.create("B", ConnectionProfile(networkName = "home")).profile)
            val repository = FakeConnectionRepository()
            val viewModel = ConnectionViewModel(repository, profiles, SavedStateHandle())
            assertEquals(first.id, viewModel.beginPermission())
            assertNull(viewModel.beginPermission())
            profiles.select(second.id)
            viewModel.finishPermission(true)
            assertEquals(listOf(first.id), repository.requestedIds)
            assertEquals(ConnectionPhase.STARTING, repository.status.value.phase)
            viewModel.disconnect()
            assertEquals(ConnectionPhase.STOPPING, repository.status.value.phase)
        }

    /** 用户拒绝授权后不发送连接请求，重复返回授权结果也不启动连接。 */
    @Test
    fun deniedPermissionClearsPendingRequest() =
        runTest {
            val profiles = ConnectionProfileRepository(MemoryProfileStore(), UnconfinedTestDispatcher(testScheduler))
            profiles.create("A", ConnectionProfile(networkName = "office"))
            val repository = FakeConnectionRepository()
            val viewModel = ConnectionViewModel(repository, profiles, SavedStateHandle())
            viewModel.beginPermission()
            viewModel.finishPermission(false)
            viewModel.finishPermission(true)
            assertTrue(repository.requestedIds.isEmpty())
            assertEquals(1, repository.denials)
        }

    /** Repository 已预留会话但服务状态尚未更新时，首页仍不接受新的授权请求。 */
    @Test
    fun reservedSessionBlocksFurtherConnectRequests() =
        runTest {
            val profiles = ConnectionProfileRepository(MemoryProfileStore(), UnconfinedTestDispatcher(testScheduler))
            val profile = requireNotNull(profiles.create("A", ConnectionProfile(networkName = "office")).profile)
            profiles.reserveSession(profile.id)
            val viewModel = ConnectionViewModel(FakeConnectionRepository(), profiles, SavedStateHandle())
            assertNull(viewModel.beginPermission())
            assertFalse(viewModel.uiState.first { it.profiles.activeProfileId != null }.canConnect)
        }
}

private class FakeConnectionRepository : ConnectionRepository {
    override val status = MutableStateFlow(ConnectionStatus())
    val requestedIds = mutableListOf<String>()
    var denials = 0

    override fun connect(profileId: String) {
        requestedIds += profileId
        status.value = ConnectionStatus(phase = ConnectionPhase.STARTING, profileId = profileId)
    }

    override fun disconnect() {
        status.value = status.value.copy(phase = ConnectionPhase.STOPPING)
    }

    override fun reportVpnPermissionDenied() {
        denials += 1
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    private val dispatcher = UnconfinedTestDispatcher()

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
