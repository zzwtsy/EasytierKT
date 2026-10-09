package com.github.zzwtsy.easytierkt.feature.profiles

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.zzwtsy.easytierkt.R
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.ProfileActionError
import com.github.zzwtsy.easytierkt.data.profile.ProfileDocument
import com.github.zzwtsy.easytierkt.data.profile.ProfilesState
import com.github.zzwtsy.easytierkt.data.profile.SavedProfile
import com.github.zzwtsy.easytierkt.ui.icons.ArrowBackIcon
import com.github.zzwtsy.easytierkt.ui.theme.EasytierKTTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ProfilesScreen(
    uiState: ProfilesUiState,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRetry: () -> Unit,
    showSaved: Boolean,
    onSavedShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = uiState.profiles
    val profiles = state.document?.profiles.orEmpty()
    val hasReadError = state.error == ProfileActionError.READ_FAILED
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val deleting = profiles.find { it.id == deletingId }
    val snackbar = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.settings_saved)
    LaunchedEffect(showSaved) {
        if (showSaved) {
            onSavedShown()
            snackbar.showSnackbar(savedMessage)
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profiles_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(ArrowBackIcon, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = onCreate,
                        enabled = !state.isLoading && !hasReadError && !uiState.isWorking,
                        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.profiles_add))
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (state.isLoading) {
                LoadingIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxSize().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (hasReadError) {
                                Text(stringResource(R.string.error_profile_read), color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = onRetry) { Text(stringResource(R.string.profiles_retry)) }
                            } else if (profiles.isEmpty()) {
                                Text(stringResource(R.string.profiles_empty))
                            } else if (state.activeProfileId != null) {
                                Text(stringResource(R.string.profiles_disconnect_first))
                            } else if (state.document?.selectedProfileId == null) {
                                Text(stringResource(R.string.profiles_choose))
                            }
                            uiState.operationError?.let {
                                Text(
                                    stringResource(profileErrorMessage(it)),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    if (!hasReadError) {
                        items(profiles, key = { it.id }) { profile ->
                            val active = state.activeProfileId == profile.id
                            val selected = state.document?.selectedProfileId == profile.id
                            val editDescription = stringResource(R.string.profile_edit_description, profile.displayName)
                            val deleteDescription = stringResource(R.string.profile_delete_description, profile.displayName)
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .testTag("select-${profile.id}")
                                                .selectable(
                                                    selected,
                                                    enabled =
                                                        state.activeProfileId == null && !uiState.isWorking,
                                                    role = Role.RadioButton,
                                                    onClick = { onSelect(profile.id) },
                                                ).padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            onClick = null,
                                            enabled =
                                                state.activeProfileId == null && !uiState.isWorking,
                                        )
                                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                            Text(profile.displayName, style = MaterialTheme.typography.titleMedium)
                                            Text(profile.config.networkName, style = MaterialTheme.typography.bodyMedium)
                                            if (active) {
                                                Text(stringResource(R.string.profile_active))
                                            } else if (selected) {
                                                Text(stringResource(R.string.profile_selected))
                                            }
                                            if (profile.config.validationError() !=
                                                null
                                            ) {
                                                Text(
                                                    stringResource(R.string.error_profile_invalid),
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(
                                            onClick = { onEdit(profile.id) },
                                            enabled = !uiState.isWorking,
                                            modifier =
                                                Modifier.semantics {
                                                    contentDescription =
                                                        editDescription
                                                },
                                        ) {
                                            Text(stringResource(R.string.profile_edit))
                                        }
                                        TextButton(
                                            onClick = { deletingId = profile.id },
                                            enabled = !active && !uiState.isWorking,
                                            modifier =
                                                Modifier.semantics {
                                                    contentDescription =
                                                        deleteDescription
                                                },
                                        ) {
                                            Text(stringResource(R.string.profile_delete))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(R.string.profile_delete)) },
            text = { Text(stringResource(R.string.profile_delete_confirmation, deleting.displayName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingId = null
                        onDelete(deleting.id)
                    },
                    enabled =
                        !uiState.isWorking && state.activeProfileId != deleting.id,
                ) {
                    Text(stringResource(R.string.profile_delete))
                }
            },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, widthDp = 840, heightDp = 600)
@Composable
private fun ProfilesPreview() {
    EasytierKTTheme {
        ProfilesScreen(
            uiState =
                ProfilesUiState(
                    ProfilesState(
                        isLoading = false,
                        document =
                            ProfileDocument(
                                profiles = listOf(SavedProfile("demo", "公司", ConnectionProfile(networkName = "office"))),
                                selectedProfileId = "demo",
                            ),
                    ),
                ),
            onBack = {},
            onCreate = {},
            onEdit = {},
            onSelect = {},
            onDelete = {},
            onRetry = {},
            showSaved = false,
            onSavedShown = {},
        )
    }
}

@Preview(showBackground = true, name = "Empty")
@Composable
private fun ProfilesEmptyPreview() = ProfilesStatePreview(ProfilesState(isLoading = false, document = ProfileDocument()))

@Preview(showBackground = true, name = "Read Error")
@Composable
private fun ProfilesReadErrorPreview() = ProfilesStatePreview(ProfilesState(isLoading = false, error = ProfileActionError.READ_FAILED))

@Preview(showBackground = true, name = "Loading")
@Composable
private fun ProfilesLoadingPreview() = ProfilesStatePreview(ProfilesState())

@Preview(showBackground = true, name = "Running Large Font", fontScale = 2f)
@Composable
private fun ProfilesRunningPreview() =
    ProfilesStatePreview(
        ProfilesState(
            isLoading = false,
            document =
                ProfileDocument(
                    profiles = listOf(SavedProfile("demo", "公司", ConnectionProfile(networkName = "office"))),
                    selectedProfileId = "demo",
                ),
            activeProfileId = "demo",
        ),
    )

@Composable
private fun ProfilesStatePreview(state: ProfilesState) {
    EasytierKTTheme {
        ProfilesScreen(
            uiState = ProfilesUiState(state),
            onBack = {},
            onCreate = {},
            onEdit = {},
            onSelect = {},
            onDelete = {},
            onRetry = {},
            showSaved = false,
            onSavedShown = {},
        )
    }
}
