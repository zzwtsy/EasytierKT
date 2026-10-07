package com.github.zzwtsy.easytierkt.data.profile

import android.content.Context
import android.annotation.SuppressLint
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/** 使用 Android Keystore 管理的 AES/GCM 密钥，将 JSON 配置加密后存入应用私有 SharedPreferences。 */
class EncryptedProfileStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /** 读取并解密配置；存储项不存在时返回默认配置，解码或解密失败会抛给调用方。 */
    fun read(): ConnectionProfile {
        val payload = preferences.getString(ENCRYPTED_PROFILE_KEY, null) ?: return ConnectionProfile()
        val decoded = Base64.decode(payload, Base64.NO_WRAP)
        require(decoded.size > IV_LENGTH && decoded[0] == FORMAT_VERSION) { "Invalid encrypted profile" }

        val iv = decoded.copyOfRange(1, 1 + IV_LENGTH)
        val ciphertext = decoded.copyOfRange(1 + IV_LENGTH, decoded.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return json.decodeFromString(cipher.doFinal(ciphertext).decodeToString())
    }

    /** 将配置序列化并加密后同步提交；加密或存储失败会以异常形式传播给调用方。 */
    @SuppressLint("UseKtx")
    fun write(profile: ConnectionProfile) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(json.encodeToString(profile).encodeToByteArray())
        val payload = byteArrayOf(FORMAT_VERSION) + cipher.iv + ciphertext
        val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
        check(preferences.edit().putString(ENCRYPTED_PROFILE_KEY, encoded).commit()) {
            "Failed to persist the encrypted profile"
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
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
        private const val KEY_ALIAS = "com.github.zzwtsy.easytierkt.profile.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FORMAT_VERSION: Byte = 1
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BITS = 128
        private const val KEY_SIZE_BITS = 256
    }
}
