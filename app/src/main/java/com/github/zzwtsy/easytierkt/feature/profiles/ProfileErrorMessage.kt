package com.github.zzwtsy.easytierkt.feature.profiles

import com.github.zzwtsy.easytierkt.R
import com.github.zzwtsy.easytierkt.data.profile.ProfileActionError

internal fun profileErrorMessage(error: ProfileActionError): Int =
    when (error) {
        ProfileActionError.READ_FAILED -> R.string.error_profile_read
        ProfileActionError.WRITE_FAILED -> R.string.error_profile_save
        ProfileActionError.NOT_FOUND -> R.string.error_profile_not_found
        ProfileActionError.CONNECTION_BUSY -> R.string.profiles_disconnect_first
        ProfileActionError.INVALID_NAME -> R.string.error_invalid_profile_name
        ProfileActionError.INVALID_CONFIG -> R.string.error_profile_invalid
    }
