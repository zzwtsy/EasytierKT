package com.github.zzwtsy.easytierkt.data.connection

import kotlinx.coroutines.flow.StateFlow

enum class ConnectionPhase {
    DISCONNECTED,
    STARTING,
    STOPPING,
    CONNECTED,
    ERROR,
}

enum class ConnectionError {
    VPN_PERMISSION_DENIED,
    PROFILE_READ_FAILED,
    PROFILE_INVALID,
    NATIVE_LIBRARY_UNAVAILABLE,
    START_FAILED,
}

data class ConnectionStatus(
    val phase: ConnectionPhase = ConnectionPhase.DISCONNECTED,
    val vpnServiceRunning: Boolean = false,
    val kernelRunning: Boolean = false,
    val virtualIpv4: String? = null,
    val peerCount: Int? = null,
    val error: ConnectionError? = null,
)

interface ConnectionRepository {
    val status: StateFlow<ConnectionStatus>

    fun connect()

    fun disconnect()

    fun reportVpnPermissionDenied()
}
