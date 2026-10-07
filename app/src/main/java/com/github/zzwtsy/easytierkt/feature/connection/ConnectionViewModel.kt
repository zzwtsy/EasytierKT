package com.github.zzwtsy.easytierkt.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.zzwtsy.easytierkt.data.connection.ConnectionRepository
import com.github.zzwtsy.easytierkt.data.connection.ConnectionStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ConnectionViewModel(
    repository: ConnectionRepository,
) : ViewModel() {
    val uiState: StateFlow<ConnectionUiState> = repository.status
        .map(::ConnectionUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = ConnectionUiState(status = ConnectionStatus.Unavailable),
        )

    companion object {
        fun factory(repository: ConnectionRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ConnectionViewModel(repository)
            }
        }
    }
}
