package com.github.zzwtsy.easytierkt

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.github.zzwtsy.easytierkt.data.connection.AndroidConnectionRepository
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfileRepository
import com.github.zzwtsy.easytierkt.data.profile.EncryptedProfileStore
import com.github.zzwtsy.easytierkt.navigation.EasytierApp
import com.github.zzwtsy.easytierkt.ui.theme.EasytierKTTheme

class MainActivity : ComponentActivity() {
    private val appContainer by lazy { AppContainer(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EasytierKTTheme {
                EasytierApp(
                    connectionRepository = appContainer.connectionRepository,
                    profileRepository = appContainer.profileRepository,
                )
            }
        }
    }
}

private class AppContainer(
    context: Context,
) {
    private val profileStore = EncryptedProfileStore(context)

    val connectionRepository = AndroidConnectionRepository(context)
    val profileRepository = ConnectionProfileRepository(profileStore)
}
