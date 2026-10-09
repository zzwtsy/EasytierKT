package com.github.zzwtsy.easytierkt.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfileRepository
import com.github.zzwtsy.easytierkt.data.profile.ProfileActionError
import com.github.zzwtsy.easytierkt.data.profile.ProfileValidationError
import com.github.zzwtsy.easytierkt.data.profile.isProfileNameValid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileEditorUiState(
    val profile: ConnectionProfile = ConnectionProfile(),
    val displayName: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: ProfileActionError? = null,
    val validationError: ProfileValidationError? = null,
    val nameError: Boolean = false,
    val saved: Boolean = false,
    val dirty: Boolean = false,
    val isRunning: Boolean = false,
)

/** 草稿只留在条目 ViewModel，Activity 重建保留，进程回收后从加密存储重新加载。 */
class ProfileEditorViewModel(
    private val repository: ConnectionProfileRepository,
    private val profileId: String?,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ProfileEditorUiState())
    val uiState = mutableUiState.asStateFlow()
    private var originalProfile = ConnectionProfile()
    private var originalName = ""

    init {
        reload()
        viewModelScope.launch {
            repository.state.collect { state ->
                mutableUiState.update { it.copy(isRunning = profileId != null && state.activeProfileId == profileId) }
            }
        }
    }

    fun reload() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true) }
            repository.refresh()
            val result = profileId?.let { repository.getById(it) }
            val error = result?.error ?: repository.state.value.error
            if (error == null) {
                originalProfile = result?.profile?.config ?: ConnectionProfile()
                originalName = result?.profile?.displayName.orEmpty()
            }
            mutableUiState.update {
                it.copy(profile = originalProfile, displayName = originalName, isLoading = false, error = error, dirty = false)
            }
        }
    }

    fun updateName(name: String) {
        if (mutableUiState.value.isSaving || mutableUiState.value.isLoading) return
        mutableUiState.update {
            it.copy(
                displayName = name,
                nameError = !isProfileNameValid(name),
                dirty = name != originalName || it.profile != originalProfile,
                saved = false,
            )
        }
    }

    fun updateProfile(profile: ConnectionProfile) {
        if (mutableUiState.value.isSaving || mutableUiState.value.isLoading) return
        mutableUiState.update {
            it.copy(
                profile = profile,
                validationError = profile.validationError(),
                dirty =
                    it.displayName != originalName || profile != originalProfile,
                saved = false,
            )
        }
    }

    fun save() {
        val current = mutableUiState.value
        if (current.isLoading ||
            current.isSaving ||
            current.error == ProfileActionError.READ_FAILED ||
            current.error == ProfileActionError.NOT_FOUND
        ) {
            return
        }
        val validation = current.profile.validationError()
        val invalidName = !isProfileNameValid(current.displayName)
        if (validation != null || invalidName) {
            mutableUiState.update { it.copy(validationError = validation, nameError = invalidName) }
            return
        }
        // 在启动协程前置忙碌标志，同一帧重复点击也不能创建两份配置。
        mutableUiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result =
                if (profileId == null) {
                    repository.create(current.displayName, current.profile)
                } else {
                    repository.update(profileId, current.displayName, current.profile)
                }
            mutableUiState.update {
                it.copy(
                    isSaving = false,
                    error = result.error,
                    saved = result.error == null,
                    dirty =
                        result.error != null,
                )
            }
        }
    }

    fun consumeSaved() {
        mutableUiState.update { it.copy(saved = false) }
    }

    companion object {
        fun factory(
            repository: ConnectionProfileRepository,
            profileId: String?,
        ): ViewModelProvider.Factory = viewModelFactory { initializer { ProfileEditorViewModel(repository, profileId) } }
    }
}
