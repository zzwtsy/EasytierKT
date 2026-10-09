package com.github.zzwtsy.easytierkt.feature.connection

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.zzwtsy.easytierkt.R
import com.github.zzwtsy.easytierkt.data.connection.ConnectionError
import com.github.zzwtsy.easytierkt.data.connection.ConnectionPhase
import com.github.zzwtsy.easytierkt.data.connection.ConnectionStatus
import com.github.zzwtsy.easytierkt.data.profile.ProfileActionError
import com.github.zzwtsy.easytierkt.ui.icons.ErrorIcon
import com.github.zzwtsy.easytierkt.ui.icons.InfoIcon
import com.github.zzwtsy.easytierkt.ui.icons.LanIcon
import com.github.zzwtsy.easytierkt.ui.icons.MemoryIcon
import com.github.zzwtsy.easytierkt.ui.icons.PowerIcon
import com.github.zzwtsy.easytierkt.ui.icons.RouterIcon
import com.github.zzwtsy.easytierkt.ui.icons.SettingsIcon
import com.github.zzwtsy.easytierkt.ui.icons.VpnKeyIcon
import com.github.zzwtsy.easytierkt.ui.theme.EasytierKTTheme
import com.github.zzwtsy.easytierkt.ui.theme.extendedColors
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private val HERO_RING_SIZE = 192.dp
private val HERO_BUTTON_SIZE = 144.dp
private val HERO_ICON_SIZE = 56.dp
private val HERO_CIRCLE_CORNER = 72.dp
private val HERO_CONNECTED_CORNER = 32.dp
private val DETAIL_MAX_WIDTH = 480.dp
private const val WIDE_LAYOUT_MIN_WIDTH_DP = 840
private const val ELAPSED_TICK_INTERVAL_MS = 30_000L

@Composable
internal fun ConnectionScreen(
    modifier: Modifier = Modifier,
    uiState: ConnectionUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetryProfiles: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.connection_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings, enabled = !uiState.awaitingPermission) {
                        Icon(
                            imageVector = SettingsIcon,
                            contentDescription = stringResource(R.string.open_settings),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        val isWide = LocalConfiguration.current.screenWidthDp >= WIDE_LAYOUT_MIN_WIDTH_DP
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
        ) {
            if (isWide) {
                // 两栏独立滚动；短内容在可用高度内居中，超高内容从顶部开始完整展示。
                Row(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        HeroSection(uiState = uiState, onConnect = onConnect, onDisconnect = onDisconnect)
                    }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        DetailSection(
                            uiState = uiState,
                            onConnect = onConnect,
                            onOpenSettings = onOpenSettings,
                            onRetryProfiles = onRetryProfiles,
                        )
                    }
                }
            } else {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HeroSection(uiState = uiState, onConnect = onConnect, onDisconnect = onDisconnect)
                    Spacer(modifier = Modifier.height(32.dp))
                    DetailSection(
                        uiState = uiState,
                        onConnect = onConnect,
                        onOpenSettings = onOpenSettings,
                        onRetryProfiles = onRetryProfiles,
                    )
                }
            }
        }
    }
}

