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

/**
 * 描述 VPN 服务、EasyTier 内核和 peer 的连接状态。
 *
 * 本应用的 `CONNECTED` 阶段表示 TUN 与内核已启动，不代表 peer 已连通；`peerCount` 为 null 表示尚无计数，0 表示没有已连接 peer。
 */
data class ConnectionStatus(
    val phase: ConnectionPhase = ConnectionPhase.DISCONNECTED,
    val vpnServiceRunning: Boolean = false,
    val kernelRunning: Boolean = false,
    val virtualIpv4: String? = null,
    val peerCount: Int? = null,
    val error: ConnectionError? = null,
)

/** 提供连接状态和连接控制操作的边界；操作结果通过 [status] 持续反馈。 */
interface ConnectionRepository {
    /** 当前连接状态的只读流。 */
    val status: StateFlow<ConnectionStatus>

    /** 请求启动连接；调用返回不代表 VPN 和内核已经启动。 */
    fun connect()

    /** 请求结束当前连接。 */
    fun disconnect()

    /** 将系统 VPN 授权被拒绝的结果反馈到连接状态。 */
    fun reportVpnPermissionDenied()
}
