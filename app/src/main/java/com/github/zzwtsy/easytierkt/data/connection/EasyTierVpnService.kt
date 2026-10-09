package com.github.zzwtsy.easytierkt.data.connection

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.core.app.NotificationCompat
import com.github.zzwtsy.easytierkt.EasytierApplication
import com.github.zzwtsy.easytierkt.MainActivity
import com.github.zzwtsy.easytierkt.R
import com.github.zzwtsy.easytierkt.data.profile.ApplicationMode

/** Android 服务只管理前台时限、通知和 Builder，业务会话及资源所有权交给应用级控制器。 */
class EasyTierVpnService : VpnService() {
    private lateinit var controller: VpnSessionController
    private var profileName: String? = null
    private var sessionToken: Long? = null
    private var activeHost: VpnServiceHost? = null
    private val hosts = mutableListOf<VpnServiceHost>()
    private val hostStartIds = mutableMapOf<VpnServiceHost, Int>()
    private val hostTokens = mutableMapOf<VpnServiceHost, Long?>()

    /** 捕获本次 startId，旧会话的延迟清理不能停止后来收到启动请求的服务。 */
    private fun createHost(startId: Int): VpnServiceHost =
        object : VpnServiceHost {
            override fun establish(
                plan: VpnPlan,
                profileName: String,
            ): TunHandle = establishTun(plan)

            override fun showProfile(
                name: String,
                token: Long,
            ) {
                profileName = name
                hostTokens[this] = token
                if (activeHost !== this) return
                sessionToken = token
                check(promoteToForeground()) { "Unable to update foreground notification" }
            }

            override fun finish() {
                val stopped = stopSelfResult(hostStartIds[this] ?: startId)
                if (stopped) stopForeground(STOP_FOREGROUND_REMOVE)
                if (activeHost === this) activeHost = null
            }
        }.also {
            hosts += it
            hostStartIds[it] = startId
        }

    override fun onCreate() {
        super.onCreate()
        controller = requireNotNull((application as EasytierApplication).appContainer.sessionController)
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val token = intent?.takeIf { it.hasExtra(EXTRA_SESSION_TOKEN) }?.getLongExtra(EXTRA_SESSION_TOKEN, 0)
        if (intent?.action == ACTION_DISCONNECT) {
            val stopToken = token ?: sessionToken
            if (!controller.isCurrentSession(stopToken)) {
                if (!controller.hasSession()) stopSelfResult(startId)
                return if (controller.hasSession()) START_STICKY else START_NOT_STICKY
            }
            val host = hosts.lastOrNull { hostTokens[it] == stopToken } ?: createHost(startId)
            if (host === activeHost) hostStartIds[host] = startId
            controller.serviceStop(host, stopToken)
            return START_NOT_STICKY
        }
        val restoring = intent == null
        if (!restoring && intent.action != ACTION_CONNECT) return START_NOT_STICKY
        val id = intent?.getStringExtra(EXTRA_PROFILE_ID)
        if (!restoring && (id.isNullOrBlank() || token == null)) return START_NOT_STICKY
        if (!restoring && !controller.isCurrentSession(token)) {
            if (!controller.hasSession()) stopSelfResult(startId)
            return if (controller.hasSession()) START_STICKY else START_NOT_STICKY
        }
        val host = activeHost?.takeIf { hostTokens[it] == token } ?: createHost(startId)
        hostStartIds[host] = startId
        hostTokens[host] = token
        activeHost = host
        sessionToken = token
        if (!promoteToForeground()) {
            controller.serviceFailed(host, token)
            return START_NOT_STICKY
        }
        controller.serviceReady(host, id, token, restoring)
        return START_STICKY
    }

    override fun onRevoke() {
        activeHost?.let { controller.serviceStop(it, sessionToken) }
        super.onRevoke()
    }

    override fun onDestroy() {
        hosts.forEach(controller::serviceDestroyed)
        super.onDestroy()
    }

    private fun establishTun(config: VpnPlan): TunHandle {
        val builder =
            Builder()
                .setSession(profileName ?: getString(R.string.notification_channel_name))
                .setMtu(config.mtu)

        config.addresses.forEach { address ->
            builder.addAddress(address.substringBefore('/'), address.substringAfter('/').toInt())
        }
        if (config.allowIpv6Bypass) builder.allowFamily(OsConstants.AF_INET6)
        config.routes.forEach { route ->
            val parts = route.split('/')
            check(parts.size == 2 && parts[1].toInt() in 0..128) { "Invalid VPN route" }
            builder.addRoute(parts[0], parts[1].toInt())
        }
        config.dnsServers.forEach(builder::addDnsServer)
        // 排除本应用，避免 EasyTier 自身传输流量再次进入 VPN TUN。
        val missing =
            config.applications.filter { app ->
                try {
                    packageManager.getApplicationInfo(app, 0)
                    false
                } catch (_: PackageManager.NameNotFoundException) {
                    true
                }
            }
        if (config.applicationMode != ApplicationMode.ALL && missing.isNotEmpty()) throw MissingApplicationException()
        if (config.applicationMode == ApplicationMode.INCLUDE) {
            check(config.applications.isNotEmpty() && packageName !in config.applications)
            config.applications.forEach(builder::addAllowedApplication)
        } else {
            builder.addDisallowedApplication(packageName)
            if (config.applicationMode == ApplicationMode.EXCLUDE) config.applications.forEach(builder::addDisallowedApplication)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        val nextInterface = builder.establish() ?: error("Android refused to establish the VPN interface")
        return AndroidTunHandle(nextInterface)
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
            .setContentText(profileName ?: getString(R.string.notification_running))
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
                    Intent(
                        this,
                        EasyTierVpnService::class.java,
                    ).setAction(ACTION_DISCONNECT).apply { sessionToken?.let { putExtra(EXTRA_SESSION_TOKEN, it) } },
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

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_SESSION_TOKEN = "session_token"
        const val ACTION_CONNECT = "com.github.zzwtsy.easytierkt.action.CONNECT"
        const val ACTION_DISCONNECT = "com.github.zzwtsy.easytierkt.action.DISCONNECT"
        private const val NOTIFICATION_CHANNEL_ID = "easytier_vpn"
        private const val NOTIFICATION_ID = 11010
        private const val OPEN_ACTIVITY_REQUEST_CODE = 1
        private const val DISCONNECT_REQUEST_CODE = 2
    }
}

private class AndroidTunHandle(
    private val descriptor: ParcelFileDescriptor,
) : TunHandle {
    override val fd: Int get() = descriptor.fd

    override fun close() = descriptor.close()
}
