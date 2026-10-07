package com.github.zzwtsy.easytierkt.feature.connection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zzwtsy.easytierkt.data.connection.ConnectionRepository

@Composable
internal fun ConnectionRoute(
    repository: ConnectionRepository,
    onOpenSettings: () -> Unit,
) {
    val factory = remember(repository) { ConnectionViewModel.factory(repository) }
    val viewModel: ConnectionViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ConnectionScreen(
        uiState = uiState,
        onOpenSettings = onOpenSettings,
    )
}
