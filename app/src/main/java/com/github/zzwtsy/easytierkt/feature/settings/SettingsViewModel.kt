package com.github.zzwtsy.easytierkt.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: ConnectionProfileRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            val result = repository.load()
            mutableUiState.update {
                it.copy(
                    profile = result.profile,
                    isLoading = false,
                    storageError = result.error,
                    validationError = result.profile.validationError(),
                )
            }
        }
    }

    fun updateProfile(profile: ConnectionProfile) {
        mutableUiState.update {
            it.copy(
                profile = profile,
                validationError = profile.validationError(),
                saved = false,
            )
        }
    }

    fun save() {
        val profile = mutableUiState.value.profile
        val validationError = profile.validationError()
        if (validationError != null) {
            mutableUiState.update { it.copy(validationError = validationError, saved = false) }
            return
        }

        viewModelScope.launch {
            mutableUiState.update { it.copy(isSaving = true, saved = false) }
            val error = repository.save(profile)
            mutableUiState.update {
                it.copy(
                    isSaving = false,
                    storageError = error,
                    saved = error == null,
                )
            }
        }
    }

    companion object {
        fun factory(repository: ConnectionProfileRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    SettingsViewModel(repository)
                }
            }
    }
}
