package com.github.zzwtsy.easytierkt.data.profile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    suspend fun load(): ProfileLoadResult = withContext(Dispatchers.IO) {
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

    suspend fun save(profile: ConnectionProfile): ProfileStorageError? = withContext(Dispatchers.IO) {
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