/** 页面主角：大号圆形连接开关 + 状态主副文案。连接/断开是主页唯一的主动作。 */
@Composable
private fun HeroSection(
    uiState: ConnectionUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val phase = uiState.status.phase
    val canDisconnect = phase == ConnectionPhase.STARTING || phase == ConnectionPhase.CONNECTED
    val motionScheme = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current

    val heroCorner by animateDpAsState(
        targetValue = if (phase == ConnectionPhase.CONNECTED) HERO_CONNECTED_CORNER else HERO_CIRCLE_CORNER,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "heroShape",
    )
    val titleColor by animateColorAsState(
        targetValue = statusColor(phase),
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "heroTitleColor",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(HERO_RING_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            if (phase == ConnectionPhase.STARTING) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            val actionDescription =
                stringResource(
                    when {
                        phase == ConnectionPhase.STARTING -> R.string.cd_hero_cancel
                        canDisconnect -> R.string.cd_hero_disconnect
                        else -> R.string.cd_hero_connect
                    },
                )
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (canDisconnect) onDisconnect() else onConnect()
                },
                enabled = canDisconnect || uiState.canConnect,
                shape = RoundedCornerShape(heroCorner),
                color = heroContainerColor(phase),
                contentColor = heroContentColor(phase),
                modifier =
                    Modifier
                        .size(HERO_BUTTON_SIZE)
                        .semantics { contentDescription = actionDescription },
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = PowerIcon,
                        contentDescription = null,
                        modifier = Modifier.size(HERO_ICON_SIZE),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                fadeIn(motionScheme.defaultEffectsSpec()) togetherWith fadeOut(motionScheme.defaultEffectsSpec())
            },
            label = "heroStatusTitle",
        ) { targetPhase ->
            Text(
                text = stringResource(statusTitle(targetPhase)),
                style = MaterialTheme.typography.headlineLargeEmphasized,
                color = titleColor,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = heroSubtitle(uiState),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 详情区：按状态分别展示状态卡片、空态引导卡片或错误卡片。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DetailSection(
    uiState: ConnectionUiState,
    onConnect: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetryProfiles: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .widthIn(max = DETAIL_MAX_WIDTH)
                .fillMaxWidth(),
    ) {
        val profileName = uiState.status.profileName ?: uiState.profiles.selectedProfile?.displayName
        TextButton(onClick = onOpenSettings, enabled = !uiState.awaitingPermission) {
            Text(
                if (profileName ==
                    null
                ) {
                    stringResource(R.string.profiles_choose)
                } else {
                    stringResource(R.string.connection_selected_profile, profileName)
                },
            )
        }
        if (uiState.status.phase != ConnectionPhase.ERROR) {
            uiState.status.error?.let { Text(stringResource(errorMessage(it)), color = MaterialTheme.colorScheme.error) }
        }
        if (uiState.profiles.isLoading) {
            LoadingIndicator()
        } else if (uiState.profiles.error == ProfileActionError.READ_FAILED) {
            Text(stringResource(R.string.error_profile_read), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetryProfiles) { Text(stringResource(R.string.profiles_retry)) }
        }
        when (uiState.status.phase) {
            ConnectionPhase.DISCONNECTED ->
                if (!uiState.profiles.isLoading && uiState.profiles.error == null && !uiState.hasProfile) {
                    if (uiState.profiles.document
                            ?.profiles
                            .isNullOrEmpty()
                    ) {
                        EmptyProfileCard(onOpenSettings = onOpenSettings)
                    } else {
                        Text(
                            stringResource(
                                if (uiState.profiles.selectedProfile ==
                                    null
                                ) {
                                    R.string.profiles_choose
                                } else {
                                    R.string.error_profile_invalid
                                },
                            ),
                        )
                    }
                }

            ConnectionPhase.STARTING,
            ConnectionPhase.STOPPING,
            ConnectionPhase.CONNECTED,
            -> StatusCard(status = uiState.status)

            ConnectionPhase.ERROR ->
                ErrorCard(
                    error = uiState.status.error,
                    onRetry = onConnect,
                    onOpenSettings = onOpenSettings,
                )
        }
    }
}

/** 连接中/已连接时展示的隧道、内核、节点与虚拟 IP 明细。 */
@Composable
private fun StatusCard(status: ConnectionStatus) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            StatusRow(
                icon = { Icon(VpnKeyIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                label = stringResource(R.string.connection_vpn_service_label),
                value =
                    stringResource(
                        if (status.vpnServiceRunning) {
                            R.string.connection_component_running
                        } else {
                            R.string.connection_component_stopped
                        },
                    ),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            StatusRow(
                icon = { Icon(MemoryIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                label = stringResource(R.string.connection_kernel_label),
                value =
                    stringResource(
                        if (status.kernelRunning) R.string.connection_component_running else R.string.connection_component_stopped,
                    ),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            StatusRow(
                icon = { Icon(LanIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                label = stringResource(R.string.connection_peers_label),
                value =
                    when (status.peerCount) {
                        null -> stringResource(R.string.connection_peers_waiting)
                        1 -> stringResource(R.string.connection_peers_one)
                        else -> stringResource(R.string.connection_peers_count, status.peerCount)
                    },
            )
            status.virtualIpv4?.let { address ->
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                StatusRow(
                    icon = { Icon(RouterIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    label = stringResource(R.string.connection_virtual_ip_label),
                    value = address,
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        icon()
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.titleSmall)
    }
}

/** 未配置网络时的引导卡片，提供前往设置的直接出口。 */
@Composable
private fun EmptyProfileCard(onOpenSettings: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = InfoIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.connection_empty_title),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
            }
            Text(
                text = stringResource(R.string.connection_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.connection_empty_action))
            }
        }
    }
}

/** 连接失败卡片：说明原因并给出重试与检查设置两个恢复动作。 */
@Composable
private fun ErrorCard(
    error: ConnectionError?,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(imageVector = ErrorIcon, contentDescription = null)
                Text(
                    text = stringResource(error?.let(::errorMessage) ?: R.string.error_connection_start),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry) {
                    Text(text = stringResource(R.string.connection_error_retry))
                }
                TextButton(onClick = onOpenSettings) {
                    Text(text = stringResource(R.string.connection_error_check_settings))
                }
            }
        }
    }
}

/** Hero 副文案：已连接时显示连接时长（每 30 秒刷新），其余阶段显示状态说明。 */
@Composable
private fun heroSubtitle(uiState: ConnectionUiState): String {
    val status = uiState.status
    val elapsed = rememberElapsedText(status.connectedAtEpochMs)
    return when (status.phase) {
        ConnectionPhase.DISCONNECTED ->
            stringResource(
                if (uiState.hasProfile) R.string.connection_ready_subtitle else R.string.connection_empty_subtitle,
            )

        ConnectionPhase.STARTING -> stringResource(R.string.connection_starting_subtitle)
        ConnectionPhase.STOPPING -> stringResource(R.string.connection_stopping_subtitle)
        ConnectionPhase.CONNECTED -> elapsed ?: stringResource(R.string.connection_connected)
        ConnectionPhase.ERROR -> stringResource(R.string.connection_error_subtitle)
    }
}

/** 由 [connectedAtEpochMs] 计算“已连接 X 分钟”文案；为 null（未连接）时返回 null。 */
@Composable
private fun rememberElapsedText(connectedAtEpochMs: Long?): String? {
    if (connectedAtEpochMs == null) return null
    val now by produceState(initialValue = System.currentTimeMillis(), connectedAtEpochMs) {
        while (true) {
            delay(ELAPSED_TICK_INTERVAL_MS.milliseconds)
            value = System.currentTimeMillis()
        }
    }
    val minutes = ((now - connectedAtEpochMs) / 60_000L).coerceAtLeast(0)
    val duration =
        when {
            minutes < 1 -> stringResource(R.string.duration_less_than_minute)
            minutes < 60 -> stringResource(R.string.duration_minutes, minutes)
            minutes % 60 == 0L -> stringResource(R.string.duration_hours, minutes / 60)
            else -> stringResource(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
        }
    return stringResource(R.string.connection_elapsed, duration)
}

/** Hero 按钮容器色随连接阶段变化，与状态文案共同构成颜色+图标+文字的多重状态信号。 */
@Composable
private fun heroContainerColor(phase: ConnectionPhase): Color =
    when (phase) {
        ConnectionPhase.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
        ConnectionPhase.STARTING,
        ConnectionPhase.STOPPING,
        -> MaterialTheme.colorScheme.primaryContainer

        ConnectionPhase.CONNECTED -> extendedColors.success
        ConnectionPhase.ERROR -> MaterialTheme.colorScheme.errorContainer
    }

@Composable
private fun heroContentColor(phase: ConnectionPhase): Color =
    when (phase) {
        ConnectionPhase.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
        ConnectionPhase.STARTING,
        ConnectionPhase.STOPPING,
        -> MaterialTheme.colorScheme.onPrimaryContainer

        ConnectionPhase.CONNECTED -> extendedColors.onSuccess
        ConnectionPhase.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }

@Composable
private fun statusColor(phase: ConnectionPhase): Color =
    when (phase) {
        ConnectionPhase.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
        ConnectionPhase.STARTING,
        ConnectionPhase.STOPPING,
        -> MaterialTheme.colorScheme.primary

        ConnectionPhase.CONNECTED -> extendedColors.success
        ConnectionPhase.ERROR -> MaterialTheme.colorScheme.error
    }

private fun statusTitle(phase: ConnectionPhase): Int =
    when (phase) {
        ConnectionPhase.DISCONNECTED -> R.string.connection_disconnected
        ConnectionPhase.STARTING -> R.string.connection_starting
        ConnectionPhase.STOPPING -> R.string.connection_disconnecting
        ConnectionPhase.CONNECTED -> R.string.connection_connected
        ConnectionPhase.ERROR -> R.string.connection_failed
    }

private fun errorMessage(error: ConnectionError): Int =
    when (error) {
        ConnectionError.VPN_PERMISSION_DENIED -> R.string.error_vpn_permission_denied
        ConnectionError.PROFILE_READ_FAILED -> R.string.error_profile_read
        ConnectionError.PROFILE_INVALID -> R.string.error_profile_invalid
        ConnectionError.PROFILE_NOT_FOUND -> R.string.error_profile_not_found
        ConnectionError.PROFILE_WRITE_FAILED -> R.string.error_profile_resume_save
        ConnectionError.NATIVE_LIBRARY_UNAVAILABLE -> R.string.error_native_library
        ConnectionError.START_FAILED -> R.string.error_connection_start
        ConnectionError.STOP_FAILED -> R.string.error_connection_stop
    }

@Preview(showBackground = true, name = "Phone")
@Preview(showBackground = true, name = "Phone Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, name = "Tablet", device = "spec:width=1280dp,height=800dp,dpi=240")
@Preview(showBackground = true, name = "Short Wide", widthDp = 840, heightDp = 360)
@Preview(showBackground = true, name = "Short Wide Large Font", widthDp = 840, heightDp = 360, fontScale = 2f)
@Composable
private fun ConnectionScreenNoProfilePreview() {
    EasytierKTTheme {
        ConnectionScreen(
            uiState = ConnectionUiState(),
            onConnect = {},
            onDisconnect = {},
            onOpenSettings = {},
        )
    }
}

@Preview(showBackground = true, name = "Connected")
@Preview(showBackground = true, name = "Connected Short Wide", widthDp = 840, heightDp = 360)
@Preview(showBackground = true, name = "Connected Short Wide Large Font", widthDp = 840, heightDp = 360, fontScale = 2f)
@Composable
private fun ConnectionScreenConnectedPreview() {
    EasytierKTTheme {
        ConnectionScreen(
            uiState =
                ConnectionUiState(
                    status =
                        ConnectionStatus(
                            phase = ConnectionPhase.CONNECTED,
                            vpnServiceRunning = true,
                            kernelRunning = true,
                            peerCount = 3,
                            virtualIpv4 = "10.144.144.1",
                            connectedAtEpochMs = System.currentTimeMillis() - 12 * 60_000L,
                        ),
                ),
            onConnect = {},
            onDisconnect = {},
            onOpenSettings = {},
        )
    }
}

@Preview(showBackground = true, name = "Error")
@Preview(showBackground = true, name = "Error Short Wide", widthDp = 840, heightDp = 360)
@Preview(showBackground = true, name = "Error Short Wide Large Font", widthDp = 840, heightDp = 360, fontScale = 2f)
@Composable
private fun ConnectionScreenErrorPreview() {
    EasytierKTTheme {
        ConnectionScreen(
            uiState =
                ConnectionUiState(
                    status =
                        ConnectionStatus(
                            phase = ConnectionPhase.ERROR,
                            error = ConnectionError.VPN_PERMISSION_DENIED,
                        ),
                ),
            onConnect = {},
            onDisconnect = {},
            onOpenSettings = {},
        )
    }
}
