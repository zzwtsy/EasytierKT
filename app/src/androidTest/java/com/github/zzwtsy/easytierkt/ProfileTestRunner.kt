package com.github.zzwtsy.easytierkt

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.github.zzwtsy.easytierkt.data.connection.ConnectionPhase
import com.github.zzwtsy.easytierkt.data.connection.ConnectionRepository
import com.github.zzwtsy.easytierkt.data.connection.ConnectionStatus
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfileRepository
import com.github.zzwtsy.easytierkt.data.profile.ProfileDocument
import com.github.zzwtsy.easytierkt.data.profile.ProfileStore
import com.github.zzwtsy.easytierkt.data.profile.SavedProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

/** UI 测试用内存配置和连接替身，不读写用户配置、不启动真实 VPN。 */
class ProfileTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader,
        className: String,
        context: Context,
    ): Application = super.newApplication(cl, TestEasytierApplication::class.java.name, context)
}

class TestEasytierApplication : EasytierApplication() {
    private var testContainer: AppContainer? = null
    lateinit var store: TestProfileStore
    lateinit var connections: TestConnectionRepository
    override val appContainer: AppContainer get() = testContainer ?: reset()

    fun reset(): AppContainer {
        store =
            TestProfileStore(
                ProfileDocument(
                    profiles =
                        listOf(
                            SavedProfile("a", "公司", ConnectionProfile(networkName = "office")),
                            SavedProfile("b", "家里", ConnectionProfile(networkName = "home")),
                        ),
                    selectedProfileId = "a",
                ),
            )
        connections = TestConnectionRepository()
        val profiles = ConnectionProfileRepository(store)
        runBlocking { profiles.refresh() }
        return AppContainer(profiles, connections).also { testContainer = it }
    }
}

class TestProfileStore(
    var document: ProfileDocument,
) : ProfileStore {
    var failRead = false
    var failWrite = false

    override suspend fun read(): ProfileDocument {
        check(!failRead) { "Test read failure" }
        return document
    }

    override suspend fun write(document: ProfileDocument) {
        check(!failWrite) { "Test write failure" }
        this.document = document
    }
}

class TestConnectionRepository : ConnectionRepository {
    override val status = MutableStateFlow(ConnectionStatus())

    override fun connect(profileId: String) {
        status.value = ConnectionStatus(phase = ConnectionPhase.CONNECTED, profileId = profileId)
    }

    override fun disconnect() {
        status.value = ConnectionStatus()
    }

    override fun reportVpnPermissionDenied() = Unit
}
