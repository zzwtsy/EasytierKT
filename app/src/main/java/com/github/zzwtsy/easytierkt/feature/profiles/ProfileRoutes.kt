package com.github.zzwtsy.easytierkt.feature.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zzwtsy.easytierkt.R
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfileRepository

@Composable
internal fun ProfilesRoute(
    repository: ConnectionProfileRepository,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    showSaved: Boolean,
    onSavedShown: () -> Unit,
) {
    val factory = remember(repository) { ProfilesViewModel.factory(repository) }
    val viewModel: ProfilesViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfilesScreen(state, onBack, onCreate, onEdit, viewModel::select, viewModel::delete, viewModel::retry, showSaved, onSavedShown)
}

@Composable
internal fun ProfileEditorRoute(
    repository: ConnectionProfileRepository,
    profileId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val factory = remember(repository, profileId) { ProfileEditorViewModel.factory(repository, profileId) }
    val viewModel: ProfileEditorViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val requestBack: () -> Unit = {
        if (!state.isSaving) {
            if (state.dirty) confirmDiscard = true else onBack()
        }
    }
    BackHandler(enabled = state.dirty || state.isSaving) { requestBack() }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            viewModel.consumeSaved()
            onSaved()
        }
    }
    ProfileEditorScreen(state, requestBack, viewModel::updateProfile, viewModel::updateName, viewModel::reload, viewModel::save)
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.profile_discard_title)) },
            text = { Text(stringResource(R.string.profile_discard_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onBack()
                }) { Text(stringResource(R.string.profile_discard)) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
