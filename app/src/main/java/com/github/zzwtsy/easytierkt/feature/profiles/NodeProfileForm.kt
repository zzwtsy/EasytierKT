package com.github.zzwtsy.easytierkt.feature.profiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.zzwtsy.easytierkt.R
import com.github.zzwtsy.easytierkt.data.profile.ProfileField
import com.github.zzwtsy.easytierkt.data.profile.ProfileSection

@Composable
internal fun NodeProfileForm(
    enabled: Boolean,
    draft: ProfileDraft,
    invalidNumbers: Set<ProfileField>,
    issues: List<com.github.zzwtsy.easytierkt.data.profile.ProfileIssue>,
    focusedIssue: ProfileField?,
) {
    FormSection(stringResource(R.string.profile_section_network), reveal = focusedIssue?.section == ProfileSection.NETWORK) {
        ProfileTextField(
            state = draft.state("hostname"),
            enabled = enabled,
            issue = issues.issueAt(draft.field("hostname")),
            label = stringResource(R.string.profile_node_name),
        )
        ProfileNumberField(
            state = draft.state("ipv4Prefix"),
            invalid = draft.field("ipv4Prefix") in invalidNumbers,
            enabled = enabled,
            issue = issues.issueAt(draft.field("ipv4Prefix")),
            label = stringResource(R.string.profile_ipv4_prefix),
        )
        ProfileTextField(
            state = draft.state("virtualIpv6"),
            enabled = enabled,
            issue = issues.issueAt(draft.field("virtualIpv6")),
            label = stringResource(R.string.profile_ipv6_address),
        )
        ProfileNumberField(
            state = draft.state("ipv6Prefix"),
            invalid = draft.field("ipv6Prefix") in invalidNumbers,
            enabled = enabled,
            issue = issues.issueAt(draft.field("ipv6Prefix")),
            label = stringResource(R.string.profile_ipv6_prefix),
        )
        ProfileTextField(
            state = draft.state("listeners"),
            enabled = enabled,
            issue = issues.issueAt(draft.field("listeners")),
            label = stringResource(R.string.profile_listeners),
        )
        ProfileTextField(
            state = draft.state("mappedListeners"),
            enabled = enabled,
            issue = issues.issueAt(draft.field("mappedListeners")),
            label = stringResource(R.string.profile_mapped_listeners),
        )
    }
}
