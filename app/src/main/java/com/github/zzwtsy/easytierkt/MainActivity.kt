package com.github.zzwtsy.easytierkt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.github.zzwtsy.easytierkt.data.connection.UnavailableConnectionRepository
import com.github.zzwtsy.easytierkt.navigation.EasytierApp
import com.github.zzwtsy.easytierkt.ui.theme.EasytierKTTheme

class MainActivity : ComponentActivity() {
    private val appContainer by lazy { AppContainer() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EasytierKTTheme {
                EasytierApp(connectionRepository = appContainer.connectionRepository)
            }
        }
    }
}

private class AppContainer {
    val connectionRepository = UnavailableConnectionRepository()
}
