package com.github.zzwtsy.easytierkt

import android.app.Application
import android.content.Context
import com.github.zzwtsy.easytierkt.data.connection.AndroidConnectionRepository
import com.github.zzwtsy.easytierkt.data.connection.ConnectionRepository
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfileRepository
import com.github.zzwtsy.easytierkt.data.profile.EncryptedProfileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Activity 与 VPN 服务共享同一个配置集合、会话预留和连接命令队列。 */
open class EasytierApplication : Application() {
    open val appContainer: AppContainer by lazy { AppContainer.create(this) }
}

class AppContainer(
    val profileRepository: ConnectionProfileRepository,
    val connectionRepository: ConnectionRepository,
) {
    internal val sessionController get() = (connectionRepository as? AndroidConnectionRepository)?.controller

    companion object {
        fun create(context: Context): AppContainer {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val profiles = ConnectionProfileRepository(EncryptedProfileStore(context))
            val connections = AndroidConnectionRepository(context, profiles, scope)
            scope.launch { profiles.refresh() }
            return AppContainer(profiles, connections)
        }
    }
}
