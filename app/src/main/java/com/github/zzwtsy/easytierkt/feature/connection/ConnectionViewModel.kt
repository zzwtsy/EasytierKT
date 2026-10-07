package com.github.zzwtsy.easytierkt.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.zzwtsy.easytierkt.data.connection.ConnectionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ConnectionViewModel(
    private val repository: ConnectionRepository,
) : ViewModel() {
    /** 最后一个订阅者离开 5 秒后停止状态映射的上游收集；这不会调用 disconnect() 或停止连接服务。 */
    val uiState: StateFlow<ConnectionUiState> = repository.status
        .map(::ConnectionUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = ConnectionUiState(),
        )

    fun connect() = repository.connect()

    fun disconnect() = repository.disconnect()

    fun reportVpnPermissionDenied() = repository.reportVpnPermissionDenied()

    companion object {
        fun factory(repository: ConnectionRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ConnectionViewModel(repository)
            }
        }
    }
}
