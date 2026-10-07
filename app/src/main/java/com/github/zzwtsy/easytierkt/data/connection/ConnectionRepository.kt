package com.github.zzwtsy.easytierkt.data.connection

import kotlinx.coroutines.flow.Flow

interface ConnectionRepository {
    val status: Flow<ConnectionStatus>
}

sealed interface ConnectionStatus {
    data object Unavailable : ConnectionStatus
}
