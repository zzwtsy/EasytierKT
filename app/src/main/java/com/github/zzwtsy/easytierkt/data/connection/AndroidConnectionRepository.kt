package com.github.zzwtsy.easytierkt.data.connection

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfileRepository
import com.github.zzwtsy.easytierkt.data.profile.ProfileActionError
import kotlinx.coroutines.CoroutineScope

/** UI 控制适配器；状态和业务命令统一委托给应用级会话控制器。 */
class AndroidConnectionRepository(
    context: Context,
    profiles: ConnectionProfileRepository,
    scope: CoroutineScope,
) : ConnectionRepository {
    internal val controller = VpnSessionController(profiles, scope, AndroidVpnServiceLauncher(context.applicationContext))
    override val status = controller.status

    override fun connect(profileId: String) = controller.connect(profileId)

    override fun disconnect() = controller.disconnect()

    override fun reportVpnPermissionDenied() = controller.permissionDenied()
}

internal class AndroidVpnServiceLauncher(
    private val context: Context,
) : VpnServiceLauncher {
    override fun connect(
        id: String,
        token: Long,
    ) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, EasyTierVpnService::class.java)
                .setAction(EasyTierVpnService.ACTION_CONNECT)
                .putExtra(EasyTierVpnService.EXTRA_PROFILE_ID, id)
                .putExtra(EasyTierVpnService.EXTRA_SESSION_TOKEN, token),
        )
    }

    override fun disconnect(token: Long) {
        context.startService(
            Intent(context, EasyTierVpnService::class.java)
                .setAction(EasyTierVpnService.ACTION_DISCONNECT)
                .putExtra(EasyTierVpnService.EXTRA_SESSION_TOKEN, token),
        )
    }
}

internal fun ProfileActionError.connectionError(): ConnectionError =
    when (this) {
        ProfileActionError.READ_FAILED -> ConnectionError.PROFILE_READ_FAILED
        ProfileActionError.WRITE_FAILED -> ConnectionError.PROFILE_WRITE_FAILED
        ProfileActionError.NOT_FOUND -> ConnectionError.PROFILE_NOT_FOUND
        ProfileActionError.INVALID_CONFIG, ProfileActionError.INVALID_NAME -> ConnectionError.PROFILE_INVALID
        ProfileActionError.CONNECTION_BUSY -> ConnectionError.START_FAILED
    }
