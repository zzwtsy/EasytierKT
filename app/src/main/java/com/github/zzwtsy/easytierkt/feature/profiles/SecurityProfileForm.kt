package com.github.zzwtsy.easytierkt.feature.profiles

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.zzwtsy.easytierkt.R
import com.github.zzwtsy.easytierkt.data.profile.AuthenticationMode
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.PeerKey
import com.github.zzwtsy.easytierkt.data.profile.ProfileField
import com.github.zzwtsy.easytierkt.data.profile.ProfileSection

@Composable
internal fun SecurityProfileForm(
    p: ConnectionProfile,
    enabled: Boolean,
    draft: ProfileDraft,
    issues: List<com.github.zzwtsy.easytierkt.data.profile.ProfileIssue>,
    focusedIssue: ProfileField?,
    onChange: (ConnectionProfile) -> Unit,
    onGenerateIdentity: () -> Unit,
    onImportIdentity: () -> Unit,
    identityBusy: Boolean,
    identityError: IdentityError?,
) {
    FormSection(stringResource(R.string.profile_section_security), reveal = focusedIssue?.section == ProfileSection.SECURITY) {
        FormChoice(stringResource(R.string.profile_authentication_mode), p.security.mode, AuthenticationMode.entries, enabled) {
            onChange(p.copy(security = p.security.copy(mode = it)))
        }
        if (p.credentialMode) {
            Text(stringResource(R.string.profile_credential_handshake))
        } else {
            ProfileToggle(enabled, stringResource(R.string.profile_secure_mode), p.security.secureMode) {
                onChange(p.copy(security = p.security.copy(secureMode = it)))
            }
        }
        ProfileTextField(
            state = draft.state("security.privateKey"),
            enabled = enabled,
            issue = issues.issueAt(draft.field("security.privateKey")),
            label = stringResource(R.string.profile_private_key),
            secret = true,
        )
        ProfileTextField(
            state = draft.state("security.publicKey"),
            enabled = enabled,
            issue = issues.issueAt(draft.field("security.publicKey")),
            label = stringResource(R.string.profile_public_key),
            readOnly = true,
        )
        Row {
            TextButton(onGenerateIdentity, enabled = enabled && !identityBusy) { Text(stringResource(R.string.profile_generate_identity)) }
            TextButton(
                onImportIdentity,
                enabled =
                    enabled && !identityBusy,
            ) { Text(stringResource(R.string.profile_import_identity)) }
        }
        if (identityBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        identityError?.let { Text(stringResource(it.messageResource()), color = MaterialTheme.colorScheme.error) }
        Text(stringResource(R.string.profile_peer_key_help))
        p.security.peerKeys.forEach { pin ->
            key(pin.id) {
                ProfileTextField(
                    state = draft.state("peerKey.${pin.id}.uri"),
                    enabled = enabled,
                    issue = issues.issueAt(draft.field("peerKey.${pin.id}.uri")),
                    label = stringResource(R.string.profile_peer_key_uri),
                )
                ProfileTextField(
                    state = draft.state("peerKey.${pin.id}.publicKey"),
                    enabled = enabled,
                    issue = issues.issueAt(draft.field("peerKey.${pin.id}.publicKey")),
                    label = stringResource(R.string.profile_peer_key_public),
                )
                TextButton({
                    onChange(
                        p.copy(
                            security =
                                p.security.copy(
                                    peerKeys =
                                        p.security.peerKeys.filterNot {
                                            it.id == pin.id
                                        },
                                ),
                        ),
                    )
                }, enabled = enabled) { Text(stringResource(R.string.profile_peer_key_remove)) }
            }
        }
        TextButton({
            onChange(p.copy(security = p.security.copy(peerKeys = p.security.peerKeys + PeerKey())))
        }, enabled = enabled) { Text(stringResource(R.string.profile_peer_key_add)) }
    }
}
