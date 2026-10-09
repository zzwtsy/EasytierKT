package com.github.zzwtsy.easytierkt.feature.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.github.zzwtsy.easytierkt.R
import com.github.zzwtsy.easytierkt.data.profile.ApplicationMode
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.InstalledApplication
import com.github.zzwtsy.easytierkt.data.profile.ProfileField
import com.github.zzwtsy.easytierkt.data.profile.ProfileSection
import com.github.zzwtsy.easytierkt.data.profile.RouteMode

@Composable
internal fun RoutingProfileForm(
    p: ConnectionProfile,
    enabled: Boolean,
    draft: ProfileDraft,
    issues: List<com.github.zzwtsy.easytierkt.data.profile.ProfileIssue>,
    focusedIssue: ProfileField?,
    onChange: (ConnectionProfile) -> Unit,
    applications: List<InstalledApplication>,
) {
    FormSection(stringResource(R.string.profile_section_routing), reveal = focusedIssue?.section == ProfileSection.ROUTING) {
        FormChoice(stringResource(R.string.profile_route_mode), p.routing.mode, RouteMode.entries, enabled) {
            onChange(p.copy(routing = p.routing.copy(mode = it)))
        }
        ProfileTextField(
            state = draft.state("routing.ipv6Routes"),
            enabled = enabled,
            issue = issues.issueAt(draft.field("routing.ipv6Routes")),
            label = stringResource(R.string.profile_ipv6_routes),
        )
        ProfileTextField(
            state = draft.state("routing.exitNodes"),
            enabled = enabled,
            issue = issues.issueAt(draft.field("routing.exitNodes")),
            label = stringResource(R.string.profile_exit_nodes),
        )
        ProfileToggle(enabled, stringResource(R.string.profile_ipv6_bypass), p.routing.ipv6InternetBypass) {
            onChange(p.copy(routing = p.routing.copy(ipv6InternetBypass = it)))
        }
        FormChoice(stringResource(R.string.profile_application_mode), p.routing.applicationMode, ApplicationMode.entries, enabled) {
            onChange(p.copy(routing = p.routing.copy(applicationMode = it)))
        }
        if (p.routing.applicationMode != ApplicationMode.ALL) {
            val query = draft.applicationSearch.text.toString()
            ProfileTextField(
                state = draft.applicationSearch,
                enabled = enabled,
                issue = null,
                label = stringResource(R.string.profile_application_search),
            )
            val selected = p.routing.applications
            val known = applications.map { it.packageName }.toSet()
            selected.filter { it !in known }.forEach { name ->
                TextButton(onClick = {
                    onChange(p.copy(routing = p.routing.copy(applications = selected - name)))
                }, enabled = enabled) { Text(stringResource(R.string.profile_application_remove, name)) }
            }
            applications
                .filter {
                    query.isNotBlank() &&
                        (it.label.contains(query, true) || it.packageName.contains(query, true)) ||
                        it.packageName in selected
                }.take(50)
                .forEach { app ->
                    key(app.packageName) {
                        Row(
                            Modifier.fillMaxWidth().toggleable(
                                value = app.packageName in selected,
                                enabled = enabled,
                                role = Role.Checkbox,
                                onValueChange = { checked ->
                                    onChange(
                                        p.copy(
                                            routing =
                                                p.routing.copy(
                                                    applications =
                                                        if (checked) {
                                                            selected +
                                                                app.packageName
                                                        } else {
                                                            selected - app.packageName
                                                        },
                                                ),
                                        ),
                                    )
                                },
                            ),
                        ) {
                            Checkbox(app.packageName in selected, null, enabled = enabled)
                            Column(Modifier.weight(1f)) {
                                Text(app.label)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            Text(stringResource(R.string.profile_application_help))
        }
    }
}
