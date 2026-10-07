package com.github.zzwtsy.easytierkt.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.github.zzwtsy.easytierkt.data.connection.ConnectionRepository
import com.github.zzwtsy.easytierkt.feature.connection.ConnectionRoute
import com.github.zzwtsy.easytierkt.feature.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
private data object ConnectionKey : NavKey

@Serializable
private data object SettingsKey : NavKey

@Composable
internal fun EasytierApp(connectionRepository: ConnectionRepository) {
    val backStack = rememberNavBackStack(ConnectionKey)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<ConnectionKey> {
                ConnectionRoute(
                    repository = connectionRepository,
                    onOpenSettings = { backStack.add(SettingsKey) },
                )
            }
            entry<SettingsKey> {
                SettingsScreen(onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}
