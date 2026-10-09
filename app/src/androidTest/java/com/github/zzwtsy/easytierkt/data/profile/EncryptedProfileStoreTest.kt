package com.github.zzwtsy.easytierkt.data.profile

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey

@RunWith(AndroidJUnit4::class)
class EncryptedProfileStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val id = UUID.randomUUID().toString()
    private val preferencesName = "profile-test-$id"
    private val keyAlias = "profile-test-key-$id"
    private val preferences get() = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private lateinit var store: EncryptedProfileStore

    @Before
    fun prepare() {
        store = EncryptedProfileStore(context, preferencesName, keyAlias)
        store.write(ProfileDocument())
        check(preferences.edit().clear().commit())
    }

    @After
    fun cleanup() {
        context.deleteSharedPreferences(preferencesName)
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            deleteEntry(keyAlias)
        }
    }

    /** 保存两份配置后重建存储对象，验证配置、选中项和恢复 ID 完整恢复且存储值不含明文密钥。 */
    @Test
    fun encryptedCollectionSurvivesStoreRecreation() {
        val document =
            ProfileDocument(
                profiles =
                    listOf(
                        SavedProfile("a", "A", ConnectionProfile(networkName = "office", networkSecret = "test-secret-only")),
                        SavedProfile("b", "B", ConnectionProfile(networkName = "home")),
                    ),
                selectedProfileId = "b",
                resumeProfileId = "a",
            )
        store.write(document)
        assertEquals(document, EncryptedProfileStore(context, preferencesName, keyAlias).read())
        assertFalse(preferences.getString("encrypted_profiles_v2", null).orEmpty().contains("test-secret-only"))
    }

    /** 存在单配置密文时迁移并清理旧 key，第二次读取保持同一 ID 且不重复迁移。 */
    @Test
    fun legacyMigrationIsPersistentAndIdempotent() {
        val legacy = ConnectionProfile(networkName = "office", networkSecret = "migration-test-secret", routes = "192.168.1.0/24")
        preferences.edit().putString("encrypted_profile_v1", encrypt(Json.encodeToString(legacy))).commit()
        val first = store.read()
        assertEquals(legacy, first.profiles.single().config)
        assertEquals(first.profiles.single().id, first.selectedProfileId)
        assertEquals(first, EncryptedProfileStore(context, preferencesName, keyAlias).read())
        assertFalse(preferences.contains("encrypted_profile_v1"))
        assertNotNull(preferences.getString("encrypted_profiles_v2", null))
    }

    /** 不完整旧配置仍被保留，迁移不会丢弃用户已填写的字段。 */
    @Test
    fun incompleteLegacyRecordIsPreserved() {
        val legacy = ConnectionProfile(networkSecret = "draft-test-secret", useDhcp = false)
        preferences.edit().putString("encrypted_profile_v1", encrypt(Json.encodeToString(legacy))).commit()
        val migrated = store.read()
        assertEquals(legacy, migrated.profiles.single().config)
        assertEquals("默认配置", migrated.profiles.single().displayName)
    }

    /** 旧密文损坏时读取失败，旧记录保留且不生成空集合覆盖。 */
    @Test
    fun corruptLegacyPayloadIsNotOverwritten() {
        preferences.edit().putString("encrypted_profile_v1", "corrupt").commit()
        assertTrue(runCatching { store.read() }.isFailure)
        assertEquals("corrupt", preferences.getString("encrypted_profile_v1", null))
        assertFalse(preferences.contains("encrypted_profiles_v2"))
    }

    /** v2 已存在但版本不支持时读取失败，即使 v1 有效也不回退至过期配置。 */
    @Test
    fun unsupportedNewDocumentDoesNotFallBackToLegacy() {
        preferences
            .edit()
            .putString("encrypted_profile_v1", encrypt(Json.encodeToString(ConnectionProfile(networkName = "old"))))
            .putString("encrypted_profiles_v2", encrypt(Json.encodeToString(ProfileDocument(schemaVersion = 3))))
            .commit()
        assertTrue(runCatching { store.read() }.isFailure)
        assertTrue(preferences.contains("encrypted_profile_v1"))
    }

    private fun encrypt(json: String): String {
        val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(keyAlias, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(json.encodeToByteArray())
        return Base64.encodeToString(byteArrayOf(1) + cipher.iv + ciphertext, Base64.NO_WRAP)
    }
}
