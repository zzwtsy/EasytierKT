package com.github.zzwtsy.easytierkt.feature.connection

import com.github.zzwtsy.easytierkt.data.connection.ConnectionStatus
import com.github.zzwtsy.easytierkt.data.connection.isBusy
import com.github.zzwtsy.easytierkt.data.profile.ProfilesState

data class ConnectionUiState(
    val status: ConnectionStatus = ConnectionStatus(),
    val profiles: ProfilesState = ProfilesState(isLoading = false),
    val awaitingPermission: Boolean = false,
) {
    val hasProfile: Boolean get() = profiles.selectedProfile?.config?.validationError() == null && profiles.selectedProfile != null
    val canConnect: Boolean get() =
        hasProfile &&
            !profiles.isLoading &&
            profiles.error == null &&
            profiles.activeProfileId == null &&
            !status.phase.isBusy &&
            !awaitingPermission
}
