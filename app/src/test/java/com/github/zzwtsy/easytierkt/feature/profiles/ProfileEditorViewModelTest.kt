package com.github.zzwtsy.easytierkt.feature.profiles

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import com.github.zzwtsy.easytierkt.data.profile.AclChain
import com.github.zzwtsy.easytierkt.data.profile.AclOptions
import com.github.zzwtsy.easytierkt.data.profile.AclRule
import com.github.zzwtsy.easytierkt.data.profile.ApplicationsRepository
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfileRepository
import com.github.zzwtsy.easytierkt.data.profile.InstalledApplication
import com.github.zzwtsy.easytierkt.data.profile.MemoryProfileStore
import com.github.zzwtsy.easytierkt.data.profile.ProfileActionError
import com.github.zzwtsy.easytierkt.data.profile.SecureIdentity
import com.github.zzwtsy.easytierkt.data.profile.SecureIdentityProvider
import com.github.zzwtsy.easytierkt.data.profile.SecurityOptions
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

    /** TextFieldState 修改与保存发生在同一帧时，保存同步读取最新输入，不等待 snapshotFlow。 */
    @Test
    fun sameFrameTextEditIsUsedBySave() =
        runTest {
            val store = MemoryProfileStore()
            val vm = ProfileEditorViewModel(ConnectionProfileRepository(store, StandardTestDispatcher(testScheduler)), null)
            runCurrent()
            vm.draft.state("displayName").setTextAndPlaceCursorAtEnd("立即保存")
            vm.draft.state("networkName").setTextAndPlaceCursorAtEnd("latest")
            vm.draft.state("transport.mtu").setTextAndPlaceCursorAtEnd("1350")
            vm.save()
            runCurrent()
            assertEquals(
                "latest",
                store.document.profiles
                    .single()
                    .config.networkName,
            )
            assertEquals(
                1350,
                store.document.profiles
                    .single()
                    .config.transport.mtu,
            )
            assertTrue(vm.uiState.value.saved)
        }

    /** 文本编辑尚未进入页面投影时切换开关，结构更新保留同一帧原始输入，不能用旧投影覆盖文字。 */
    @Test
    fun sameFrameTogglePreservesLatestTextState() =
        runTest {
            val vm = ProfileEditorViewModel(ConnectionProfileRepository(MemoryProfileStore(), StandardTestDispatcher(testScheduler)), null)
            runCurrent()
            val rendered = vm.uiState.value.profile
            vm.draft.state("networkName").setTextAndPlaceCursorAtEnd("latest")
            vm.updateProfile(rendered.copy(useDhcp = false))
            assertEquals(
                "latest",
                vm.draft
                    .state("networkName")
                    .text
                    .toString(),
            )
            assertEquals("latest", vm.uiState.value.profile.networkName)
            assertFalse(vm.uiState.value.profile.useDhcp)
        }

    /** 动态条目新增后原始文本状态被订阅，名称和非法数字立即反映在校验中；删除条目清理其输入。 */
    @Test
    fun newRuleTextIsObservedAndDeletedRuleCannotBlockSave() =
        runTest {
            val vm = ProfileEditorViewModel(ConnectionProfileRepository(MemoryProfileStore(), StandardTestDispatcher(testScheduler)), null)
            Snapshot.sendApplyNotifications()
            runCurrent()
            val rule = AclRule(name = "old")
            val chain = AclChain(name = "in", rules = listOf(rule))
            vm.updateProfile(ConnectionProfile(networkName = "test", acl = AclOptions(enabled = true, chains = listOf(chain))))
            Snapshot.sendApplyNotifications()
            runCurrent()
            vm.draft.state("rule.${rule.id}.name").setTextAndPlaceCursorAtEnd("new")
            vm.draft.state("rule.${rule.id}.priority").setTextAndPlaceCursorAtEnd("bad")
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertEquals(
                "new",
                vm.uiState.value.profile.acl.chains
                    .single()
                    .rules
                    .single()
                    .name,
            )
            assertTrue(
                vm.uiState.value.invalidNumbers
                    .isNotEmpty(),
            )
            vm.updateProfile(
                vm.uiState.value.profile
                    .copy(acl = AclOptions(enabled = true)),
            )
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertTrue(
                vm.uiState.value.invalidNumbers
                    .isEmpty(),
            )
            assertTrue(
                vm.draft
                    .rawValues()
                    .keys
                    .none { it.path.startsWith("rule.${rule.id}") },
            )
        }

    /** 原始非法数字计入 dirty；关闭选项保存时采用加载值，而非此前输入的合法值，重新开启仍校验原文本。 */
    @Test
    fun disabledInvalidNumberUsesLoadedValueAndRawTextStaysDirty() =
        runTest {
            val store = MemoryProfileStore()
            val repo = ConnectionProfileRepository(store, StandardTestDispatcher(testScheduler))
            val saved = requireNotNull(repo.create("A", ConnectionProfile(networkName = "test")).profile)
            val vm = ProfileEditorViewModel(repo, saved.id)
            runCurrent()
            vm.updateNumericDraft("transport.threadCount", "8")
            vm.updateNumericDraft("transport.threadCount", "bad")
            assertTrue(vm.uiState.value.dirty)
            vm.updateProfile(
                vm.uiState.value.profile
                    .copy(
                        transport =
                            vm.uiState.value.profile.transport
                                .copy(multiThread = false),
                    ),
            )
            assertEquals(2, vm.uiState.value.profile.transport.threadCount)
            assertTrue(
                vm.uiState.value.invalidNumbers
                    .isEmpty(),
            )
            vm.updateProfile(
                vm.uiState.value.profile
                    .copy(
                        transport =
                            vm.uiState.value.profile.transport
                                .copy(multiThread = true),
                    ),
            )
            assertTrue(
                vm.uiState.value.invalidNumbers
                    .isNotEmpty(),
            )
            assertEquals(
                "bad",
                vm.draft
                    .state("transport.threadCount")
                    .text
                    .toString(),
            )
        }

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

    /** 数值框处于非整数草稿时保存无效，修正草稿后才能写入仓库，避免静默保存之前的数值。 */
    @Test
    fun invalidNumericDraftBlocksSaving() =
        runTest {
            val store = MemoryProfileStore()
            val viewModel = ProfileEditorViewModel(ConnectionProfileRepository(store, StandardTestDispatcher(testScheduler)), null)
            runCurrent()
            viewModel.updateName("配置")
            viewModel.updateProfile(ConnectionProfile(networkName = "test"))
            viewModel.updateNumericDraft("transport.mtu", "abc")
            viewModel.save()
            runCurrent()
            assertTrue(store.document.profiles.isEmpty())
            assertEquals(
                "abc",
                viewModel.draft
                    .state("transport.mtu")
                    .text
                    .toString(),
            )
            viewModel.updateNumericDraft("transport.mtu", "1300")
            viewModel.save()
            runCurrent()
            assertEquals(1, store.document.profiles.size)
        }

    /** 安全模式第一次保存失败后保留已生成身份，重试派生同一公钥，避免改变需要授权的客户端身份。 */
    @Test
    fun failedSavePreservesGeneratedIdentityForRetry() =
        runTest {
            val store = MemoryProfileStore()
            val privateKey =
                java.util.Base64
                    .getEncoder()
                    .encodeToString(ByteArray(32) { 7 })
            val publicKey =
                java.util.Base64
                    .getEncoder()
                    .encodeToString(ByteArray(32) { 8 })
            val inputs = mutableListOf<String>()
            val provider =
                SecureIdentityProvider { input ->
                    inputs += input
                    com.github.zzwtsy.easytierkt.data.profile
                        .SecureIdentity(input.ifBlank { privateKey }, publicKey)
                }
            val viewModel =
                ProfileEditorViewModel(ConnectionProfileRepository(store, StandardTestDispatcher(testScheduler)), null, provider)
            runCurrent()
            viewModel.updateName("安全配置")
            viewModel.updateProfile(
                ConnectionProfile(
                    networkName = "test",
                    security =
                        com.github.zzwtsy.easytierkt.data.profile
                            .SecurityOptions(secureMode = true),
                ),
            )
            store.failWrite = true
            viewModel.save()
            runCurrent()
            assertEquals(privateKey, viewModel.uiState.value.profile.security.privateKey)
            store.failWrite = false
            viewModel.save()
            runCurrent()
            assertEquals(listOf("", privateKey), inputs)
            assertEquals(
                publicKey,
                store.document.profiles
                    .single()
                    .config.security.publicKey,
            )
        }

    /** 私钥文件内容错误时返回类型化导入错误，原身份与其他草稿字段均保持不变。 */
    @Test
    fun invalidImportPreservesDraft() =
        runTest {
            val viewModel =
                ProfileEditorViewModel(ConnectionProfileRepository(MemoryProfileStore(), StandardTestDispatcher(testScheduler)), null)
            runCurrent()
            val draft = ConnectionProfile(networkName = "test", hostname = "my-node")
            viewModel.updateProfile(draft)
            viewModel.importIdentity { "invalid key" }
            runCurrent()
            assertEquals(draft, viewModel.uiState.value.profile)
            assertEquals(IdentityError.IMPORT_FAILED, viewModel.uiState.value.identityError)
            assertFalse(viewModel.uiState.value.identityBusy)
        }

    /** 应用列表加载失败可重试；业务草稿不受应用查询失败影响。 */
    @Test
    fun applicationLoadFailureCanRetry() =
        runTest {
            var fail = true
            val source =
                ApplicationsRepository {
                    if (fail) error("test")
                    listOf(
                        com.github.zzwtsy.easytierkt.data.profile
                            .InstalledApplication("test.app", "测试应用"),
                    )
                }
            val viewModel =
                ProfileEditorViewModel(
                    ConnectionProfileRepository(MemoryProfileStore(), StandardTestDispatcher(testScheduler)),
                    null,
                    applicationsRepository = source,
                )
            runCurrent()
            assertTrue(viewModel.uiState.value.applicationsFailed)
            fail = false
            viewModel.reloadApplications()
            runCurrent()
            assertFalse(viewModel.uiState.value.applicationsFailed)
            assertEquals(
                "test.app",
                viewModel.uiState.value.applications
                    .single()
                    .packageName,
            )
        }

    /** 关闭 ACL 后忽略其非法数值草稿，重新开启时恢复阻止保存，不能静默丢失用户输入。 */
    @Test
    fun disabledOptionsRestoreNumericValidationWhenReenabled() =
        runTest {
            val viewModel =
                ProfileEditorViewModel(ConnectionProfileRepository(MemoryProfileStore(), StandardTestDispatcher(testScheduler)), null)
            runCurrent()
            val rule = AclRule(name = "allow")
            val draft =
                ConnectionProfile(
                    networkName = "test",
                    acl = AclOptions(enabled = true, chains = listOf(AclChain(name = "in", rules = listOf(rule)))),
                )
            viewModel.updateProfile(draft)
            viewModel.updateNumericDraft("rule.${rule.id}.priority", "bad")
            assertTrue(
                viewModel.uiState.value.invalidNumbers
                    .isNotEmpty(),
            )
            viewModel.updateProfile(draft.copy(acl = draft.acl.copy(enabled = false)))
            assertTrue(
                viewModel.uiState.value.invalidNumbers
                    .isEmpty(),
            )
            viewModel.updateProfile(draft)
            assertTrue(
                viewModel.uiState.value.invalidNumbers
                    .isNotEmpty(),
            )
            assertEquals(
                "bad",
                viewModel.draft
                    .state("rule.${rule.id}.priority")
                    .text
                    .toString(),
            )
        }
}
