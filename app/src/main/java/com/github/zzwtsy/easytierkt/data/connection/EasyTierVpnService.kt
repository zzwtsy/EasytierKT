package com.github.zzwtsy.easytierkt.data.connection

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.github.zzwtsy.easytierkt.EasytierApplication
import com.github.zzwtsy.easytierkt.MainActivity
import com.github.zzwtsy.easytierkt.R
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfileRepository
import com.github.zzwtsy.easytierkt.data.profile.SavedProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/** 在前台服务中管理单个配置对应的 VPN 接口与 EasyTier 内核实例。 */
class EasyTierVpnService : VpnService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var profiles: ConnectionProfileRepository
    private val commands = Channel<ServiceCommand>(Channel.UNLIMITED)
    private var sessionProfile: SavedProfile? = null
    private var generation = 0L
    private var startJob: Job? = null
    private var monitorJob: Job? = null
    private var vpnInterface: ParcelFileDescriptor? = null
    private var currentTunConfig: TunConfig? = null

    @Volatile
    private var engineStarted = false

    private var sessionActive = false

    override fun onCreate() {
        super.onCreate()
        profiles = (application as EasytierApplication).appContainer.profileRepository
        createNotificationChannel()
        serviceScope.launch {
            for (command in commands) {
                when (command) {
                    is ServiceCommand.Connect -> {
                        if (sessionActive || startJob?.isActive == true) continue
                        generation += 1
                        val sessionGeneration = generation
                        startJob = serviceScope.launch { startSession(command.id, command.restore, command.startId, sessionGeneration) }
                    }
                    is ServiceCommand.Stop -> {
                        endSession()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelfResult(command.startId)
                    }
                    is ServiceCommand.Failed -> {
                        // 已清理会话的延迟错误不能终止后来启动的新会话。
                        if (command.generation != generation) continue
                        endSession(command.error)
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        lastStartId = startId
        if (intent?.action == ACTION_DISCONNECT) {
            ConnectionRuntime.update(ConnectionRuntime.status.value.copy(phase = ConnectionPhase.STOPPING))
            commands.trySend(ServiceCommand.Stop(startId))
            return START_NOT_STICKY
        }
        val restoring = intent == null
        if (!restoring && intent.action != ACTION_CONNECT) return START_NOT_STICKY
        val id = intent?.getStringExtra(EXTRA_PROFILE_ID)
        if (!restoring && id.isNullOrBlank()) return START_NOT_STICKY
        if (!promoteToForeground()) {
            commands.trySend(ServiceCommand.Failed(ConnectionError.START_FAILED, generation))
            return START_NOT_STICKY
        }
        commands.trySend(ServiceCommand.Connect(id, restoring, startId))
        return START_STICKY
    }

    override fun onRevoke() {
        super.onRevoke()
        commands.trySend(ServiceCommand.Stop(lastStartId))
    }

    private var lastStartId = 0

    override fun onDestroy() {
        commands.close()
        if (sessionActive || engineStarted || startJob?.isActive == true) {
            ConnectionRuntime.update(ConnectionRuntime.status.value.copy(phase = ConnectionPhase.STOPPING))
            serviceScope.launch(start = CoroutineStart.UNDISPATCHED) {
                // 非主动销毁保留恢复 ID，只清理此进程的资源与预留；显式停止已在 endSession 撤销恢复。
                withContext(NonCancellable) {
                    startJob?.cancelAndJoin()
                    monitorJob?.cancelAndJoin()
                    sessionActive = false
                    stopEasyTierInstance()
                    closeTunInterface()
                    profiles.releaseSession()
                    ConnectionRuntime.update(ConnectionStatus())
                }
            }
        } else {
            closeTunInterface()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun startSession(
        requestedId: String?,
        restore: Boolean,
        startId: Int,
        generation: Long,
    ) {
        if (restore) {
            // 同进程的上一服务仍清理资源时，恢复任务等待其释放身份，避免留下未启动的前台服务。
            ConnectionRuntime.status.first { it.phase != ConnectionPhase.STOPPING }
        } else if (ConnectionRuntime.status.value.phase == ConnectionPhase.STOPPING) {
            return
        }
        val id = if (restore) profiles.resumableProfileId() else requestedId
        if (id == null) {
            if (profiles.state.value.error != null) {
                fail(ConnectionError.PROFILE_READ_FAILED, generation)
            } else {
                commands.trySend(ServiceCommand.Stop(startId))
            }
            return
        }
        val result = profiles.sessionProfile(id)
        if (result.error != null) {
            fail(result.error.connectionError(), generation)
            return
        }
        if (ConnectionRuntime.status.value.phase == ConnectionPhase.STOPPING) return
        val savedProfile = requireNotNull(result.profile)
        sessionProfile = savedProfile
        val profile = savedProfile.config
        ConnectionRuntime.update(
            ConnectionStatus(phase = ConnectionPhase.STARTING, profileId = id, profileName = savedProfile.displayName),
        )
        promoteToForeground()

        try {
            withContext(Dispatchers.IO) {
                EasyTierEngine.start(profile.toEasyTierToml())
                engineStarted = true
            }
            ConnectionRuntime.update(
                ConnectionStatus(
                    phase = ConnectionPhase.STARTING,
                    profileId = savedProfile.id,
                    profileName = savedProfile.displayName,
                    kernelRunning = true,
                ),
            )

            var networkInfo: EasyTierNetworkInfo? = null
            for (attempt in 0 until STARTUP_INFO_ATTEMPTS) {
                networkInfo =
                    withContext(Dispatchers.IO) {
                        EasyTierEngine.networkInfo(ConnectionProfile.INSTANCE_NAME)
                    }
                if (networkInfo != null) break
                if (attempt < STARTUP_INFO_ATTEMPTS - 1) {
                    delay(STARTUP_INFO_INTERVAL_MS.milliseconds)
                }
            }

            val assignedInfo =
                networkInfo ?: run {
                    fail(ConnectionError.START_FAILED, generation)
                    return
                }

            val desiredConfig = createTunConfig(profile, assignedInfo)
            applyTunConfig(desiredConfig)
            sessionActive = true
            publishConnected(assignedInfo)
            monitorJob = serviceScope.launch { monitorNetwork(profile, generation) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: LinkageError) {
            fail(ConnectionError.NATIVE_LIBRARY_UNAVAILABLE, generation)
        } catch (_: SecurityException) {
            fail(ConnectionError.START_FAILED, generation)
        } catch (error: Exception) {
            Log.e(TAG, "Failed to start EasyTier session (${error.javaClass.simpleName})")
            fail(ConnectionError.START_FAILED, generation)
        }
    }

    private suspend fun monitorNetwork(
        profile: ConnectionProfile,
        generation: Long,
    ) {
        while (serviceScope.isActive && sessionActive) {
            delay(NETWORK_INFO_INTERVAL_MS.milliseconds)
            val info =
                try {
                    withContext(Dispatchers.IO) {
                        EasyTierEngine.networkInfo(ConnectionProfile.INSTANCE_NAME)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "Failed to read EasyTier network status (${error.javaClass.simpleName})")
                    continue
                }

            if (info == null) continue
            val desiredConfig = createTunConfig(profile, info)
            if (desiredConfig != currentTunConfig) {
                try {
                    applyTunConfig(desiredConfig)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e(TAG, "Failed to update VPN routes (${error.javaClass.simpleName})")
                    fail(ConnectionError.START_FAILED, generation)
                    return
                }
            }
            publishConnected(info)
        }
    }

    private suspend fun applyTunConfig(config: TunConfig) {
        val builder =
            Builder()
                .setSession(sessionProfile?.displayName ?: getString(R.string.notification_channel_name))
                .setMtu(VPN_MTU)

        builder.addAddress(config.ipv4Address, config.networkLength)
        config.routes.forEach { route ->
            val parts = route.split('/')
            check(parts.size == 2 && parts[1].toInt() in 1..32) { "Invalid VPN route" }
            builder.addRoute(parts[0], parts[1].toInt())
        }
        if (config.enableMagicDns) {
            builder.addDnsServer(MAGIC_DNS_SERVER)
        }
        // 排除本应用，避免 EasyTier 自身传输流量再次进入 VPN TUN。
        builder.addDisallowedApplication(packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        val nextInterface = builder.establish() ?: error("Android refused to establish the VPN interface")
        try {
            withContext(Dispatchers.IO) {
                EasyTierEngine.setTunFd(ConnectionProfile.INSTANCE_NAME, nextInterface.fd)
            }
        } catch (error: Throwable) {
            nextInterface.close()
            throw error
        }

        // 新接口交给内核成功后才替换并关闭旧接口；交接失败时先关闭新接口。
        val previousInterface = vpnInterface
        vpnInterface = nextInterface
        currentTunConfig = config
        try {
            previousInterface?.close()
        } catch (_: Exception) {
            // Android 可能已先关闭被替换的 VPN 接口。
        }
    }

    private fun fail(
        error: ConnectionError,
        generation: Long,
    ) {
        commands.trySend(ServiceCommand.Failed(error, generation))
    }

    private suspend fun endSession(error: ConnectionError? = null) {
        // 清理不可因服务作用域取消而中断；先撤销恢复，再等待启动和监控任务结束。
        withContext(NonCancellable) {
            ConnectionRuntime.update(ConnectionRuntime.status.value.copy(phase = ConnectionPhase.STOPPING))
            val storageError = profiles.clearResume().error?.connectionError()
            startJob?.cancelAndJoin()
            startJob = null
            monitorJob?.cancelAndJoin()
            monitorJob = null
            sessionActive = false
            stopEasyTierInstance()
            closeTunInterface()
            profiles.releaseSession()
            sessionProfile = null
            generation += 1
            val finalError = storageError ?: error
            ConnectionRuntime.update(
                ConnectionStatus(
                    phase = if (finalError == null) ConnectionPhase.DISCONNECTED else ConnectionPhase.ERROR,
                    error = finalError,
                ),
            )
        }
    }

    private suspend fun stopEasyTierInstance() {
        if (!engineStarted) return
        withContext(Dispatchers.IO) {
            try {
                EasyTierEngine.stop()
            } catch (_: LinkageError) {
                Log.w(TAG, "EasyTier native library is unavailable during stop")
            } catch (error: Exception) {
                Log.w(TAG, "Failed to stop EasyTier (${error.javaClass.simpleName})")
            } finally {
                engineStarted = false
            }
        }
    }

    private fun closeTunInterface() {
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
            // VPN 被撤销时，系统可能已先关闭文件描述符。
        } finally {
            vpnInterface = null
            currentTunConfig = null
        }
    }

    private fun publishConnected(info: EasyTierNetworkInfo) {
        // 监控循环每 2 秒重建状态；已连接时保留首次进入 CONNECTED 的时刻，避免连接时长被重置。
        val previous = ConnectionRuntime.status.value
        ConnectionRuntime.update(
            ConnectionStatus(
                phase = ConnectionPhase.CONNECTED,
                profileId = sessionProfile?.id,
                profileName = sessionProfile?.displayName,
                vpnServiceRunning = vpnInterface != null,
                kernelRunning = engineStarted,
                virtualIpv4 = info.virtualIpv4,
                peerCount = info.peerCount,
                connectedAtEpochMs =
                    previous.connectedAtEpochMs?.takeIf { previous.phase == ConnectionPhase.CONNECTED }
                        ?: System.currentTimeMillis(),
            ),
        )
    }

    private fun createTunConfig(
        profile: ConnectionProfile,
        info: EasyTierNetworkInfo,
    ): TunConfig {
        val magicDnsRoute = if (profile.enableMagicDns) listOf("$MAGIC_DNS_SERVER/32") else emptyList()
        // 不向 VPN 添加默认路由，避免 TUN 接管全部 IPv4 流量。
        val routes =
            (profile.routeCidrs() + info.proxyRoutes + magicDnsRoute)
                .filterNot { it.endsWith("/0") }
                .distinct()
                .sorted()

        return TunConfig(
            ipv4Address = info.virtualIpv4,
            networkLength = info.networkLength,
            routes = routes,
            enableMagicDns = profile.enableMagicDns,
        )
    }

    private fun promoteToForeground(): Boolean =
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }

    private fun buildNotification() =
        NotificationCompat
            .Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notification_channel_name))
            .setContentText(sessionProfile?.displayName ?: getString(R.string.notification_running))
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    OPEN_ACTIVITY_REQUEST_CODE,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            ).setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                0,
                getString(R.string.connection_disconnect),
                PendingIntent.getService(
                    this,
                    DISCONNECT_REQUEST_CODE,
                    Intent(this, EasyTierVpnService::class.java).setAction(ACTION_DISCONNECT),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            ).build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    private data class TunConfig(
        val ipv4Address: String,
        val networkLength: Int,
        val routes: List<String>,
        val enableMagicDns: Boolean,
    )

    private sealed interface ServiceCommand {
        data class Connect(
            val id: String?,
            val restore: Boolean,
            val startId: Int,
        ) : ServiceCommand

        data class Stop(
            val startId: Int,
        ) : ServiceCommand

        data class Failed(
            val error: ConnectionError,
            val generation: Long,
        ) : ServiceCommand
    }

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
        const val ACTION_CONNECT = "com.github.zzwtsy.easytierkt.action.CONNECT"
        const val ACTION_DISCONNECT = "com.github.zzwtsy.easytierkt.action.DISCONNECT"

        private const val TAG = "EasyTierVpnService"
        private const val NOTIFICATION_CHANNEL_ID = "easytier_vpn"
        private const val NOTIFICATION_ID = 11010
        private const val OPEN_ACTIVITY_REQUEST_CODE = 1
        private const val DISCONNECT_REQUEST_CODE = 2
        private const val VPN_MTU = 1_300
        private const val MAGIC_DNS_SERVER = "100.100.100.101"
        private const val STARTUP_INFO_ATTEMPTS = 60
        private const val STARTUP_INFO_INTERVAL_MS = 500L
        private const val NETWORK_INFO_INTERVAL_MS = 2_000L
    }
}
