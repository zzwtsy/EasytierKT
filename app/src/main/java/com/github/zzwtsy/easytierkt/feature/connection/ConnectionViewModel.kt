package com.github.zzwtsy.easytierkt.feature.connection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.zzwtsy.easytierkt.data.connection.ConnectionRepository
import com.github.zzwtsy.easytierkt.data.connection.isBusy
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConnectionViewModel(
    private val repository: ConnectionRepository,
    private val profiles: ConnectionProfileRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val pendingId = savedStateHandle.getStateFlow<String?>(PENDING_PROFILE_ID, null)
    val uiState =
        combine(repository.status, profiles.state, pendingId) { status, state, pending ->
            ConnectionUiState(status, state, pending != null)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionUiState())

    /** 授权期间只保存 ID，返回后不能因选中项变化而连接另一份配置。 */
    fun beginPermission(): String? {
        val state = profiles.state.value
        val selected = state.selectedProfile ?: return null
        if (pendingId.value != null ||
            repository.status.value.phase.isBusy ||
            state.activeProfileId != null ||
            state.isLoading ||
            state.error != null ||
            selected.config.validationError() != null
        ) {
            return null
        }
        savedStateHandle[PENDING_PROFILE_ID] = selected.id
        return selected.id
    }

    fun finishPermission(granted: Boolean) {
        val id = pendingId.value ?: return
        savedStateHandle[PENDING_PROFILE_ID] = null
        if (granted) repository.connect(id) else repository.reportVpnPermissionDenied()
    }

    fun retryProfiles() {
        viewModelScope.launch { profiles.refresh() }
    }

    fun disconnect() = repository.disconnect()

    companion object {
        private const val PENDING_PROFILE_ID = "pending_profile_id"

        fun factory(
            repository: ConnectionRepository,
            profiles: ConnectionProfileRepository,
        ): ViewModelProvider.Factory =
            viewModelFactory { initializer { ConnectionViewModel(repository, profiles, createSavedStateHandle()) } }
    }
}
