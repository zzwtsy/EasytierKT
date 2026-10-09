package com.github.zzwtsy.easytierkt.feature.profiles

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.zzwtsy.easytierkt.R
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.ProfileActionError
import com.github.zzwtsy.easytierkt.data.profile.ProfileValidationError
import com.github.zzwtsy.easytierkt.data.profile.isProfileNameValid
import com.github.zzwtsy.easytierkt.ui.icons.ArrowBackIcon
import com.github.zzwtsy.easytierkt.ui.icons.ErrorIcon
import com.github.zzwtsy.easytierkt.ui.icons.VisibilityIcon
import com.github.zzwtsy.easytierkt.ui.icons.VisibilityOffIcon
import com.github.zzwtsy.easytierkt.ui.theme.EasytierKTTheme

/** 大屏下表单的最大宽度，避免输入框在平板/折叠屏上全宽拉伸。 */
private val CONTENT_MAX_WIDTH = 600.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ProfileEditorScreen(
    uiState: ProfileEditorUiState,
    onBack: () -> Unit,
    onProfileChange: (ConnectionProfile) -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    draft: ProfileDraft? = null,
    onGenerateIdentity: () -> Unit = {},
    onImportIdentity: () -> Unit = {},
    applications: List<com.github.zzwtsy.easytierkt.data.profile.InstalledApplication> = emptyList(),
    onRetryApplications: () -> Unit = {},
) {
    val fields = draft ?: androidx.compose.runtime.remember { ProfileDraft(uiState.profile, uiState.displayName) }
    val profile = uiState.profile
    val validationError = uiState.validationError
    val formEnabled =
        !uiState.isSaving &&
            !uiState.identityBusy &&
            uiState.error != ProfileActionError.READ_FAILED &&
            uiState.error != ProfileActionError.NOT_FOUND

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(text = stringResource(R.string.profile_editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = ArrowBackIcon,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            SaveBottomBar(
                isSaving = uiState.isSaving,
                enabled =
                    !uiState.isSaving &&
                        !uiState.isLoading &&
                        profile.validationError() == null &&
                        uiState.invalidNumbers.isEmpty() &&
                        !uiState.identityBusy &&
                        isProfileNameValid(uiState.displayName) &&
                        uiState.error != ProfileActionError.READ_FAILED &&
                        uiState.error != ProfileActionError.NOT_FOUND,
                onSave = onSave,
            )
        },
    ) { contentPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .consumeWindowInsets(contentPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = CONTENT_MAX_WIDTH)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (uiState.isLoading) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator()
                    }
                } else {
                    uiState.error?.let { error ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = ErrorIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = stringResource(profileErrorMessage(error)),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    if (uiState.error == ProfileActionError.READ_FAILED) {
                        androidx.compose.material3.TextButton(onClick = onRetry) {
                            Text(stringResource(R.string.profiles_retry))
                        }
                    }
                    OutlinedTextField(
                        state = fields.state("displayName"),
                        modifier = Modifier.fillMaxWidth(),
                        enabled =
                            !uiState.isSaving &&
                                uiState.error != ProfileActionError.READ_FAILED &&
                                uiState.error != ProfileActionError.NOT_FOUND,
                        label = { Text(stringResource(R.string.profile_name)) },
                        lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
                        isError = uiState.nameError,
                        supportingText =
                            if (uiState.nameError) {
                                { Text(stringResource(R.string.error_invalid_profile_name)) }
                            } else {
                                null
                            },
                    )

                    SettingsGroup(title = stringResource(R.string.settings_section_credentials)) {
                        OutlinedTextField(
                            state = fields.state("networkName"),
                            enabled = formEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.settings_network_name)) },
                            supportingText =
                                validationSupportingText(validationError, ProfileValidationError.INVALID_NETWORK_NAME),
                            isError = validationError == ProfileValidationError.INVALID_NETWORK_NAME,
                            lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
                        )
                        SecretTextField(
                            state = fields.state("networkSecret"),
                            enabled = formEnabled && !profile.credentialMode,
                            isError = validationError == ProfileValidationError.INVALID_NETWORK_SECRET,
                            supportingText =
                                validationSupportingText(validationError, ProfileValidationError.INVALID_NETWORK_SECRET),
                        )
                    }

                    SettingsGroup(title = stringResource(R.string.settings_section_network)) {
                        OutlinedTextField(
                            state = fields.state("peerAddresses"),
                            enabled = formEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.settings_peers)) },
                            supportingText =
                                validationSupportingText(validationError, ProfileValidationError.INVALID_PEER_ADDRESS) ?: {
                                    Text(stringResource(R.string.settings_peers_hint))
                                },
                            isError = validationError == ProfileValidationError.INVALID_PEER_ADDRESS,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            lineLimits =
                                androidx.compose.foundation.text.input.TextFieldLineLimits.MultiLine(
                                    minHeightInLines = 2,
                                    maxHeightInLines = 5,
                                ),
                        )
                        SettingSwitch(
                            label = stringResource(R.string.settings_use_dhcp),
                            checked = profile.useDhcp,
                            enabled = formEnabled,
                            onCheckedChange = { onProfileChange(profile.copy(useDhcp = it)) },
                        )
                        AnimatedVisibility(visible = !profile.useDhcp) {
                            OutlinedTextField(
                                state = fields.state("ipv4Address"),
                                enabled = formEnabled,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.settings_static_ipv4)) },
                                supportingText =
                                    validationSupportingText(
                                        validationError,
                                        ProfileValidationError.INVALID_STATIC_ADDRESS,
                                    ),
                                isError = validationError == ProfileValidationError.INVALID_STATIC_ADDRESS,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
                            )
                        }
                        OutlinedTextField(
                            state = fields.state("routes"),
                            enabled = formEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.settings_routes)) },
                            supportingText =
                                validationSupportingText(validationError, ProfileValidationError.INVALID_ROUTE) ?: {
                                    Text(stringResource(R.string.settings_routes_hint))
                                },
                            isError = validationError == ProfileValidationError.INVALID_ROUTE,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            lineLimits =
                                androidx.compose.foundation.text.input.TextFieldLineLimits.MultiLine(
                                    minHeightInLines = 2,
                                    maxHeightInLines = 5,
                                ),
                        )
                    }

                    if (uiState.applicationsLoading) Text(stringResource(R.string.profile_applications_loading))
                    if (uiState.applicationsFailed) {
                        androidx.compose.material3.TextButton(onRetryApplications) {
                            Text(stringResource(R.string.profile_applications_retry))
                        }
                    }
                    uiState.identityError?.let { Text(stringResource(it.messageResource()), color = MaterialTheme.colorScheme.error) }
                    AdvancedProfileForm(
                        profile,
                        formEnabled,
                        fields,
                        uiState.invalidNumbers,
                        onProfileChange,
                        onGenerateIdentity,
                        onImportIdentity,
                        uiState.identityBusy,
                        uiState.identityError,
                        applications,
                    )

                    Text(
                        text =
                            stringResource(
                                if (uiState.isRunning) {
                                    R.string.profile_running_edit_hint
                                } else {
                                    R.string.settings_change_applies_next_connection
                                },
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.settings_license_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 底部固定的保存主操作栏，不随内容滚动；键盘弹出时随 IME 上移。 */
@Composable
private fun SaveBottomBar(
    isSaving: Boolean,
    enabled: Boolean,
    onSave: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Button(
                onClick = onSave,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text =
                        stringResource(
                            if (isSaving) R.string.settings_saving else R.string.settings_save,
                        ),
                )
            }
        }
    }
}

