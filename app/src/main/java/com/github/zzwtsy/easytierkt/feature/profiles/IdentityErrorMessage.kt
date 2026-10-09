package com.github.zzwtsy.easytierkt.feature.profiles

import androidx.annotation.StringRes
import com.github.zzwtsy.easytierkt.R

@StringRes
internal fun IdentityError.messageResource(): Int =
    when (this) {
        IdentityError.IMPORT_FAILED -> R.string.profile_identity_import_failed
        IdentityError.NATIVE_UNAVAILABLE -> R.string.profile_identity_native_unavailable
        IdentityError.PREPARE_FAILED -> R.string.profile_identity_prepare_failed
        IdentityError.INVALID_FOR_SAVE -> R.string.profile_identity_invalid_for_save
    }
