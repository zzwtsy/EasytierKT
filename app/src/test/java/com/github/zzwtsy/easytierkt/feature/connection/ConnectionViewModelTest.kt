package com.github.zzwtsy.easytierkt.feature.connection

import com.github.zzwtsy.easytierkt.data.connection.ConnectionStatus
import com.github.zzwtsy.easytierkt.data.connection.UnavailableConnectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun exposesUnavailableStateUntilConnectionBackendIsAdded() = runTest {
        val viewModel = ConnectionViewModel(UnavailableConnectionRepository())

        val state = viewModel.uiState.first()

        assertEquals(ConnectionStatus.Unavailable, state.status)
    }

    @Test
    fun unavailableRepositoryEmitsUnavailableStatus() = runTest {
        val status = UnavailableConnectionRepository().status.first()

        assertEquals(ConnectionStatus.Unavailable, status)
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
