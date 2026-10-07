package com.github.zzwtsy.easytierkt.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfileRepository

@Composable
internal fun SettingsRoute(
    repository: ConnectionProfileRepository,
    onBack: () -> Unit,
) {
    val factory = remember(repository) { SettingsViewModel.factory(repository) }
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        uiState = uiState,
        onBack = onBack,
        onProfileChange = viewModel::updateProfile,
        onSave = viewModel::save,
    )
}
