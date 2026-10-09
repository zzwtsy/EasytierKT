package com.github.zzwtsy.easytierkt.feature.connection

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zzwtsy.easytierkt.data.connection.ConnectionRepository
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfileRepository

/** 连接页处理系统 VPN 授权，Repository 持续提供配置与实际会话状态。 */
@Composable
internal fun ConnectionRoute(
    repository: ConnectionRepository,
    profileRepository: ConnectionProfileRepository,
    onOpenSettings: () -> Unit,
) {
    val factory = remember(repository, profileRepository) { ConnectionViewModel.factory(repository, profileRepository) }
    val viewModel: ConnectionViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.finishPermission(it.resultCode == Activity.RESULT_OK)
        }
    ConnectionScreen(
        uiState = uiState,
        onConnect = {
            if (viewModel.beginPermission() != null) {
                val intent = VpnService.prepare(context)
                if (intent == null) viewModel.finishPermission(true) else launcher.launch(intent)
            }
        },
        onDisconnect = viewModel::disconnect,
        onOpenSettings = { if (!uiState.awaitingPermission) onOpenSettings() },
        onRetryProfiles = viewModel::retryProfiles,
    )
}
