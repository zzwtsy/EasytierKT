package com.github.zzwtsy.easytierkt.data.connection

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object ConnectionRuntime {
    private val mutableStatus = MutableStateFlow(ConnectionStatus())
    val status = mutableStatus.asStateFlow()

    fun update(status: ConnectionStatus) {
        mutableStatus.value = status
    }
}

class AndroidConnectionRepository(
    context: Context,
) : ConnectionRepository {
    private val appContext = context.applicationContext

    override val status = ConnectionRuntime.status

    override fun connect() {
        ConnectionRuntime.update(ConnectionStatus(phase = ConnectionPhase.STARTING))
        val intent =
            Intent(appContext, EasyTierVpnService::class.java)
                .setAction(EasyTierVpnService.ACTION_CONNECT)

        try {
            ContextCompat.startForegroundService(appContext, intent)
        } catch (_: SecurityException) {
            ConnectionRuntime.update(ConnectionStatus(phase = ConnectionPhase.ERROR, error = ConnectionError.START_FAILED))
        } catch (_: IllegalStateException) {
            ConnectionRuntime.update(ConnectionStatus(phase = ConnectionPhase.ERROR, error = ConnectionError.START_FAILED))
        }
    }

    override fun disconnect() {
        ConnectionRuntime.update(status.value.copy(phase = ConnectionPhase.STOPPING, error = null))
        val intent =
            Intent(appContext, EasyTierVpnService::class.java)
                .setAction(EasyTierVpnService.ACTION_DISCONNECT)
        try {
            appContext.startService(intent)
        } catch (_: SecurityException) {
            ConnectionRuntime.update(ConnectionStatus())
        } catch (_: IllegalStateException) {
            ConnectionRuntime.update(ConnectionStatus())
        }
    }

    override fun reportVpnPermissionDenied() {
        ConnectionRuntime.update(
            ConnectionStatus(
                phase = ConnectionPhase.ERROR,
                error = ConnectionError.VPN_PERMISSION_DENIED,
            ),
        )
    }
}
