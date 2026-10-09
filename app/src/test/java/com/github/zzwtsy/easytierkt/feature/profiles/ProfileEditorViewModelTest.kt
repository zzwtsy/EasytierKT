package com.github.zzwtsy.easytierkt.feature.profiles

import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfileRepository
import com.github.zzwtsy.easytierkt.data.profile.MemoryProfileStore
import com.github.zzwtsy.easytierkt.data.profile.ProfileActionError
import com.github.zzwtsy.easytierkt.feature.connection.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** 新增页填写有效内容后同一帧重复保存，最终只创建一份配置并发送成功状态。 */
    @Test
    fun repeatedSaveCreatesOnlyOneProfile() =
        runTest {
            val store = MemoryProfileStore()
            val repository = ConnectionProfileRepository(store, StandardTestDispatcher(testScheduler))
            val viewModel = ProfileEditorViewModel(repository, null)
            runCurrent()
            viewModel.updateName("公司")
            viewModel.updateProfile(ConnectionProfile(networkName = "office"))
            assertTrue(viewModel.uiState.value.dirty)
            viewModel.save()
            viewModel.save()
            runCurrent()
            assertEquals(1, store.document.profiles.size)
            assertTrue(viewModel.uiState.value.saved)
            assertFalse(viewModel.uiState.value.dirty)
        }

    /** 已存在的配置保存失败时草稿仍保留，重试后同一 ID 的配置被更新而非新增。 */
    @Test
    fun writeFailureRetainsDraftForRetry() =
        runTest {
            val store = MemoryProfileStore()
            val repository = ConnectionProfileRepository(store, StandardTestDispatcher(testScheduler))
            val saved = requireNotNull(repository.create("A", ConnectionProfile(networkName = "office")).profile)
            val viewModel = ProfileEditorViewModel(repository, saved.id)
            runCurrent()
            viewModel.updateName("改名")
            store.failWrite = true
            viewModel.save()
            runCurrent()
            assertEquals("改名", viewModel.uiState.value.displayName)
            assertEquals(ProfileActionError.WRITE_FAILED, viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.saved)
            store.failWrite = false
            viewModel.save()
            runCurrent()
            assertTrue(viewModel.uiState.value.saved)
            assertEquals(
                saved.id,
                store.document.profiles
                    .single()
                    .id,
            )
            assertEquals(
                "改名",
                store.document.profiles
                    .single()
                    .displayName,
            )
        }

    /** 编辑目标不存在时显示缺失错误，填写并保存也不能重新创建已删除配置。 */
    @Test
    fun missingEditorTargetCannotBeRecreated() =
        runTest {
            val store = MemoryProfileStore()
            val repository = ConnectionProfileRepository(store, StandardTestDispatcher(testScheduler))
            val viewModel = ProfileEditorViewModel(repository, "missing")
            runCurrent()
            assertEquals(ProfileActionError.NOT_FOUND, viewModel.uiState.value.error)
            viewModel.updateName("A")
            viewModel.updateProfile(ConnectionProfile(networkName = "office"))
            viewModel.save()
            runCurrent()
            assertTrue(store.document.profiles.isEmpty())
        }
}
