package com.github.zzwtsy.easytierkt.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.github.zzwtsy.easytierkt.data.connection.ConnectionRepository
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfileRepository
import com.github.zzwtsy.easytierkt.feature.connection.ConnectionRoute
import com.github.zzwtsy.easytierkt.feature.profiles.ProfileEditorRoute
import com.github.zzwtsy.easytierkt.feature.profiles.ProfilesRoute
import kotlinx.serialization.Serializable

@Serializable
private data object ConnectionKey : NavKey

@Serializable
private data object SettingsKey : NavKey

@Serializable
private data object ProfilesKey : NavKey

@Serializable
private data class ProfileEditorKey(
    val profileId: String?,
) : NavKey

@Composable
internal fun EasytierApp(
    connectionRepository: ConnectionRepository,
    profileRepository: ConnectionProfileRepository,
) {
    val backStack = rememberNavBackStack(ConnectionKey)
    var showSaved by rememberSaveable { mutableStateOf(false) }
    val openEditor: (String?) -> Unit = { id ->
        val key = ProfileEditorKey(id)
        if (backStack.lastOrNull() != key) backStack.add(key)
    }
    val profilesContent: @Composable () -> Unit = {
        ProfilesRoute(
            repository = profileRepository,
            onBack = { backStack.removeLastOrNull() },
            onCreate = { openEditor(null) },
            onEdit = { openEditor(it) },
            showSaved = showSaved,
            onSavedShown = { showSaved = false },
        )
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        // M3 转场：前进/后退沿水平轴滑动并伴随淡入淡出。
        transitionSpec = {
            (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                (slideOutHorizontally { -it / 4 } + fadeOut())
        },
        popTransitionSpec = {
            (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                (slideOutHorizontally { it / 4 } + fadeOut())
        },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<ConnectionKey> {
                    ConnectionRoute(
                        repository = connectionRepository,
                        profileRepository = profileRepository,
                        onOpenSettings = { if (backStack.lastOrNull() == ConnectionKey) backStack.add(ProfilesKey) },
                    )
                }
                // 旧版本保存的设置返回栈恢复到配置列表，不恢复已移除的单配置表单。
                entry<SettingsKey> { profilesContent() }
                entry<ProfilesKey> { profilesContent() }
                entry<ProfileEditorKey> { key ->
                    ProfileEditorRoute(
                        repository = profileRepository,
                        profileId = key.profileId,
                        onBack = { if (backStack.lastOrNull() == key) backStack.removeLastOrNull() },
                        onSaved = {
                            if (backStack.lastOrNull() == key) {
                                showSaved = true
                                backStack.removeLastOrNull()
                            }
                        },
                    )
                }
            },
    )
}