/** 表单分组：Expressive 强调组标题 + 大圆角卡片容器。 */
@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                content()
            }
        }
    }
}

/** 密钥使用安全输入 API，避免明文复制与草稿自动持久化。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecretTextField(
    state: androidx.compose.foundation.text.input.TextFieldState,
    isError: Boolean,
    enabled: Boolean,
    supportingText: (@Composable () -> Unit)?,
) {
    var visible by remember { mutableStateOf(false) }
    androidx.compose.material3.OutlinedSecureTextField(
        textObfuscationMode =
            if (visible) {
                androidx.compose.foundation.text.input.TextObfuscationMode.Visible
            } else {
                androidx.compose.foundation.text.input.TextObfuscationMode.RevealLastTyped
            },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) VisibilityOffIcon else VisibilityIcon,
                    contentDescription = stringResource(if (visible) R.string.settings_hide_secret else R.string.settings_show_secret),
                )
            }
        },
        state = state,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.settings_network_secret)) },
        supportingText = supportingText,
        isError = isError,
    )
}

/** 整行可点（含标签文字）的开关设置项，语义角色为 Switch，保证 48dp 触控目标。 */
@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                ).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        // 交互由整行的 toggleable 处理，Switch 置空回调避免 TalkBack 重复播报操作。
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** 校验错误命中 [field] 时返回该字段的错误说明，否则返回 null 让字段显示常规提示。 */
@Composable
private fun validationSupportingText(
    validationError: ProfileValidationError?,
    field: ProfileValidationError,
): (@Composable () -> Unit)? =
    if (validationError == field) {
        { Text(text = stringResource(validationMessage(field))) }
    } else {
        null
    }

private fun validationMessage(error: ProfileValidationError): Int =
    when (error) {
        ProfileValidationError.INVALID_NETWORK_NAME -> R.string.error_invalid_network_name
        ProfileValidationError.INVALID_NETWORK_SECRET -> R.string.error_invalid_network_secret
        ProfileValidationError.INVALID_STATIC_ADDRESS -> R.string.error_invalid_static_address
        ProfileValidationError.INVALID_PEER_ADDRESS -> R.string.error_invalid_peer_address
        ProfileValidationError.INVALID_ADVANCED -> R.string.error_profile_invalid
        ProfileValidationError.INVALID_ROUTE -> R.string.error_invalid_route
    }

@Preview(showBackground = true, name = "Phone")
@Preview(showBackground = true, name = "Phone Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, name = "Tablet", device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun ProfileEditorScreenPreview() {
    EasytierKTTheme {
        ProfileEditorScreen(
            uiState =
                ProfileEditorUiState(
                    profile = ConnectionProfile(networkName = "demo-network"),
                    displayName = "演示配置",
                    isLoading = false,
                ),
            onBack = {},
            onProfileChange = {},
            onSave = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, name = "Validation Error")
@Composable
private fun ProfileEditorScreenErrorPreview() {
    EasytierKTTheme {
        ProfileEditorScreen(
            uiState =
                ProfileEditorUiState(
                    profile = ConnectionProfile(useDhcp = false, ipv4Address = "999.0.0.1"),
                    isLoading = false,
                    validationError = ProfileValidationError.INVALID_STATIC_ADDRESS,
                ),
            onBack = {},
            onProfileChange = {},
            onSave = {},
            onRetry = {},
        )
    }
}
