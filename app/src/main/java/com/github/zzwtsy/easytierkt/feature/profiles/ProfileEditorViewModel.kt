package com.github.zzwtsy.easytierkt.feature.profiles

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.zzwtsy.easytierkt.data.profile.ApplicationsRepository
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfileRepository
import com.github.zzwtsy.easytierkt.data.profile.InstalledApplication
import com.github.zzwtsy.easytierkt.data.profile.NativeSecureIdentityProvider
import com.github.zzwtsy.easytierkt.data.profile.ProfileActionError
import com.github.zzwtsy.easytierkt.data.profile.ProfileValidationError
import com.github.zzwtsy.easytierkt.data.profile.SecureIdentityProvider
import com.github.zzwtsy.easytierkt.data.profile.isProfileNameValid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class IdentityError { IMPORT_FAILED, NATIVE_UNAVAILABLE, PREPARE_FAILED, INVALID_FOR_SAVE }

data class ProfileEditorUiState(
    val applications: List<InstalledApplication> = emptyList(),
    val applicationsLoading: Boolean = false,
    val applicationsFailed: Boolean = false,
    val invalidNumbers: Set<com.github.zzwtsy.easytierkt.data.profile.ProfileField> = emptySet(),
    val identityBusy: Boolean = false,
    val identityError: IdentityError? = null,
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
    private val identityProvider: SecureIdentityProvider = NativeSecureIdentityProvider,
    private val applicationsRepository: ApplicationsRepository? = null,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ProfileEditorUiState())
    val uiState = mutableUiState.asStateFlow()
    val draft = ProfileDraft()
    private var originalProfile = ConnectionProfile()
    private var originalName = ""

    init {
        reload()
        reloadApplications()
        viewModelScope.launch { snapshotFlow { draft.rawValues() }.collect { publishDraft() } }
        viewModelScope.launch {
            repository.state.collect { state ->
                mutableUiState.update { it.copy(isRunning = profileId != null && state.activeProfileId == profileId) }
            }
        }
    }

    fun reloadApplications() {
        val source = applicationsRepository ?: return
        if (mutableUiState.value.applicationsLoading) return
        mutableUiState.update { it.copy(applicationsLoading = true, applicationsFailed = false) }
        viewModelScope.launch {
            try {
                val apps = source.load()
                mutableUiState.update { it.copy(applications = apps, applicationsLoading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableUiState.update { it.copy(applicationsLoading = false, applicationsFailed = true) }
            }
        }
    }

    fun reload() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, invalidNumbers = emptySet()) }
            repository.refresh()
            val result = profileId?.let { repository.getById(it) }
            val error = result?.error ?: repository.state.value.error
            if (error == null) {
                originalProfile = result?.profile?.config ?: ConnectionProfile()
                originalName = result?.profile?.displayName.orEmpty()
            }
            draft.reset(originalProfile, originalName)
            mutableUiState.update {
                it.copy(profile = originalProfile, displayName = originalName, isLoading = false, error = error, dirty = false)
            }
        }
    }

    fun updateName(name: String) {
        if (!canEdit()) return
        draft.state("displayName").setTextAndPlaceCursorAtEnd(name)
        publishDraft()
    }

    fun updateProfile(profile: ConnectionProfile) {
        if (!canEdit()) return
        draft.update(profile, mutableUiState.value.profile)
        publishDraft()
    }

    fun updateNumericDraft(
        path: String,
        text: String,
    ) {
        if (!canEdit()) return
        draft.state(path).setTextAndPlaceCursorAtEnd(text)
        publishDraft()
    }

    private fun canEdit(): Boolean = mutableUiState.value.let { !it.isSaving && !it.isLoading && !it.identityBusy }

    private fun publishDraft() {
        if (mutableUiState.value.isLoading) return
        var compiled = draft.compile()
        if (compiled.profile.security.privateKey != mutableUiState.value.profile.security.privateKey) {
            draft.state("security.publicKey").setTextAndPlaceCursorAtEnd("")
            compiled = draft.compile()
        }
        mutableUiState.update {
            it.copy(
                profile = compiled.profile,
                displayName = compiled.displayName,
                invalidNumbers = compiled.invalidNumbers,
                validationError = compiled.profile.validationError(),
                nameError = !isProfileNameValid(compiled.displayName),
                dirty = draft.isDirty(),
                saved = if (draft.isDirty()) false else it.saved,
            )
        }
    }

    fun importIdentity(readPrivateKey: suspend () -> String) {
        if (mutableUiState.value.isSaving || mutableUiState.value.identityBusy) return
        mutableUiState.update { it.copy(identityBusy = true, identityError = null) }
        viewModelScope.launch {
            try {
                val privateKey = readPrivateKey()
                require(
                    com.github.zzwtsy.easytierkt.data.profile
                        .validKey(privateKey),
                )
                val identity = identityProvider.prepare(privateKey)
                draft.update(
                    mutableUiState.value.profile.copy(
                        security =
                            mutableUiState.value.profile.security.copy(
                                privateKey = identity.privateKey,
                                publicKey = identity.publicKey,
                            ),
                    ),
                    previous = mutableUiState.value.profile,
                )
                mutableUiState.update {
                    it.copy(
                        profile =
                            it.profile.copy(
                                security = it.profile.security.copy(privateKey = identity.privateKey, publicKey = identity.publicKey),
                            ),
                        dirty = true,
                        identityBusy = false,
                        validationError = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableUiState.update { it.copy(identityBusy = false, identityError = IdentityError.IMPORT_FAILED) }
            } catch (_: LinkageError) {
                mutableUiState.update { it.copy(identityBusy = false, identityError = IdentityError.NATIVE_UNAVAILABLE) }
            }
        }
    }

    fun prepareIdentity(privateKey: String = "") {
        if (mutableUiState.value.isSaving || mutableUiState.value.identityBusy) return
        mutableUiState.update { it.copy(identityBusy = true, identityError = null) }
        viewModelScope.launch {
            try {
                val identity = identityProvider.prepare(privateKey)
                draft.update(
                    mutableUiState.value.profile.copy(
                        security =
                            mutableUiState.value.profile.security.copy(
                                privateKey = identity.privateKey,
                                publicKey = identity.publicKey,
                            ),
                    ),
                    previous = mutableUiState.value.profile,
                )
                mutableUiState.update {
                    it.copy(
                        profile =
                            it.profile.copy(
                                security = it.profile.security.copy(privateKey = identity.privateKey, publicKey = identity.publicKey),
                            ),
                        dirty = true,
                        identityBusy = false,
                        validationError = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: LinkageError) {
                mutableUiState.update { it.copy(identityBusy = false, identityError = IdentityError.NATIVE_UNAVAILABLE) }
            } catch (_: Exception) {
                mutableUiState.update { it.copy(identityBusy = false, identityError = IdentityError.PREPARE_FAILED) }
            }
        }
    }

    fun save() {
        if (!mutableUiState.value.isLoading && !mutableUiState.value.isSaving) publishDraft()
        val current = mutableUiState.value
        if (current.identityBusy ||
            current.invalidNumbers.isNotEmpty() ||
            current.isLoading ||
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
            val profile =
                try {
                    if (current.profile.security.secureMode || current.profile.credentialMode) {
                        val identity = identityProvider.prepare(current.profile.security.privateKey)
                        current.profile.copy(
                            security = current.profile.security.copy(privateKey = identity.privateKey, publicKey = identity.publicKey),
                        )
                    } else {
                        current.profile
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: LinkageError) {
                    mutableUiState.update { it.copy(isSaving = false, identityError = IdentityError.NATIVE_UNAVAILABLE) }
                    return@launch
                } catch (_: Exception) {
                    mutableUiState.update { it.copy(isSaving = false, identityError = IdentityError.INVALID_FOR_SAVE) }
                    return@launch
                }
            draft.update(profile, mutableUiState.value.profile)
            mutableUiState.update { it.copy(profile = profile) }
            val result =
                if (profileId == null) {
                    repository.create(current.displayName, profile)
                } else {
                    repository.update(profileId, current.displayName, profile)
                }
            if (result.error == null) draft.reset(profile, current.displayName)
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
            applicationsRepository: ApplicationsRepository? = null,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ProfileEditorViewModel(repository, profileId, applicationsRepository = applicationsRepository) }
            }
    }
}
