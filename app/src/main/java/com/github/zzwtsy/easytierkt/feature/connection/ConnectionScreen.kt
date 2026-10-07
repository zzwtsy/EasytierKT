package com.github.zzwtsy.easytierkt.feature.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.zzwtsy.easytierkt.R
import com.github.zzwtsy.easytierkt.data.connection.ConnectionStatus
import com.github.zzwtsy.easytierkt.ui.theme.EasytierKTTheme

@Composable
internal fun ConnectionScreen(
    uiState: ConnectionUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = stringResource(R.string.connection_title))
            Text(text = stringResource(R.string.connection_status_label))
            when (uiState.status) {
                ConnectionStatus.Unavailable -> {
                    Text(text = stringResource(R.string.connection_unavailable))
                    Text(text = stringResource(R.string.connection_unavailable_description))
                }
            }
            TextButton(onClick = onOpenSettings) {
                Text(text = stringResource(R.string.open_settings))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionScreenPreview() {
    EasytierKTTheme {
        ConnectionScreen(
            uiState = ConnectionUiState(ConnectionStatus.Unavailable),
            onOpenSettings = {},
        )
    }
}
