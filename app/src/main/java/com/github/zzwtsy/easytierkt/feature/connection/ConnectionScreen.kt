package com.github.zzwtsy.easytierkt.feature.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.zzwtsy.easytierkt.R
import com.github.zzwtsy.easytierkt.data.connection.ConnectionError
import com.github.zzwtsy.easytierkt.data.connection.ConnectionPhase
import com.github.zzwtsy.easytierkt.data.connection.ConnectionStatus
import com.github.zzwtsy.easytierkt.ui.theme.EasytierKTTheme

@Composable
internal fun ConnectionScreen(
    uiState: ConnectionUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = uiState.status
    val isBusy = status.phase == ConnectionPhase.STOPPING
    val canDisconnect = status.phase == ConnectionPhase.STARTING || status.phase == ConnectionPhase.CONNECTED

    Scaffold(modifier = modifier.fillMaxSize()) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(text = stringResource(R.string.connection_title), style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
            Text(text = stringResource(R.string.connection_setup_hint))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(statusTitle(status.phase)),
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    )
                    StatusLine(
                        label = stringResource(R.string.connection_vpn_service_label),
                        value = stringResource(
                            if (status.vpnServiceRunning) R.string.connection_component_running else R.string.connection_component_stopped,
                        ),
                    )
                    StatusLine(
                        label = stringResource(R.string.connection_kernel_label),
                        value = stringResource(
                            if (status.kernelRunning) R.string.connection_component_running else R.string.connection_component_stopped,
                        ),
                    )
                    StatusLine(
                        label = stringResource(R.string.connection_peers_label),
                        value = when {
                            status.peerCount == null -> stringResource(R.string.connection_peers_waiting)
                            status.peerCount == 1 -> stringResource(R.string.connection_peers_one)
                            else -> stringResource(R.string.connection_peers_count, status.peerCount)
                        },
                    )
                    status.virtualIpv4?.let { address ->
                        StatusLine(
                            label = stringResource(R.string.connection_virtual_ip_label),
                            value = address,
                        )
                    }
                    status.error?.let { error ->
                        Text(
                            text = stringResource(errorMessage(error)),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Button(
                onClick = if (canDisconnect) onDisconnect else onConnect,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(
                        when {
                            isBusy -> R.string.connection_disconnecting
                            canDisconnect && status.phase == ConnectionPhase.STARTING -> R.string.connection_cancel
                            canDisconnect -> R.string.connection_disconnect
                            else -> R.string.connection_connect
                        },
                    ),
                )
            }

            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.open_settings))
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label)
        Text(text = value)
    }
}

private fun statusTitle(phase: ConnectionPhase): Int = when (phase) {
    ConnectionPhase.DISCONNECTED -> R.string.connection_disconnected
    ConnectionPhase.STARTING -> R.string.connection_starting
    ConnectionPhase.STOPPING -> R.string.connection_disconnecting
    ConnectionPhase.CONNECTED -> R.string.connection_connected
    ConnectionPhase.ERROR -> R.string.connection_failed
}

private fun errorMessage(error: ConnectionError): Int = when (error) {
    ConnectionError.VPN_PERMISSION_DENIED -> R.string.error_vpn_permission_denied
    ConnectionError.PROFILE_READ_FAILED -> R.string.error_profile_read
    ConnectionError.PROFILE_INVALID -> R.string.error_profile_invalid
    ConnectionError.NATIVE_LIBRARY_UNAVAILABLE -> R.string.error_native_library
    ConnectionError.START_FAILED -> R.string.error_connection_start
}

@Preview(showBackground = true)
@Composable
private fun ConnectionScreenPreview() {
    EasytierKTTheme {
        ConnectionScreen(
            uiState = ConnectionUiState(ConnectionStatus()),
            onConnect = {},
            onDisconnect = {},
            onOpenSettings = {},
        )
    }
}
