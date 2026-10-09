package com.github.zzwtsy.easytierkt.data.profile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class EncryptedProfileStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val id = UUID.randomUUID().toString()
    private val fileName = "profile-test-$id.bin"
    private val alias = "profile-test-key-$id"
    private val file = File(context.filesDir, "datastore/$fileName")
    private var job = SupervisorJob()
    private var store = createStore()
    private val document =
        ProfileDocument(
            profiles =
                listOf(
                    SavedProfile("a", "A", ConnectionProfile(networkName = "office", networkSecret = "test-secret-only")),
                    SavedProfile("b", "B", ConnectionProfile(networkName = "home")),
                ),
            selectedProfileId = "b",
            resumeProfileId = "a",
        )

    private fun createStore() = EncryptedProfileStore(context, fileName, alias, CoroutineScope(job + Dispatchers.IO))

    private suspend fun closeStore() {
        job.cancelAndJoin()
    }

    private fun reopen() {
        job = SupervisorJob()
        store = createStore()
    }

    @After
    fun cleanup() =
        runBlocking {
            closeStore()
            file.delete()
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                deleteEntry(alias)
            }
            context.deleteSharedPreferences("legacy-test-$id")
            Unit
        }

    /** 两份配置原子写入后关闭旧实例并重建，全部字段恢复且二进制文件不包含明文密钥。 */
    @Test
    fun encryptedCollectionSurvivesStoreRecreation() =
        runBlocking {
            store.write(document)
            closeStore()
            assertFalse(file.readBytes().decodeToString().contains("test-secret-only"))
            reopen()
            assertEquals(document, store.read())
        }

    /** 只有旧 SharedPreferences 时，新存储返回空文档且保留旧文件，不执行兼容读取或迁移。 */
    @Test
    fun absentFileIgnoresAndPreservesLegacyData() =
        runBlocking {
            val prefs = context.getSharedPreferences("legacy-test-$id", Context.MODE_PRIVATE)
            assertTrue(prefs.edit().putString("encrypted_profiles_v3", "old-ciphertext").commit())
            assertEquals(ProfileDocument(), store.read())
            assertEquals("old-ciphertext", prefs.getString("encrypted_profiles_v3", null))
            assertFalse(file.exists())
        }

    /** 已认证密文中任一字节被更改时，读写均失败且原损坏文件不被空文档替换。 */
    @Test
    fun corruptCurrentPayloadIsNotOverwritten() =
        runBlocking {
            store.write(document)
            closeStore()
            val bytes = file.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            file.writeBytes(bytes)
            reopen()
            assertTrue(runCatching { store.read() }.isFailure)
            assertTrue(runCatching { store.write(ProfileDocument()) }.isFailure)
            assertTrue(bytes.contentEquals(file.readBytes()))
        }

    /** 数据文件仍在但 Keystore 密钥已丢失时，读取失败且不创建替代密钥或覆盖原密文。 */
    @Test
    fun missingKeyDoesNotCreateReplacementOrOverwrite() =
        runBlocking {
            store.write(document)
            closeStore()
            val bytes = file.readBytes()
            val keys =
                KeyStore.getInstance("AndroidKeyStore").apply {
                    load(null)
                    deleteEntry(alias)
                }
            reopen()
            assertTrue(runCatching { store.read() }.isFailure)
            assertTrue(runCatching { store.write(ProfileDocument()) }.isFailure)
            assertFalse(keys.containsAlias(alias))
            assertTrue(bytes.contentEquals(file.readBytes()))
        }

    /** 同一实例已缓存文档但密钥随后丢失时，写入仍失败，不用新密钥覆盖可供排查的原密文。 */
    @Test
    fun missingKeyDuringOpenStoreCannotOverwriteCachedDocument() =
        runBlocking {
            store.write(document)
            val bytes = file.readBytes()
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                deleteEntry(alias)
            }
            assertTrue(runCatching { store.write(document.copy(selectedProfileId = "a")) }.isFailure)
            assertTrue(bytes.contentEquals(file.readBytes()))
        }

    /** 结构不合法的新文档在写入前被拒绝，已有配置继续可读且文件内容保持不变。 */
    @Test
    fun invalidDocumentDoesNotReplaceCurrentFile() =
        runBlocking {
            store.write(document)
            val bytes = file.readBytes()
            assertTrue(runCatching { store.write(ProfileDocument(schemaVersion = 3)) }.isFailure)
            assertEquals(document, store.read())
            assertTrue(bytes.contentEquals(file.readBytes()))
        }
}
