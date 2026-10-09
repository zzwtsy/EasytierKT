package com.github.zzwtsy.easytierkt.data.profile

import com.easytier.jni.EasyTierJNI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SecureIdentity(
    val privateKey: String,
    val publicKey: String,
)

/** 原生生成和派生与网络握手复用同一实现；接口供编辑器测试隔离原生库。 */
fun interface SecureIdentityProvider {
    suspend fun prepare(privateKey: String): SecureIdentity
}

object NativeSecureIdentityProvider : SecureIdentityProvider {
    override suspend fun prepare(privateKey: String): SecureIdentity =
        withContext(Dispatchers.IO) {
            Json.decodeFromString<SecureIdentity>(EasyTierJNI.prepareSecureIdentity(privateKey))
        }
}
