package com.github.zzwtsy.easytierkt.data.profile

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 使用 Android Keystore 管理的 AES/GCM 密钥，将 JSON 配置加密后存入应用私有 SharedPreferences。 */
class EncryptedProfileStore(
    context: Context,
    preferencesName: String = PREFERENCES_NAME,
    private val keyAlias: String = KEY_ALIAS,
) : ProfileStore {
    private val preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /** 优先读取集合；旧记录仅在集合缺失时迁移，迁移失败不清理旧数据。 */
    override fun read(): ProfileDocument {
        readCollection()?.let { return it }
        val legacy = preferences.getString(ENCRYPTED_PROFILE_KEY, null) ?: return ProfileDocument()
        return migrateLegacyRecord(
            legacy = json.decodeFromString<ConnectionProfile>(decrypt(legacy)),
            writeNew = ::write,
            readNew = { requireNotNull(readCollection()) },
            removeOld = ::removeLegacy,
        )
    }

    private fun readCollection(): ProfileDocument? =
        preferences.getString(ENCRYPTED_PROFILES_KEY, null)?.let { payload ->
            json.decodeFromString<ProfileDocument>(decrypt(payload)).also { it.validateStructure() }
        }

    private fun decrypt(payload: String): String {
        val decoded = Base64.decode(payload, Base64.NO_WRAP)
        require(decoded.size > 1 + IV_LENGTH && decoded[0] == FORMAT_VERSION) { "Invalid encrypted profile" }
        val iv = decoded.copyOfRange(1, 1 + IV_LENGTH)
        val ciphertext = decoded.copyOfRange(1 + IV_LENGTH, decoded.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext).decodeToString()
    }

    /** 同步提交整个集合；不改变旧密钥或 AES/GCM 封装版本。 */
    @SuppressLint("UseKtx")
    override fun write(document: ProfileDocument) {
        document.validateStructure()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(json.encodeToString(document).encodeToByteArray())
        val payload = byteArrayOf(FORMAT_VERSION) + cipher.iv + ciphertext
        val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
        val previous = preferences.getString(ENCRYPTED_PROFILES_KEY, null)
        if (!preferences.edit().putString(ENCRYPTED_PROFILES_KEY, encoded).commit()) {
            // commit 失败也可能已更新 SharedPreferences 的内存，恢复旧值避免 refresh 暴露未保存集合。
            preferences.edit().putString(ENCRYPTED_PROFILES_KEY, previous).apply()
            error("Failed to persist encrypted profiles")
        }
    }

    @SuppressLint("UseKtx")
    private fun removeLegacy() {
        // 清理失败时 v2 仍是读取来源，不再次迁移或生成新 ID。
        preferences.edit().remove(ENCRYPTED_PROFILE_KEY).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val PREFERENCES_NAME = "easytier_profile"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ENCRYPTED_PROFILE_KEY = "encrypted_profile_v1"
        private const val ENCRYPTED_PROFILES_KEY = "encrypted_profiles_v2"
        private const val KEY_ALIAS = "com.github.zzwtsy.easytierkt.profile.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FORMAT_VERSION: Byte = 1
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BITS = 128
        private const val KEY_SIZE_BITS = 256
    }
}
