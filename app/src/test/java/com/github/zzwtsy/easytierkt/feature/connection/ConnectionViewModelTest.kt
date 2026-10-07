package com.github.zzwtsy.easytierkt.feature.connection

import com.github.zzwtsy.easytierkt.data.connection.ConnectionPhase
import com.github.zzwtsy.easytierkt.data.connection.ConnectionRepository
import com.github.zzwtsy.easytierkt.data.connection.ConnectionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class ConnectionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** 验证仓库处于断开状态时，ViewModel 首次可观察到的状态也是断开。 */
    @Test
    fun exposesDisconnectedStateWhenTheServiceIsStopped() =
        runTest {
            val repository = FakeConnectionRepository()
            val viewModel = ConnectionViewModel(repository)

            val state = viewModel.uiState.first()

            assertEquals(ConnectionStatus(), state.status)
        }

    /** 验证调用连接和断开操作后，仓库替身依次进入 STARTING 与 STOPPING 阶段。 */
    @Test
    fun connectAndDisconnectAreDelegatedToTheRepository() =
        runTest {
            val repository = FakeConnectionRepository()
            val viewModel = ConnectionViewModel(repository)

            viewModel.connect()
            assertEquals(ConnectionPhase.STARTING, repository.status.value.phase)

            viewModel.disconnect()
            assertEquals(ConnectionPhase.STOPPING, repository.status.value.phase)
        }
}

private class FakeConnectionRepository : ConnectionRepository {
    private val mutableStatus = MutableStateFlow(ConnectionStatus())
    override val status: StateFlow<ConnectionStatus> = mutableStatus

    override fun connect() {
        mutableStatus.value = ConnectionStatus(phase = ConnectionPhase.STARTING)
    }

    override fun disconnect() {
        mutableStatus.value = ConnectionStatus(phase = ConnectionPhase.STOPPING)
    }

    override fun reportVpnPermissionDenied() {
        mutableStatus.value = ConnectionStatus(phase = ConnectionPhase.ERROR)
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
