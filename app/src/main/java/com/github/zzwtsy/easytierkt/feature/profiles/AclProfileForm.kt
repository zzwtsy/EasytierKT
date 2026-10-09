package com.github.zzwtsy.easytierkt.feature.profiles

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.res.stringResource
import com.github.zzwtsy.easytierkt.R
import com.github.zzwtsy.easytierkt.data.profile.AclAction
import com.github.zzwtsy.easytierkt.data.profile.AclChain
import com.github.zzwtsy.easytierkt.data.profile.AclChainType
import com.github.zzwtsy.easytierkt.data.profile.AclGroup
import com.github.zzwtsy.easytierkt.data.profile.AclProtocol
import com.github.zzwtsy.easytierkt.data.profile.AclRule
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.ProfileField
import com.github.zzwtsy.easytierkt.data.profile.ProfileSection

@Composable
internal fun AclProfileForm(
    p: ConnectionProfile,
    enabled: Boolean,
    draft: ProfileDraft,
    invalidNumbers: Set<ProfileField>,
    issues: List<com.github.zzwtsy.easytierkt.data.profile.ProfileIssue>,
    focusedIssue: ProfileField?,
    onChange: (ConnectionProfile) -> Unit,
) {
    FormSection(stringResource(R.string.profile_section_acl), reveal = focusedIssue?.section == ProfileSection.ACL) {
        ProfileToggle(
            enabled,
            stringResource(R.string.profile_acl_enabled),
            p.acl.enabled,
        ) { onChange(p.copy(acl = p.acl.copy(enabled = it))) }
        if (p.acl.enabled) {
            ProfileTextField(
                state = draft.state("acl.members"),
                enabled = enabled,
                issue = issues.issueAt(draft.field("acl.members")),
                label = stringResource(R.string.profile_acl_members),
            )
            p.acl.declares.forEach { g ->
                key(g.id) {
                    fun update(next: AclGroup) =
                        onChange(
                            p.copy(
                                acl =
                                    p.acl.copy(
                                        declares =
                                            p.acl.declares.map {
                                                if (it.id ==
                                                    g.id
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
                        state = draft.state("group.${g.id}.name"),
                        enabled = enabled,
                        issue = issues.issueAt(draft.field("group.${g.id}.name")),
                        label = stringResource(R.string.profile_acl_group_name),
                    )
                    ProfileTextField(
                        state = draft.state("group.${g.id}.secret"),
                        enabled = enabled,
                        issue = issues.issueAt(draft.field("group.${g.id}.secret")),
                        label = stringResource(R.string.profile_acl_group_secret),
                        secret = true,
                    )
                    TextButton(
                        { onChange(p.copy(acl = p.acl.copy(declares = p.acl.declares.filterNot { it.id == g.id }))) },
                        enabled = enabled,
                    ) { Text(stringResource(R.string.profile_acl_group_remove)) }
                }
            }
            TextButton({
                onChange(p.copy(acl = p.acl.copy(declares = p.acl.declares + AclGroup())))
            }, enabled = enabled) { Text(stringResource(R.string.profile_acl_group_add)) }
            p.acl.chains.forEach { chain ->
                key(chain.id) {
                    fun update(next: AclChain) =
                        onChange(
                            p.copy(
                                acl =
                                    p.acl.copy(
                                        chains =
                                            p.acl.chains.map {
                                                if (it.id ==
                                                    chain.id
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
                        state = draft.state("chain.${chain.id}.name"),
                        enabled = enabled,
                        issue = issues.issueAt(draft.field("chain.${chain.id}.name")),
                        label = stringResource(R.string.profile_acl_chain_name),
                    )
                    FormChoice(
                        stringResource(R.string.profile_acl_chain_type),
                        chain.type,
                        AclChainType.entries,
                        enabled,
                    ) { update(chain.copy(type = it)) }
                    FormChoice(stringResource(R.string.profile_acl_default_action), chain.defaultAction, AclAction.entries, enabled) {
                        update(chain.copy(defaultAction = it))
                    }
                    ProfileToggle(
                        enabled,
                        stringResource(R.string.profile_acl_chain_enabled),
                        chain.enabled,
                    ) { update(chain.copy(enabled = it)) }
                    chain.rules.forEach { rule ->
                        key(rule.id) {
                            fun updateRule(next: AclRule) =
                                update(
                                    chain.copy(
                                        rules =
                                            chain.rules.map {
                                                if (it.id ==
                                                    rule.id
                                                ) {
                                                    next
                                                } else {
                                                    it
                                                }
                                            },
                                    ),
                                )
                            ProfileTextField(
                                state = draft.state("rule.${rule.id}.name"),
                                enabled = enabled,
                                issue = issues.issueAt(draft.field("rule.${rule.id}.name")),
                                label = stringResource(R.string.profile_acl_rule_name),
                            )
                            ProfileTextField(
                                state = draft.state("rule.${rule.id}.description"),
                                enabled = enabled,
                                issue = issues.issueAt(draft.field("rule.${rule.id}.description")),
                                label = stringResource(R.string.profile_acl_rule_description),
                            )
                            ProfileTextField(
                                state = draft.state("rule.${rule.id}.sourceIps"),
                                enabled = enabled,
                                issue = issues.issueAt(draft.field("rule.${rule.id}.sourceIps")),
                                label = stringResource(R.string.profile_acl_source_ips),
                            )
                            ProfileTextField(
                                state = draft.state("rule.${rule.id}.destinationIps"),
                                enabled = enabled,
                                issue = issues.issueAt(draft.field("rule.${rule.id}.destinationIps")),
                                label = stringResource(R.string.profile_acl_destination_ips),
                            )
                            ProfileTextField(
                                state = draft.state("rule.${rule.id}.sourcePorts"),
                                enabled = enabled,
                                issue = issues.issueAt(draft.field("rule.${rule.id}.sourcePorts")),
                                label = stringResource(R.string.profile_acl_source_ports),
                            )
                            ProfileTextField(
                                state = draft.state("rule.${rule.id}.destinationPorts"),
                                enabled = enabled,
                                issue = issues.issueAt(draft.field("rule.${rule.id}.destinationPorts")),
                                label = stringResource(R.string.profile_acl_destination_ports),
                            )
                            ProfileTextField(
                                state = draft.state("rule.${rule.id}.sourceGroups"),
                                enabled = enabled,
                                issue = issues.issueAt(draft.field("rule.${rule.id}.sourceGroups")),
                                label = stringResource(R.string.profile_acl_source_groups),
                            )
                            ProfileTextField(
                                state = draft.state("rule.${rule.id}.destinationGroups"),
                                enabled = enabled,
                                issue = issues.issueAt(draft.field("rule.${rule.id}.destinationGroups")),
                                label = stringResource(R.string.profile_acl_destination_groups),
                            )
                            ProfileNumberField(
                                state = draft.state("rule.${rule.id}.priority"),
                                invalid =
                                    draft.field("rule.${rule.id}.priority") in invalidNumbers,
                                enabled = enabled,
                                issue = issues.issueAt(draft.field("rule.${rule.id}.priority")),
                                label = stringResource(R.string.profile_acl_priority),
                            )
                            ProfileNumberField(
                                state = draft.state("rule.${rule.id}.rateLimit"),
                                invalid =
                                    draft.field("rule.${rule.id}.rateLimit") in invalidNumbers,
                                enabled = enabled,
                                issue = issues.issueAt(draft.field("rule.${rule.id}.rateLimit")),
                                label = stringResource(R.string.profile_acl_rate_limit),
                            )
                            ProfileNumberField(
                                state = draft.state("rule.${rule.id}.burstLimit"),
                                invalid =
                                    draft.field("rule.${rule.id}.burstLimit") in invalidNumbers,
                                enabled = enabled,
                                issue = issues.issueAt(draft.field("rule.${rule.id}.burstLimit")),
                                label = stringResource(R.string.profile_acl_burst_limit),
                            )
                            FormChoice(stringResource(R.string.profile_acl_protocol), rule.protocol, AclProtocol.entries, enabled) {
                                updateRule(rule.copy(protocol = it))
                            }
                            FormChoice(
                                stringResource(R.string.profile_acl_action),
                                rule.action,
                                AclAction.entries,
                                enabled,
                            ) { updateRule(rule.copy(action = it)) }
                            ProfileToggle(
                                enabled,
                                stringResource(R.string.profile_acl_rule_enabled),
                                rule.enabled,
                            ) { updateRule(rule.copy(enabled = it)) }
                            ProfileToggle(
                                enabled,
                                stringResource(R.string.profile_acl_stateful),
                                rule.stateful,
                            ) { updateRule(rule.copy(stateful = it)) }
                            TextButton(
                                { update(chain.copy(rules = chain.rules.filterNot { it.id == rule.id })) },
                                enabled = enabled,
                            ) { Text(stringResource(R.string.profile_acl_rule_remove)) }
                        }
                    }
                    TextButton({
                        update(
                            chain.copy(
                                rules =
                                    chain.rules +
                                        AclRule(priority = ((chain.rules.maxOfOrNull { it.priority } ?: 99) + 1).coerceAtMost(65535)),
                            ),
                        )
                    }, enabled = enabled) { Text(stringResource(R.string.profile_acl_rule_add)) }
                    TextButton(
                        { onChange(p.copy(acl = p.acl.copy(chains = p.acl.chains.filterNot { it.id == chain.id }))) },
                        enabled = enabled,
                    ) { Text(stringResource(R.string.profile_acl_chain_remove)) }
                }
            }
            TextButton(
                { onChange(p.copy(acl = p.acl.copy(chains = p.acl.chains + AclChain()))) },
                enabled =
                    enabled && p.acl.chains.size < 3,
            ) { Text(stringResource(R.string.profile_acl_chain_add)) }
        }
    }
}
