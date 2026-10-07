package com.github.zzwtsy.easytierkt.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.zzwtsy.easytierkt.R
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.ProfileStorageError
import com.github.zzwtsy.easytierkt.data.profile.ProfileValidationError
import com.github.zzwtsy.easytierkt.ui.theme.EasytierKTTheme

@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onProfileChange: (ConnectionProfile) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile = uiState.profile
    val validationMessage = uiState.validationError?.let(::validationMessage)

    Scaffold(modifier = modifier.fillMaxSize()) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = onBack) {
                Text(text = stringResource(R.string.back_to_home))
            }
            Text(text = stringResource(R.string.settings_title), style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
            Text(text = stringResource(R.string.settings_description))

            if (uiState.isLoading) {
                Text(text = stringResource(R.string.settings_loading))
            } else {
                uiState.storageError?.let { error ->
                    Text(
                        text = stringResource(storageErrorMessage(error)),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    )
                }

                OutlinedTextField(
                    value = profile.networkName,
                    onValueChange = { onProfileChange(profile.copy(networkName = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_network_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = profile.networkSecret,
                    onValueChange = { onProfileChange(profile.copy(networkSecret = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_network_secret)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = profile.peerAddresses,
                    onValueChange = { onProfileChange(profile.copy(peerAddresses = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_peers)) },
                    supportingText = { Text(stringResource(R.string.settings_peers_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    minLines = 2,
                    maxLines = 5,
                )

                SettingSwitch(
                    label = stringResource(R.string.settings_use_dhcp),
                    checked = profile.useDhcp,
                    onCheckedChange = { onProfileChange(profile.copy(useDhcp = it)) },
                )
                if (!profile.useDhcp) {
                    OutlinedTextField(
                        value = profile.ipv4Address,
                        onValueChange = { onProfileChange(profile.copy(ipv4Address = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_static_ipv4)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = profile.routes,
                    onValueChange = { onProfileChange(profile.copy(routes = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_routes)) },
                    supportingText = { Text(stringResource(R.string.settings_routes_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    minLines = 2,
                    maxLines = 5,
                )
                SettingSwitch(
                    label = stringResource(R.string.settings_magic_dns),
                    checked = profile.enableMagicDns,
                    onCheckedChange = { onProfileChange(profile.copy(enableMagicDns = it)) },
                )

                Text(text = stringResource(R.string.settings_change_applies_next_connection))
                Text(text = stringResource(R.string.settings_license_notice))
                validationMessage?.let { message ->
                    Text(text = stringResource(message), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
                if (uiState.saved) {
                    Text(text = stringResource(R.string.settings_saved))
                }

                Button(
                    onClick = onSave,
                    enabled = !uiState.isSaving && validationMessage == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text =
                            stringResource(
                                if (uiState.isSaving) R.string.settings_saving else R.string.settings_save,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun validationMessage(error: ProfileValidationError): Int =
    when (error) {
        ProfileValidationError.INVALID_NETWORK_NAME -> R.string.error_invalid_network_name
        ProfileValidationError.INVALID_NETWORK_SECRET -> R.string.error_invalid_network_secret
        ProfileValidationError.INVALID_STATIC_ADDRESS -> R.string.error_invalid_static_address
        ProfileValidationError.INVALID_PEER_ADDRESS -> R.string.error_invalid_peer_address
        ProfileValidationError.INVALID_ROUTE -> R.string.error_invalid_route
    }

private fun storageErrorMessage(error: ProfileStorageError): Int =
    when (error) {
        ProfileStorageError.READ_FAILED -> R.string.error_profile_read
        ProfileStorageError.WRITE_FAILED -> R.string.error_profile_save
    }

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    EasytierKTTheme {
        SettingsScreen(
            uiState =
                SettingsUiState(
                    profile = ConnectionProfile(networkName = "demo-network"),
                    isLoading = false,
                ),
            onBack = {},
            onProfileChange = {},
            onSave = {},
        )
    }
}
