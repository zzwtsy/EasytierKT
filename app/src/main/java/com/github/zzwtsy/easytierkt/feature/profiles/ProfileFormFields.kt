package com.github.zzwtsy.easytierkt.feature.profiles

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.zzwtsy.easytierkt.R
import com.github.zzwtsy.easytierkt.data.profile.EntryProperty
import com.github.zzwtsy.easytierkt.data.profile.ProfileField
import com.github.zzwtsy.easytierkt.data.profile.ProfileIssue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileTextField(
    state: TextFieldState,
    enabled: Boolean,
    issue: ProfileIssue?,
    label: String,
    secret: Boolean = false,
    readOnly: Boolean = false,
) {
    val supportingText: @Composable () -> Unit = { if (issue != null) Text(stringResource(issue.code.messageResource())) }
    if (secret) {
        OutlinedSecureTextField(
            state = state,
            label = { Text(label) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            isError = issue != null,
            supportingText = supportingText,
        )
    } else {
        OutlinedTextField(
            state = state,
            readOnly = readOnly,
            label = { Text(label) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            isError = issue != null,
            supportingText = supportingText,
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
        )
    }
}

@Composable
internal fun ProfileNumberField(
    state: TextFieldState,
    enabled: Boolean,
    issue: ProfileIssue?,
    label: String,
    invalid: Boolean,
) {
    OutlinedTextField(
        state = state,
        label = { Text(label) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        lineLimits = TextFieldLineLimits.SingleLine,
        isError = invalid || issue != null,
        supportingText = {
            if (invalid) {
                Text(stringResource(R.string.profile_issue_number))
            } else if (issue != null) {
                Text(stringResource(issue.code.messageResource()))
            }
        },
    )
}

/** 类型化字段精确定位；条目级错误同时作用于该条目的输入框。 */
internal fun List<ProfileIssue>.issueAt(field: ProfileField): ProfileIssue? =
    firstOrNull { issue ->
        issue.field == field ||
            (
                issue.field is ProfileField.Entry &&
                    field is ProfileField.Entry &&
                    issue.field.kind == field.kind &&
                    issue.field.id == field.id &&
                    issue.field.chainId == field.chainId &&
                    issue.field.property == EntryProperty.VALUE
            )
    }
