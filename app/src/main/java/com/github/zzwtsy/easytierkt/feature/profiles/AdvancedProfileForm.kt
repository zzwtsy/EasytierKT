package com.github.zzwtsy.easytierkt.feature.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.zzwtsy.easytierkt.R
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.InstalledApplication
import com.github.zzwtsy.easytierkt.data.profile.ProfileField
import com.github.zzwtsy.easytierkt.data.profile.ProfileSection

/** 分组表单只修改草稿；原始文本由条目 ViewModel 持有，启用字段的非法输入阻止保存。 */
@Composable
internal fun AdvancedProfileForm(
    p: ConnectionProfile,
    enabled: Boolean,
    draft: ProfileDraft,
    invalidNumbers: Set<ProfileField>,
    onChange: (ConnectionProfile) -> Unit,
    onGenerateIdentity: () -> Unit,
    onImportIdentity: () -> Unit,
    identityBusy: Boolean,
    identityError: IdentityError?,
    applications: List<InstalledApplication>,
) {
    val issues = p.issues()
    var focusedIssue by remember { mutableStateOf<ProfileField?>(null) }

    NodeProfileForm(enabled, draft, invalidNumbers, issues, focusedIssue)
    RoutingProfileForm(p, enabled, draft, issues, focusedIssue, onChange, applications)
    DnsProfileForm(p, enabled, draft, issues, focusedIssue, onChange)
    SecurityProfileForm(
        p,
        enabled,
        draft,
        issues,
        focusedIssue,
        onChange,
        onGenerateIdentity,
        onImportIdentity,
        identityBusy,
        identityError,
    )
    TransportProfileForm(p, enabled, draft, invalidNumbers, issues, focusedIssue, onChange)
    ProxyProfileForm(p, enabled, draft, invalidNumbers, issues, focusedIssue, onChange)
    AclProfileForm(p, enabled, draft, invalidNumbers, issues, focusedIssue, onChange)
    issues.forEach { issue ->
        TextButton({
            focusedIssue = issue.field
        }) {
            Text(
                stringResource(R.string.profile_labeled_value, issueSection(issue.field), stringResource(issue.code.messageResource())),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
internal fun FormSection(
    title: String,
    reveal: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(reveal) {
        if (reveal) {
            expanded = true
            withFrameNanos { }
            requester.bringIntoView()
        }
    }
    Card(Modifier.fillMaxWidth().bringIntoViewRequester(requester)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton({
                expanded = !expanded
            }) { Text(stringResource(if (expanded) R.string.profile_section_expanded else R.string.profile_section_collapsed, title)) }
            if (expanded) content()
        }
    }
}

@Composable
internal fun <T : Enum<T>> FormChoice(
    label: String,
    value: T,
    choices: List<T>,
    enabled: Boolean,
    onChange: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(
            { open = true },
            enabled = enabled,
        ) { Text(stringResource(R.string.profile_labeled_value, label, choiceLabel(value.name))) }
        DropdownMenu(open, { open = false }) {
            choices.forEach { option ->
                DropdownMenuItem(text = { Text(choiceLabel(option.name)) }, onClick = {
                    open = false
                    onChange(option)
                })
            }
        }
    }
}

@Composable
private fun choiceLabel(value: String): String =
    when (value) {
        "SHARED_SECRET" -> stringResource(R.string.profile_choice_shared_secret)
        "CREDENTIAL" -> stringResource(R.string.profile_choice_credential)
        "AUTOMATIC" -> stringResource(R.string.profile_choice_automatic)
        "MANUAL" -> stringResource(R.string.profile_choice_manual)
        "DIRECT_ONLY" -> stringResource(R.string.profile_choice_direct_only)
        "DISABLED" -> stringResource(R.string.profile_choice_disabled)
        "SYSTEM" -> stringResource(R.string.profile_choice_system_dns)
        "MAGIC" -> stringResource(R.string.profile_choice_magic_dns)
        "CUSTOM" -> stringResource(R.string.profile_choice_custom_dns)
        "ALL" -> stringResource(R.string.profile_choice_all_applications)
        "INCLUDE" -> stringResource(R.string.profile_choice_include_applications)
        "EXCLUDE" -> stringResource(R.string.profile_choice_exclude_applications)
        "INBOUND" -> stringResource(R.string.profile_choice_inbound)
        "OUTBOUND" -> stringResource(R.string.profile_choice_outbound)
        "FORWARD" -> stringResource(R.string.profile_choice_forward)
        "ALLOW" -> stringResource(R.string.profile_choice_allow)
        "DROP" -> stringResource(R.string.profile_choice_drop)
        "TCP" -> stringResource(R.string.profile_choice_tcp)
        "UDP" -> stringResource(R.string.profile_choice_udp)
        "ICMP" -> stringResource(R.string.profile_choice_icmp)
        "ICMPV6" -> stringResource(R.string.profile_choice_icmpv6)
        "ANY" -> stringResource(R.string.profile_choice_any)
        else -> error("Unsupported choice: $value")
    }

@Composable
internal fun issueSection(field: ProfileField): String =
    when (field.section) {
        ProfileSection.NETWORK -> stringResource(R.string.profile_section_network)
        ProfileSection.ROUTING -> stringResource(R.string.profile_section_routing)
        ProfileSection.SECURITY -> stringResource(R.string.profile_section_security)
        ProfileSection.DNS -> stringResource(R.string.profile_section_dns)
        ProfileSection.TRANSPORT -> stringResource(R.string.profile_section_transport)
        ProfileSection.PROXY -> stringResource(R.string.profile_section_proxy)
        ProfileSection.ACL -> stringResource(R.string.profile_section_acl)
    }

@Composable
internal fun ProfileToggle(
    enabled: Boolean,
    label: String,
    value: Boolean,
    change: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = value, enabled = enabled, role = Role.Switch, onValueChange = change),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(value, null, enabled = enabled)
    }
}
