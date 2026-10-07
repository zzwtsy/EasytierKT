package com.github.zzwtsy.easytierkt.feature.connection

import com.github.zzwtsy.easytierkt.data.connection.ConnectionStatus

data class ConnectionUiState(
    val status: ConnectionStatus = ConnectionStatus.Unavailable,
)
