package com.github.zzwtsy.easytierkt.data.connection

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class UnavailableConnectionRepository : ConnectionRepository {
    override val status: Flow<ConnectionStatus> = flowOf(ConnectionStatus.Unavailable)
}
