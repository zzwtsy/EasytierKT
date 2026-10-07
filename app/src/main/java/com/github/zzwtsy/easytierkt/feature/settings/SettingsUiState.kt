package com.github.zzwtsy.easytierkt.feature.settings

import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.ProfileStorageError
import com.github.zzwtsy.easytierkt.data.profile.ProfileValidationError

data class SettingsUiState(
    val profile: ConnectionProfile = ConnectionProfile(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val storageError: ProfileStorageError? = null,
    val validationError: ProfileValidationError? = null,
    val saved: Boolean = false,
)
