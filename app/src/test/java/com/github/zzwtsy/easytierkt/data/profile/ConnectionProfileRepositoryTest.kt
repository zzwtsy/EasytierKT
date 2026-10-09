package com.github.zzwtsy.easytierkt.data.profile

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionProfileRepositoryTest {
    private val config = ConnectionProfile(networkName = "office")

    /** 空存储下依次创建同网络两份配置，验证 ID 不同且仅首份自动选中。 */
    @Test
    fun createsIndependentProfilesAndOnlySelectsFirst() =
        runTest {
            val store = MemoryProfileStore()
            val repository = ConnectionProfileRepository(store, StandardTestDispatcher(testScheduler))
            val first = requireNotNull(repository.create("公司主线路", config).profile)
            val second = requireNotNull(repository.create("公司备用", config).profile)
            assertFalse(first.id == second.id)
            assertEquals(listOf(first, second), store.document.profiles)
            assertEquals(first.id, store.document.selectedProfileId)
        }

    /** 已选中配置更新后 ID 和顺序保持不变，删除该配置后不会自动选择剩余项。 */
    @Test
    fun updatesIdentityAndClearsSelectionOnDeletion() =
        runTest {
            val store = MemoryProfileStore()
            val repository = ConnectionProfileRepository(store, StandardTestDispatcher(testScheduler))
            val first = requireNotNull(repository.create("A", config).profile)
            val second = requireNotNull(repository.create("B", config).profile)
            repository.update(first.id, "改名", config.copy(peerAddresses = "udp://example.com:11010"))
            assertEquals(listOf(first.id, second.id), store.document.profiles.map { it.id })
            assertEquals(
                "改名",
                store.document.profiles
                    .first()
                    .displayName,
            )
            repository.delete(first.id)
            assertEquals(listOf(second), store.document.profiles)
            assertNull(store.document.selectedProfileId)
            assertEquals(ProfileActionError.NOT_FOUND, repository.update(first.id, "A", config).error)
        }

    /** 运行 A 时拒绝切换和删除 A，但允许更新 A 与删除 B，停止完成后重新允许切换。 */
    @Test
    fun locksOnlySessionSelectionAndActiveDeletion() =
        runTest {
            val store = MemoryProfileStore()
            val repository = ConnectionProfileRepository(store, StandardTestDispatcher(testScheduler))
            val first = requireNotNull(repository.create("A", config).profile)
            val second = requireNotNull(repository.create("B", config).profile)
            assertNull(repository.reserveSession(first.id).error)
            assertEquals(ProfileActionError.CONNECTION_BUSY, repository.select(second.id).error)
            assertEquals(ProfileActionError.CONNECTION_BUSY, repository.delete(first.id).error)
            assertNull(repository.update(first.id, "A 改名", config).error)
            assertNull(repository.delete(second.id).error)
            repository.clearResume()
            assertEquals(first.id, repository.state.value.activeProfileId)
            repository.releaseSession()
            assertNull(repository.select(first.id).error)
        }

    /** 保存失败时集合及选中项保留最后成功值，重试成功后只增加一份配置。 */
    @Test
    fun failedWritesDoNotPublishUnpersistedProfiles() =
        runTest {
            val store = MemoryProfileStore()
            val repository = ConnectionProfileRepository(store, StandardTestDispatcher(testScheduler))
            repository.create("A", config)
            val before = repository.state.value.document
            store.failWrite = true
            assertEquals(ProfileActionError.WRITE_FAILED, repository.create("B", config).error)
            assertEquals(before, repository.state.value.document)
            assertEquals(before, store.document)
            store.failWrite = false
            assertNull(repository.create("B", config).error)
            assertEquals(2, store.document.profiles.size)
        }

    /** 读取失败时新增被拒绝且不写入存储，显式重试成功后能读取原配置。 */
    @Test
    fun readFailureCannotOverwriteOriginalData() =
        runTest {
            val original = ProfileDocument(profiles = listOf(SavedProfile("old", "old", config)), selectedProfileId = "old")
            val store = MemoryProfileStore(original).apply { failRead = true }
            val repository = ConnectionProfileRepository(store, StandardTestDispatcher(testScheduler))
            repository.refresh()
            assertEquals(ProfileActionError.READ_FAILED, repository.create("B", config).error)
            assertEquals(0, store.writes)
            assertEquals(original, store.document)
            store.failRead = false
            repository.refresh()
            assertEquals(original, repository.state.value.document)
        }

    /** 同时提交二十个新增操作后，验证每份配置都保存且 ID 唯一。 */
    @Test
    fun concurrentMutationsKeepAllProfiles() =
        runTest {
            val store = MemoryProfileStore()
            val repository = ConnectionProfileRepository(store, StandardTestDispatcher(testScheduler))
            (1..20).map { async { repository.create("配置 $it", config) } }.awaitAll()
            assertEquals(20, store.document.profiles.size)
            assertEquals(
                20,
                store.document.profiles
                    .map { it.id }
                    .distinct()
                    .size,
            )
        }

    /** A 的恢复记录存在且 A 已编辑时，新进程的 Repository 恢复 A 最新参数而不是选中 B。 */
    @Test
    fun restoresOriginalIdWithLatestSavedParameters() =
        runTest {
            val store = MemoryProfileStore()
            val repository = ConnectionProfileRepository(store, StandardTestDispatcher(testScheduler))
            val first = requireNotNull(repository.create("A", config).profile)
            val second = requireNotNull(repository.create("B", config.copy(networkName = "home")).profile)
            repository.reserveSession(first.id)
            repository.update(first.id, "A", config.copy(peerAddresses = "udp://new.example:11010"))
            store.document = store.document.copy(selectedProfileId = second.id)
            val restarted = ConnectionProfileRepository(store, StandardTestDispatcher(testScheduler))
            assertEquals(first.id, restarted.resumableProfileId())
            val resumed = restarted.sessionProfile(first.id)
            assertNull(resumed.error)
            assertEquals("udp://new.example:11010", resumed.profile?.config?.peerAddresses)
            assertEquals(first.id, restarted.state.value.activeProfileId)
        }

    /** 无恢复记录时不能将选中配置当作恢复目标；主动停止后恢复 ID 被清除。 */
    @Test
    fun neverFallsBackToSelectionWithoutRecoveryRecord() =
        runTest {
            val store = MemoryProfileStore()
            val repository = ConnectionProfileRepository(store, StandardTestDispatcher(testScheduler))
            val first = requireNotNull(repository.create("A", config).profile)
            assertNull(repository.resumableProfileId())
            assertEquals(ProfileActionError.NOT_FOUND, repository.sessionProfile(first.id).error)
            repository.reserveSession(first.id)
            repository.clearResume()
            repository.releaseSession()
            assertNull(repository.resumableProfileId())
        }

    /** 损坏文档中的重复 ID、悬空引用及未来版本均拒绝读取，不能当作空列表。 */
    @Test
    fun rejectsInvalidDocumentStructure() {
        val profile = SavedProfile("id", "A", config)
        val invalid =
            listOf(
                ProfileDocument(schemaVersion = 99),
                ProfileDocument(profiles = listOf(profile, profile)),
                ProfileDocument(selectedProfileId = "missing"),
                ProfileDocument(resumeProfileId = "missing"),
            )
        invalid.forEach { document -> assertTrue(runCatching { document.validateStructure() }.isFailure) }
    }

    /** schema 3 不属于当前存储协议，读取失败后拒绝新增且不覆盖原文档。 */
    @Test
    fun obsoleteSchemaBlocksMutationWithoutOverwrite() =
        runTest {
            val document = ProfileDocument(schemaVersion = 3)
            val store = MemoryProfileStore(document)
            val repository = ConnectionProfileRepository(store, StandardTestDispatcher(testScheduler))
            assertEquals(ProfileActionError.READ_FAILED, repository.create("A", config).error)
            assertEquals(document, store.document)
            assertEquals(0, store.writes)
        }

    /** 名称空白或含控制字符以及网络参数无效时，新增失败且存储保持为空。 */
    @Test
    fun validatesNamesAndNetworkParametersBeforeSaving() =
        runTest {
            val store = MemoryProfileStore()
            val repository = ConnectionProfileRepository(store, StandardTestDispatcher(testScheduler))
            assertEquals(ProfileActionError.INVALID_NAME, repository.create("  ", config).error)
            assertEquals(ProfileActionError.INVALID_NAME, repository.create("A\nB", config).error)
            assertEquals(ProfileActionError.INVALID_CONFIG, repository.create("A", ConnectionProfile()).error)
            assertEquals(0, store.writes)
        }
}

internal class MemoryProfileStore(
    var document: ProfileDocument = ProfileDocument(),
) : ProfileStore {
    var failRead = false
    var failWrite = false
    var writes = 0

    override suspend fun read(): ProfileDocument {
        check(!failRead) { "Test read failure" }
        return document
    }

    override suspend fun write(document: ProfileDocument) {
        check(!failWrite) { "Test write failure" }
        this.document = document
        writes += 1
    }
}
