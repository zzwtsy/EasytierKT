package com.github.zzwtsy.easytierkt.feature.connection

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.github.zzwtsy.easytierkt.data.connection.ConnectionRepository

/** 连接页的装配层，负责创建 ViewModel、处理系统 VPN 授权并连接 Screen 回调。 */
@Composable
internal fun ConnectionRoute(
    repository: ConnectionRepository,
    onOpenSettings: () -> Unit,
) {
    val factory = remember(repository) { ConnectionViewModel.factory(repository) }
    val viewModel: ConnectionViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.connect()
        } else {
            viewModel.reportVpnPermissionDenied()
        }
    }

    ConnectionScreen(
        uiState = uiState,
        onConnect = {
            val permissionIntent: Intent? = VpnService.prepare(context)
            if (permissionIntent == null) {
                viewModel.connect()
            } else {
                vpnPermissionLauncher.launch(permissionIntent)
            }
        },
        onDisconnect = viewModel::disconnect,
        onOpenSettings = onOpenSettings,
    )
}
