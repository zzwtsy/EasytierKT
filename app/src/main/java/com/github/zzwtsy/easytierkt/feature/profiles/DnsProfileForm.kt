package com.github.zzwtsy.easytierkt.feature.profiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.zzwtsy.easytierkt.R
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.DnsMode
import com.github.zzwtsy.easytierkt.data.profile.ProfileField
import com.github.zzwtsy.easytierkt.data.profile.ProfileSection

@Composable
internal fun DnsProfileForm(
    p: ConnectionProfile,
    enabled: Boolean,
    draft: ProfileDraft,
    issues: List<com.github.zzwtsy.easytierkt.data.profile.ProfileIssue>,
    focusedIssue: ProfileField?,
    onChange: (ConnectionProfile) -> Unit,
) {
    FormSection(stringResource(R.string.profile_section_dns), reveal = focusedIssue?.section == ProfileSection.DNS) {
        FormChoice(stringResource(R.string.profile_dns_mode), p.dns.mode, DnsMode.entries, enabled) {
            onChange(p.copy(dns = p.dns.copy(mode = it)))
        }
        ProfileTextField(
            state = draft.state("dns.servers"),
            enabled = enabled,
            issue = issues.issueAt(draft.field("dns.servers")),
            label = stringResource(R.string.profile_dns_servers),
        )
        ProfileTextField(
            state = draft.state("dns.zone"),
            enabled = enabled,
            issue = issues.issueAt(draft.field("dns.zone")),
            label = stringResource(R.string.profile_dns_zone),
        )
    }
}
