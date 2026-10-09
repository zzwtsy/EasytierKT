package com.github.zzwtsy.easytierkt.data.connection

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfileRepository
import com.github.zzwtsy.easytierkt.data.profile.ProfileActionError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal object ConnectionRuntime {
    private val mutableStatus = MutableStateFlow(ConnectionStatus())
    val status = mutableStatus.asStateFlow()

    fun update(status: ConnectionStatus) {
        mutableStatus.value = status
    }
}

/** 应用级队列保证快速连接、取消和再次连接不会交错修改预留记录。 */
class AndroidConnectionRepository(
    context: Context,
    private val profiles: ConnectionProfileRepository,
    scope: CoroutineScope,
) : ConnectionRepository {
    private val appContext = context.applicationContext
    private val commands = Channel<Command>(Channel.UNLIMITED)
    override val status = ConnectionRuntime.status

    init {
        scope.launch {
            for (command in commands) {
                when (command) {
                    is Command.Connect -> start(command.id)
                    Command.Disconnect -> stop()
                }
            }
        }
    }

    override fun connect(profileId: String) {
        commands.trySend(Command.Connect(profileId))
    }

    override fun disconnect() {
        commands.trySend(Command.Disconnect)
    }

    override fun reportVpnPermissionDenied() {
        if (!status.value.phase.isBusy) {
            ConnectionRuntime.update(ConnectionStatus(phase = ConnectionPhase.ERROR, error = ConnectionError.VPN_PERMISSION_DENIED))
        }
    }

    private suspend fun start(id: String) {
        if (status.value.phase.isBusy) return
        val result = profiles.reserveSession(id)
        if (result.error == ProfileActionError.CONNECTION_BUSY) return
        if (result.error != null) {
            ConnectionRuntime.update(
                ConnectionStatus(phase = ConnectionPhase.ERROR, profileId = id, error = result.error.connectionError()),
            )
            return
        }
        val profile = requireNotNull(result.profile)
        ConnectionRuntime.update(
            ConnectionStatus(phase = ConnectionPhase.STARTING, profileId = profile.id, profileName = profile.displayName),
        )
        val intent =
            Intent(appContext, EasyTierVpnService::class.java)
                .setAction(EasyTierVpnService.ACTION_CONNECT)
                .putExtra(EasyTierVpnService.EXTRA_PROFILE_ID, id)
        try {
            ContextCompat.startForegroundService(appContext, intent)
        } catch (_: SecurityException) {
            startFailed()
        } catch (_: IllegalStateException) {
            startFailed()
        }
    }

    private suspend fun startFailed() {
        val error = profiles.clearResume().error
        profiles.releaseSession()
        ConnectionRuntime.update(
            ConnectionStatus(
                phase = ConnectionPhase.ERROR,
                error = error?.connectionError() ?: ConnectionError.START_FAILED,
            ),
        )
    }

    private suspend fun stop() {
        if (!status.value.phase.isBusy) return
        val previous = status.value
        ConnectionRuntime.update(previous.copy(phase = ConnectionPhase.STOPPING, error = null))
        val intent = Intent(appContext, EasyTierVpnService::class.java).setAction(EasyTierVpnService.ACTION_DISCONNECT)
        try {
            appContext.startService(intent)
        } catch (_: SecurityException) {
            stopFailed(previous)
        } catch (_: IllegalStateException) {
            stopFailed(previous)
        }
    }

    private fun stopFailed(previous: ConnectionStatus) {
        // 请求未送达时不能假定服务已停止，更不能释放运行配置允许启动另一个网络。
        ConnectionRuntime.update(previous.copy(error = ConnectionError.STOP_FAILED))
    }

    private sealed interface Command {
        data class Connect(
            val id: String,
        ) : Command

        data object Disconnect : Command
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
