package com.github.zzwtsy.easytierkt.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfileRepository
import com.github.zzwtsy.easytierkt.data.profile.ProfileActionError
import com.github.zzwtsy.easytierkt.data.profile.ProfileActionResult
import com.github.zzwtsy.easytierkt.data.profile.ProfilesState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfilesUiState(
    val profiles: ProfilesState = ProfilesState(),
    val isWorking: Boolean = false,
    val operationError: ProfileActionError? = null,
)

class ProfilesViewModel(
    private val repository: ConnectionProfileRepository,
) : ViewModel() {
    private val working = MutableStateFlow(false)
    private val error = MutableStateFlow<ProfileActionError?>(null)
    val uiState =
        combine(repository.state, working, error, ::ProfilesUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfilesUiState())

    init {
        retry()
    }

    fun retry() {
        viewModelScope.launch { repository.refresh() }
    }

    fun select(id: String) = perform { repository.select(id) }

    fun delete(id: String) = perform { repository.delete(id) }

    private fun perform(action: suspend () -> ProfileActionResult) {
        if (working.value) return
        working.value = true
        error.value = null
        viewModelScope.launch {
            try {
                error.value = action().error
            } finally {
                working.value = false
            }
        }
    }

    companion object {
        fun factory(repository: ConnectionProfileRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { ProfilesViewModel(repository) } }
    }
}
