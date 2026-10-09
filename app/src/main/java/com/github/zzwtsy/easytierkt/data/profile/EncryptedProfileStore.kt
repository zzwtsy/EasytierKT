package com.github.zzwtsy.easytierkt.data.profile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 单应用实例的类型化 DataStore；文件原子替换由库负责，损坏或丢失密钥时拒绝覆盖。 */
class EncryptedProfileStore(
    context: Context,
    fileName: String = FILE_NAME,
    keyAlias: String = KEY_ALIAS,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ProfileStore {
    private val file = File(context.applicationContext.filesDir, "datastore/$fileName")
    private val dataStore =
        DataStoreFactory.create(
            serializer = EncryptedProfileSerializer(keyAlias, file),
            scope = scope,
            produceFile = { file },
        )

    override suspend fun read(): ProfileDocument = dataStore.data.first()

    override suspend fun write(document: ProfileDocument) {
        document.validateStructure()
        dataStore.updateData { document }
    }

    companion object {
        const val FILE_NAME = "profiles_v4.bin"
        private const val KEY_ALIAS = "com.github.zzwtsy.easytierkt.profile.v4"
    }
}

/** 二进制信封为版本、12 字节随机 IV、密文及认证标签；AAD 将密文绑定到当前协议。 */
internal class EncryptedProfileSerializer(
    private val keyAlias: String,
    private val file: File,
) : Serializer<ProfileDocument> {
    override val defaultValue = ProfileDocument()
    private val json = Json { encodeDefaults = true }

    override suspend fun readFrom(input: InputStream): ProfileDocument {
        val payload = input.readBytes()
        require(payload.size >= 1 + IV_LENGTH + TAG_LENGTH_BITS / 8 && payload[0] == FORMAT_VERSION) {
            "Invalid encrypted profile"
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // 解密绝不创建新密钥，避免密钥丢失被误判为空配置。
        cipher.init(
            Cipher.DECRYPT_MODE,
            requireNotNull(readKey()) { "Missing profile encryption key" },
            GCMParameterSpec(TAG_LENGTH_BITS, payload.copyOfRange(1, 1 + IV_LENGTH)),
        )
        cipher.updateAAD(AAD)
        val plaintext = cipher.doFinal(payload, 1 + IV_LENGTH, payload.size - 1 - IV_LENGTH)
        return json
            .decodeFromString<ProfileDocument>(plaintext.decodeToString(throwOnInvalidSequence = true))
            .also { it.validateStructure() }
    }

    override suspend fun writeTo(
        t: ProfileDocument,
        output: OutputStream,
    ) {
        t.validateStructure()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val key =
            readKey() ?: run {
                check(!file.exists()) { "Missing profile encryption key" }
                createKey()
            }
        cipher.init(Cipher.ENCRYPT_MODE, key)
        check(cipher.iv.size == IV_LENGTH)
        cipher.updateAAD(AAD)
        val ciphertext = cipher.doFinal(json.encodeToString(t).encodeToByteArray())
        output.write(byteArrayOf(FORMAT_VERSION) + cipher.iv + ciphertext)
    }

    private fun readKey(): SecretKey? =
        KeyStore.getInstance(ANDROID_KEYSTORE).run {
            load(null)
            getKey(keyAlias, null) as? SecretKey
        }

    private fun createKey(): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec
                    .Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION: Byte = 1
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
        val AAD = "EasytierKT.ProfileDocument.v4".encodeToByteArray()
    }
}
