package com.github.zzwtsy.easytierkt.data.profile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 配置读取结果；读取失败时仍提供默认配置，并通过 [error] 标明失败原因。 */
data class ProfileLoadResult(
    val profile: ConnectionProfile,
    val error: ProfileStorageError? = null,
)

enum class ProfileStorageError {
    READ_FAILED,
    WRITE_FAILED,
}

class ConnectionProfileRepository(
    private val store: EncryptedProfileStore,
) {
    /**
     * 在 `Dispatchers.IO` 读取配置；没有已存储配置时返回默认值，其他读取异常映射为 [ProfileStorageError.READ_FAILED]。
     * 协程取消会继续向调用方传播。
     */
    suspend fun load(): ProfileLoadResult =
        withContext(Dispatchers.IO) {
            try {
                ProfileLoadResult(profile = store.read())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ProfileLoadResult(
                    profile = ConnectionProfile(),
                    error = ProfileStorageError.READ_FAILED,
                )
            }
        }

    /**
     * 在 `Dispatchers.IO` 保存给定配置，不执行字段校验；成功返回 null，写入异常映射为
     * [ProfileStorageError.WRITE_FAILED]，协程取消会继续向调用方传播。
     */
    suspend fun save(profile: ConnectionProfile): ProfileStorageError? =
        withContext(Dispatchers.IO) {
            try {
                store.write(profile)
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ProfileStorageError.WRITE_FAILED
            }
        }
}
