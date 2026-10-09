package com.github.zzwtsy.easytierkt.data.connection

import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfileRepository
import com.github.zzwtsy.easytierkt.data.profile.ProfileActionError
import com.github.zzwtsy.easytierkt.data.profile.SavedProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

internal interface VpnServiceLauncher {
    fun connect(
        id: String,
        token: Long,
    )

    fun disconnect(token: Long)
}

internal interface TunHandle : AutoCloseable {
    val fd: Int
}

/** Android 生命周期与 TUN 建立端口；控制器持有接口资源，服务只提供平台操作。 */
internal interface VpnServiceHost {
    fun establish(
        plan: VpnPlan,
        profileName: String,
    ): TunHandle

    fun showProfile(
        name: String,
        token: Long,
    )

    fun finish()
}

/** 应用级单事件队列是唯一状态发布者；会话令牌隔离旧任务和旧服务回调。 */
internal class VpnSessionController(
    private val profiles: ConnectionProfileRepository,
    private val scope: CoroutineScope,
    private val launcher: VpnServiceLauncher,
    private val kernel: VpnKernel = EasyTierEngine,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val events = Channel<Event>(Channel.UNLIMITED)
    private val mutableStatus = MutableStateFlow(ConnectionStatus())
    val status = mutableStatus.asStateFlow()
    private var generation = 0L
    private var session: Session? = null

    init {
        scope.launch {
            for (event in events) {
                when (event) {
                    is Event.Connect -> connectSession(event.id)
                    Event.Disconnect -> requestStop()
                    Event.PermissionDenied -> if (session == null) publishError(ConnectionError.VPN_PERMISSION_DENIED)
                    is Event.ServiceReady -> attachService(event)
                    is Event.ServiceStop -> {
                        val current = session
                        if (current != null &&
                            (
                                (current.host === event.host && (event.token == null || current.token == event.token)) ||
                                    current.host == null &&
                                    current.token == event.token
                            )
                        ) {
                            current.host = event.host
                            cleanup(clearResume = true)
                        } else if (current?.host !== event.host) {
                            event.host.finish()
                        }
                    }
                    is Event.ServiceDestroyed -> if (session?.host === event.host) cleanup(clearResume = false, finishHost = false)
                    is Event.ServiceFailed ->
                        if (session != null &&
                            session?.token == event.token &&
                            (session?.host == null || session?.host === event.host)
                        ) {
                            session?.host = event.host
                            cleanup(clearResume = true, error = ConnectionError.START_FAILED)
                        } else {
                            event.host.finish()
                        }
                    is Event.KernelStarted ->
                        if (session?.token == event.token && session?.closing == false) {
                            mutableStatus.value = status.value.copy(kernelRunning = true)
                        }
                    is Event.Info -> if (session?.token == event.token) applyInfo(event.info)
                    is Event.Failed -> if (session?.token == event.token) cleanup(clearResume = true, error = event.error)
                }
            }
        }
    }

    /** 服务与事件消费均在 Main，Android 分发前过滤已过期的 Intent，避免误停新服务。 */
    fun isCurrentSession(token: Long?): Boolean = token != null && session?.token == token

    fun hasSession(): Boolean = session != null

    fun connect(id: String) {
        events.trySend(Event.Connect(id))
    }

    fun disconnect() {
        events.trySend(Event.Disconnect)
    }

    fun permissionDenied() {
        events.trySend(Event.PermissionDenied)
    }

    fun serviceReady(
        host: VpnServiceHost,
        id: String?,
        token: Long?,
        restore: Boolean,
    ) {
        events.trySend(Event.ServiceReady(host, id, token, restore))
    }

    fun serviceStop(
        host: VpnServiceHost,
        token: Long?,
    ) {
        events.trySend(Event.ServiceStop(host, token))
    }

    fun serviceDestroyed(host: VpnServiceHost) {
        events.trySend(Event.ServiceDestroyed(host))
    }

    fun serviceFailed(
        host: VpnServiceHost,
        token: Long?,
    ) {
        events.trySend(Event.ServiceFailed(host, token))
    }

    private suspend fun connectSession(id: String) {
        if (session != null) return
        val result = profiles.reserveSession(id)
        if (result.error == ProfileActionError.CONNECTION_BUSY) return
        if (result.error != null) {
            publishError(result.error.connectionError(), id)
            return
        }
        val current = Session(++generation, requireNotNull(result.profile))
        session = current
        publishStarting(current)
        try {
            launcher.connect(id, current.token)
        } catch (_: Exception) {
            cleanup(clearResume = true, error = ConnectionError.START_FAILED)
        }
    }

    private suspend fun attachService(event: Event.ServiceReady) {
        if (event.restore && session == null) {
            val id = profiles.resumableProfileId()
            if (id == null) {
                if (profiles.state.value.error != null) publishError(ConnectionError.PROFILE_READ_FAILED)
                event.host.finish()
                return
            }
            val result = profiles.sessionProfile(id)
            if (result.error != null) {
                publishError(result.error.connectionError(), id)
                event.host.finish()
                return
            }
            session = Session(++generation, requireNotNull(result.profile))
            publishStarting(requireNotNull(session))
        }
        val current = session
        if (current == null || !event.restore && (event.token != current.token || event.id != current.profile.id)) {
            event.host.finish()
            return
        }
        if (current.host != null) {
            if (current.host !== event.host) event.host.finish()
            return
        }
        current.host = event.host
        if (current.closing || status.value.phase == ConnectionPhase.STOPPING) {
            cleanup(clearResume = true)
            return
        }
        try {
            event.host.showProfile(current.profile.displayName, current.token)
        } catch (_: Exception) {
            cleanup(true, ConnectionError.START_FAILED)
            return
        }
        // 在调用启动前记下清理责任，即使 JNI 部分启动后抛出异常，也必须停止内核。
        current.kernelOwned = true
        current.job = scope.launch { runKernel(current) }
    }

    private suspend fun runKernel(current: Session) {
        var stage = ConnectionError.CONFIG_REJECTED
        try {
            withContext(ioDispatcher) { kernel.start(current.profile.config.toEasyTierToml()) }
            events.send(Event.KernelStarted(current.token))
            stage = ConnectionError.ADDRESS_TIMEOUT
            var ready = false
            var addressAssigned = false
            for (attempt in 0 until 60) {
                val result = readInfo()
                if (result == NetworkInfoResult.Error) {
                    events.send(Event.Failed(current.token, ConnectionError.START_FAILED))
                    return
                }
                if (result is NetworkInfoResult.Ready) {
                    addressAssigned = true
                    try {
                        VpnPlanBuilder.build(current.profile.config, result.info)
                        events.send(Event.Info(current.token, result.info))
                        ready = true
                        break
                    } catch (_: UnreachableDnsException) {
                        // 地址分配可能先于路由发现，启动期间允许等待 DNS 所需路由。
                    }
                }
                if (!ready && attempt < 59) delay(500.milliseconds)
            }
            if (!ready) {
                events.send(
                    Event.Failed(current.token, if (addressAssigned) ConnectionError.DNS_UNREACHABLE else ConnectionError.ADDRESS_TIMEOUT),
                )
                return
            }
            while (scope.isActive) {
                delay(2_000.milliseconds)
                when (val result = readInfo()) {
                    is NetworkInfoResult.Ready -> events.send(Event.Info(current.token, result.info))
                    NetworkInfoResult.Error -> {
                        events.send(Event.Failed(current.token, ConnectionError.START_FAILED))
                        return
                    }
                    NetworkInfoResult.NotReady -> Unit
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: LinkageError) {
            events.send(Event.Failed(current.token, ConnectionError.NATIVE_LIBRARY_UNAVAILABLE))
        } catch (_: Exception) {
            events.send(Event.Failed(current.token, stage))
        }
    }

    /** 瞬时读取或格式错误允许下次轮询，明确的内核 Error 仍交由状态机清理。 */
    private suspend fun readInfo(): NetworkInfoResult =
        try {
            withContext(ioDispatcher) { kernel.readInfo(ConnectionProfile.INSTANCE_NAME) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            NetworkInfoResult.NotReady
        }

    private suspend fun applyInfo(info: EasyTierNetworkInfo) {
        val current = session ?: return
        if (current.closing || status.value.phase == ConnectionPhase.STOPPING) return
        try {
            val plan = VpnPlanBuilder.build(current.profile.config, info)
            if (plan != current.plan) {
                val next = requireNotNull(current.host).establish(plan, current.profile.displayName)
                try {
                    withContext(NonCancellable + ioDispatcher) { kernel.setTunFd(ConnectionProfile.INSTANCE_NAME, next.fd) }
                } catch (error: Throwable) {
                    close(next)
                    throw error
                }
                val previous = current.tun
                current.tun = next
                current.plan = plan
                close(previous)
            }
            mutableStatus.value =
                ConnectionStatus(
                    phase = ConnectionPhase.CONNECTED,
                    profileId = current.profile.id,
                    profileName = current.profile.displayName,
                    vpnServiceRunning = current.tun != null,
                    kernelRunning = true,
                    virtualIpv4 = info.virtualIpv4,
                    virtualIpv6 = info.virtualIpv6,
                    peerCount = info.peerCount,
                    connectedAtEpochMs = status.value.connectedAtEpochMs ?: now(),
                )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: LinkageError) {
            cleanup(true, ConnectionError.NATIVE_LIBRARY_UNAVAILABLE)
        } catch (_: UnreachableDnsException) {
            cleanup(true, ConnectionError.DNS_UNREACHABLE)
        } catch (_: MissingApplicationException) {
            cleanup(true, ConnectionError.APPLICATION_UNAVAILABLE)
        } catch (_: Exception) {
            cleanup(true, ConnectionError.TUN_FAILED)
        }
    }

    private fun requestStop() {
        val current = session ?: return
        if (status.value.phase == ConnectionPhase.STOPPING && status.value.error == null) return
        val previous = status.value
        mutableStatus.value = previous.copy(phase = ConnectionPhase.STOPPING, error = null)
        try {
            launcher.disconnect(current.token)
        } catch (_: Exception) {
            // 停止请求未送达时保持资源预留，防止两个内核并存。
            mutableStatus.value = previous.copy(error = ConnectionError.STOP_FAILED)
        }
    }

    private suspend fun cleanup(
        clearResume: Boolean,
        error: ConnectionError? = null,
        finishHost: Boolean = true,
    ) {
        val current = session ?: return
        withContext(NonCancellable) {
            current.closing = true
            mutableStatus.value = status.value.copy(phase = ConnectionPhase.STOPPING)
            val storageError = if (clearResume) profiles.clearResume().error?.connectionError() else null
            current.job?.cancelAndJoin()
            var stopError: ConnectionError? = null
            if (current.kernelOwned) {
                try {
                    withContext(ioDispatcher) { kernel.stop() }
                } catch (_: LinkageError) {
                    stopError = ConnectionError.NATIVE_LIBRARY_UNAVAILABLE
                } catch (_: Exception) {
                    stopError = ConnectionError.STOP_FAILED
                }
            }
            close(current.tun)
            current.tun = null
            current.plan = null
            current.job = null
            if (stopError != null) {
                // 无法确认内核停止时继续持有预留，用户可重试断开，不能启动第二个内核。
                if (!finishHost) current.host = null
                mutableStatus.value =
                    status.value.copy(
                        phase = ConnectionPhase.ERROR,
                        vpnServiceRunning = false,
                        kernelRunning = true,
                        connectedAtEpochMs = null,
                        error = stopError,
                    )
                return@withContext
            }
            profiles.releaseSession()
            session = null
            if (finishHost) current.host?.finish()
            val failure = storageError ?: error ?: stopError
            mutableStatus.value =
                ConnectionStatus(
                    phase = if (failure == null) ConnectionPhase.DISCONNECTED else ConnectionPhase.ERROR,
                    error = failure,
                )
        }
    }

    private fun close(handle: TunHandle?) {
        try {
            handle?.close()
        } catch (_: Exception) {
            // 系统撤销 VPN 时可能已关闭接口。
        }
    }

    private fun publishError(
        error: ConnectionError,
        id: String? = null,
    ) {
        mutableStatus.value = ConnectionStatus(phase = ConnectionPhase.ERROR, profileId = id, error = error)
    }

    private fun publishStarting(current: Session) {
        mutableStatus.value =
            ConnectionStatus(phase = ConnectionPhase.STARTING, profileId = current.profile.id, profileName = current.profile.displayName)
    }

    private class Session(
        val token: Long,
        val profile: SavedProfile,
    ) {
        var host: VpnServiceHost? = null
        var job: Job? = null
        var closing = false
        var kernelOwned = false
        var tun: TunHandle? = null
        var plan: VpnPlan? = null
    }

    private sealed interface Event {
        data class Connect(
            val id: String,
        ) : Event

        data object Disconnect : Event

        data object PermissionDenied : Event

        data class ServiceReady(
            val host: VpnServiceHost,
            val id: String?,
            val token: Long?,
            val restore: Boolean,
        ) : Event

        data class ServiceStop(
            val host: VpnServiceHost,
            val token: Long?,
        ) : Event

        data class ServiceDestroyed(
            val host: VpnServiceHost,
        ) : Event

        data class ServiceFailed(
            val host: VpnServiceHost,
            val token: Long?,
        ) : Event

        data class KernelStarted(
            val token: Long,
        ) : Event

        data class Info(
            val token: Long,
            val info: EasyTierNetworkInfo,
        ) : Event

        data class Failed(
            val token: Long,
            val error: ConnectionError,
        ) : Event
    }
}

internal class MissingApplicationException : IllegalArgumentException("Selected application unavailable")
