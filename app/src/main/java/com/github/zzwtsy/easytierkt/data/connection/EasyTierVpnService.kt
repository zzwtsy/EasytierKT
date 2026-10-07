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
import com.github.zzwtsy.easytierkt.MainActivity
import com.github.zzwtsy.easytierkt.R
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.EncryptedProfileStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/** 在前台服务中管理单个配置对应的 VPN 接口与 EasyTier 内核实例。 */
class EasyTierVpnService : VpnService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var profileStore: EncryptedProfileStore
    private var startJob: Job? = null
    private var monitorJob: Job? = null
    private var vpnInterface: ParcelFileDescriptor? = null
    private var currentTunConfig: TunConfig? = null

    @Volatile
    private var engineStarted = false

    private var sessionActive = false

    override fun onCreate() {
        super.onCreate()
        profileStore = EncryptedProfileStore(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                serviceScope.launch {
                    endSession()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelfResult(startId)
                }
                return START_NOT_STICKY
            }

            // START_STICKY 恢复服务时可能收到 null intent；此时仍按当前保存的配置启动会话。
            null, ACTION_CONNECT -> Unit

            else -> {
                stopSelfResult(startId)
                return START_NOT_STICKY
            }
        }

        if (!promoteToForeground()) {
            ConnectionRuntime.update(
                ConnectionStatus(phase = ConnectionPhase.ERROR, error = ConnectionError.START_FAILED),
            )
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        if (!sessionActive && startJob?.isActive != true) {
            ConnectionRuntime.update(ConnectionStatus(phase = ConnectionPhase.STARTING))
            startJob = serviceScope.launch { startSession() }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        super.onRevoke()
        serviceScope.launch {
            endSession()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        closeTunInterface()
        super.onDestroy()
    }

    private suspend fun startSession() {
        val profile =
            try {
                withContext(Dispatchers.IO) { profileStore.read() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                fail(ConnectionError.PROFILE_READ_FAILED)
                return
            }

        if (profile.validationError() != null) {
            fail(ConnectionError.PROFILE_INVALID)
            return
        }

        try {
            withContext(Dispatchers.IO) {
                EasyTierEngine.start(profile.toEasyTierToml())
                engineStarted = true
            }
            ConnectionRuntime.update(
                ConnectionStatus(
                    phase = ConnectionPhase.STARTING,
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
                    fail(ConnectionError.START_FAILED)
                    return
                }

            val desiredConfig = createTunConfig(profile, assignedInfo)
            applyTunConfig(desiredConfig)
            sessionActive = true
            publishConnected(assignedInfo)
            monitorJob = serviceScope.launch { monitorNetwork(profile) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: LinkageError) {
            fail(ConnectionError.NATIVE_LIBRARY_UNAVAILABLE)
        } catch (_: SecurityException) {
            fail(ConnectionError.START_FAILED)
        } catch (error: Exception) {
            Log.e(TAG, "Failed to start EasyTier session (${error.javaClass.simpleName})")
            fail(ConnectionError.START_FAILED)
        }
    }

    private suspend fun monitorNetwork(profile: ConnectionProfile) {
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
                    fail(ConnectionError.START_FAILED)
                    return
                }
            }
            publishConnected(info)
        }
    }

    private suspend fun applyTunConfig(config: TunConfig) {
        val builder =
            Builder()
                .setSession(getString(R.string.notification_channel_name))
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

    private suspend fun fail(error: ConnectionError) {
        sessionActive = false
        stopEasyTierInstance()
        closeTunInterface()
        ConnectionRuntime.update(ConnectionStatus(phase = ConnectionPhase.ERROR, error = error))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun endSession() {
        startJob?.cancelAndJoin()
        startJob = null
        monitorJob?.cancelAndJoin()
        monitorJob = null
        sessionActive = false
        stopEasyTierInstance()
        closeTunInterface()
        ConnectionRuntime.update(ConnectionStatus())
    }

    private suspend fun stopEasyTierInstance() {
        if (!engineStarted) return
        withContext(Dispatchers.IO) {
            try {
                EasyTierEngine.stop()
            } catch (error: LinkageError) {
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
        ConnectionRuntime.update(
            ConnectionStatus(
                phase = ConnectionPhase.CONNECTED,
                vpnServiceRunning = vpnInterface != null,
                kernelRunning = engineStarted,
                virtualIpv4 = info.virtualIpv4,
                peerCount = info.peerCount,
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
            .setContentText(getString(R.string.notification_running))
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

    companion object {
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
