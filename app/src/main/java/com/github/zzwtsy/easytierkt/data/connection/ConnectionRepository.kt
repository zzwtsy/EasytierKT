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
    PROFILE_NOT_FOUND,
    PROFILE_WRITE_FAILED,
    NATIVE_LIBRARY_UNAVAILABLE,
    CONFIG_REJECTED,
    ADDRESS_TIMEOUT,
    APPLICATION_UNAVAILABLE,
    DNS_UNREACHABLE,
    TUN_FAILED,
    START_FAILED,
    STOP_FAILED,
}

/**
 * 描述 VPN 服务、EasyTier 内核和 peer 的连接状态。
 *
 * 本应用的 `CONNECTED` 阶段表示 TUN 与内核已启动，不代表 peer 已连通；`peerCount` 为 null 表示尚无计数，0 表示没有已连接 peer。
 */
data class ConnectionStatus(
    val phase: ConnectionPhase = ConnectionPhase.DISCONNECTED,
    val profileId: String? = null,
    val profileName: String? = null,
    val vpnServiceRunning: Boolean = false,
    val kernelRunning: Boolean = false,
    val virtualIpv4: String? = null,
    val virtualIpv6: String? = null,
    val peerCount: Int? = null,
    val error: ConnectionError? = null,
    /** 进入 CONNECTED 的时刻（epoch 毫秒），用于展示连接时长；非 CONNECTED 阶段为 null。 */
    val connectedAtEpochMs: Long? = null,
)

/** 提供连接状态和连接控制操作的边界；操作结果通过 [status] 持续反馈。 */
interface ConnectionRepository {
    /** 当前连接状态的只读流。 */
    val status: StateFlow<ConnectionStatus>

    /** 请求启动指定配置；调用返回不代表 VPN 和内核已经启动，忙碌期间忽略重复请求。 */
    fun connect(profileId: String)

    /** 请求结束当前连接。 */
    fun disconnect()

    /** 将系统 VPN 授权被拒绝的结果反馈到连接状态。 */
    fun reportVpnPermissionDenied()
}

/** 这些阶段持有会话身份，配置选择与运行配置删除必须等待清理完成。 */
val ConnectionPhase.isBusy: Boolean
    get() = this == ConnectionPhase.STARTING || this == ConnectionPhase.CONNECTED || this == ConnectionPhase.STOPPING
