package com.github.zzwtsy.easytierkt.feature.profiles

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.res.stringResource
import com.github.zzwtsy.easytierkt.R
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.PortForward
import com.github.zzwtsy.easytierkt.data.profile.ProfileField
import com.github.zzwtsy.easytierkt.data.profile.ProfileSection

@Composable
internal fun ProxyProfileForm(
    p: ConnectionProfile,
    enabled: Boolean,
    draft: ProfileDraft,
    invalidNumbers: Set<ProfileField>,
    issues: List<com.github.zzwtsy.easytierkt.data.profile.ProfileIssue>,
    focusedIssue: ProfileField?,
    onChange: (ConnectionProfile) -> Unit,
) {
    FormSection(stringResource(R.string.profile_section_proxy), reveal = focusedIssue?.section == ProfileSection.PROXY) {
        ProfileToggle(enabled, stringResource(R.string.profile_socks5), p.localProxy.socks5) {
            onChange(p.copy(localProxy = p.localProxy.copy(socks5 = it)))
        }
        ProfileTextField(
            state = draft.state("localProxy.bindAddress"),
            enabled = enabled,
            issue = issues.issueAt(draft.field("localProxy.bindAddress")),
            label = stringResource(R.string.profile_socks5_address),
        )
        ProfileNumberField(
            state = draft.state("localProxy.port"),
            invalid = draft.field("localProxy.port") in invalidNumbers,
            enabled = enabled,
            issue = issues.issueAt(draft.field("localProxy.port")),
            label = stringResource(R.string.profile_socks5_port),
        )
        p.localProxy.forwards.forEach { f ->
            key(f.id) {
                fun update(next: PortForward) =
                    onChange(
                        p.copy(
                            localProxy =
                                p.localProxy.copy(
                                    forwards =
                                        p.localProxy.forwards.map {
                                            if (it.id ==
                                                f.id
                                            ) {
                                                next
                                            } else {
                                                it
                                            }
                                        },
                                ),
                        ),
                    )
                ProfileTextField(
                    state = draft.state("forward.${f.id}.protocol"),
                    enabled = enabled,
                    issue = issues.issueAt(draft.field("forward.${f.id}.protocol")),
                    label = stringResource(R.string.profile_forward_protocol),
                )
                ProfileTextField(
                    state = draft.state("forward.${f.id}.bindAddress"),
                    enabled = enabled,
                    issue = issues.issueAt(draft.field("forward.${f.id}.bindAddress")),
                    label = stringResource(R.string.profile_forward_address),
                )
                ProfileNumberField(
                    state = draft.state("forward.${f.id}.bindPort"),
                    invalid =
                        draft.field("forward.${f.id}.bindPort") in invalidNumbers,
                    enabled = enabled,
                    issue = issues.issueAt(draft.field("forward.${f.id}.bindPort")),
                    label = stringResource(R.string.profile_forward_port),
                )
                ProfileTextField(
                    state = draft.state("forward.${f.id}.destination"),
                    enabled = enabled,
                    issue = issues.issueAt(draft.field("forward.${f.id}.destination")),
                    label = stringResource(R.string.profile_forward_destination),
                )
                ProfileNumberField(
                    state = draft.state("forward.${f.id}.destinationPort"),
                    invalid =
                        draft.field("forward.${f.id}.destinationPort") in invalidNumbers,
                    enabled = enabled,
                    issue = issues.issueAt(draft.field("forward.${f.id}.destinationPort")),
                    label = stringResource(R.string.profile_forward_destination_port),
                )
                TextButton({
                    onChange(
                        p.copy(
                            localProxy =
                                p.localProxy.copy(
                                    forwards =
                                        p.localProxy.forwards.filterNot {
                                            it.id == f.id
                                        },
                                ),
                        ),
                    )
                }, enabled = enabled) { Text(stringResource(R.string.profile_forward_remove)) }
            }
        }
        TextButton({
            onChange(p.copy(localProxy = p.localProxy.copy(forwards = p.localProxy.forwards + PortForward())))
        }, enabled = enabled) { Text(stringResource(R.string.profile_forward_add)) }
    }
}
