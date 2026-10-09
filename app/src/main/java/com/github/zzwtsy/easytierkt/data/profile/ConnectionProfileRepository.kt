package com.github.zzwtsy.easytierkt.data.profile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

enum class ProfileActionError {
    READ_FAILED,
    WRITE_FAILED,
    INVALID_NAME,
    INVALID_CONFIG,
    NOT_FOUND,
    CONNECTION_BUSY,
}

data class ProfileActionResult(
    val profile: SavedProfile? = null,
    val error: ProfileActionError? = null,
)

data class ProfilesState(
    val isLoading: Boolean = true,
    val document: ProfileDocument? = null,
    val error: ProfileActionError? = null,
    val activeProfileId: String? = null,
) {
    val selectedProfile: SavedProfile? get() = document?.profiles?.find { it.id == document.selectedProfileId }
}

/** 串行维护配置集合与会话预留；磁盘提交成功后才发布状态，读取失败时拒绝变更。 */
class ConnectionProfileRepository(
    private val store: ProfileStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(ProfilesState())
    val state = mutableState.asStateFlow()

    suspend fun refresh() =
        transaction {
            mutableState.value = mutableState.value.copy(isLoading = true)
            readDocument()
        }

    suspend fun create(
        name: String,
        config: ConnectionProfile,
    ): ProfileActionResult =
        mutate {
            validateInput(name, config)?.let { return@mutate ProfileActionResult(error = it) }
            val profile = SavedProfile(newId(), name.trim(), config)
            val document = requireNotNull(mutableState.value.document)
            persist(
                document.copy(
                    profiles = document.profiles + profile,
                    selectedProfileId = if (document.profiles.isEmpty()) profile.id else document.selectedProfileId,
                ),
                profile,
            )
        }

    suspend fun update(
        id: String,
        name: String,
        config: ConnectionProfile,
    ): ProfileActionResult =
        mutate {
            validateInput(name, config)?.let { return@mutate ProfileActionResult(error = it) }
            val document = requireNotNull(mutableState.value.document)
            if (document.profiles.none { it.id == id }) return@mutate ProfileActionResult(error = ProfileActionError.NOT_FOUND)
            val profile = SavedProfile(id, name.trim(), config)
            persist(document.copy(profiles = document.profiles.map { if (it.id == id) profile else it }), profile)
        }

    suspend fun select(id: String): ProfileActionResult =
        mutate {
            if (mutableState.value.activeProfileId != null) {
                return@mutate ProfileActionResult(error = ProfileActionError.CONNECTION_BUSY)
            }
            val document = requireNotNull(mutableState.value.document)
            val profile = document.profiles.find { it.id == id } ?: return@mutate ProfileActionResult(error = ProfileActionError.NOT_FOUND)
            persist(document.copy(selectedProfileId = id), profile)
        }

    suspend fun delete(id: String): ProfileActionResult =
        mutate {
            if (mutableState.value.activeProfileId == id) return@mutate ProfileActionResult(error = ProfileActionError.CONNECTION_BUSY)
            val document = requireNotNull(mutableState.value.document)
            if (document.profiles.none { it.id == id }) return@mutate ProfileActionResult(error = ProfileActionError.NOT_FOUND)
            persist(
                document.copy(
                    profiles = document.profiles.filterNot { it.id == id },
                    selectedProfileId = document.selectedProfileId?.takeUnless { it == id },
                    resumeProfileId = document.resumeProfileId?.takeUnless { it == id },
                ),
            )
        }

    suspend fun getById(id: String): ProfileActionResult =
        mutate {
            ProfileActionResult(
                profile =
                    mutableState.value.document
                        ?.profiles
                        ?.find { it.id == id },
            ).let { if (it.profile == null) it.copy(error = ProfileActionError.NOT_FOUND) else it }
        }

    /** 连接预留与配置修改共用锁，防止选择、删除与启动互相穿插；重复预留被拒绝。 */
    suspend fun reserveSession(id: String): ProfileActionResult =
        mutate {
            if (mutableState.value.activeProfileId != null) {
                return@mutate ProfileActionResult(error = ProfileActionError.CONNECTION_BUSY)
            }
            reserve(id)
        }

    /** 服务接管预留或恢复原 ID，并读取最新参数；不回退到选中配置。 */
    suspend fun sessionProfile(id: String): ProfileActionResult =
        mutate {
            val active = mutableState.value.activeProfileId
            if (active != null && active != id) return@mutate ProfileActionResult(error = ProfileActionError.CONNECTION_BUSY)
            if (active == null) {
                if (mutableState.value.document?.resumeProfileId !=
                    id
                ) {
                    return@mutate ProfileActionResult(error = ProfileActionError.NOT_FOUND)
                }
                return@mutate reserve(id)
            }
            val profile =
                mutableState.value.document
                    ?.profiles
                    ?.find { it.id == id }
                    ?: return@mutate ProfileActionResult(error = ProfileActionError.NOT_FOUND)
            if (profile.config.validationError() != null) return@mutate ProfileActionResult(error = ProfileActionError.INVALID_CONFIG)
            ProfileActionResult(profile)
        }

    suspend fun resumableProfileId(): String? =
        transaction {
            ensureLoaded()
            mutableState.value.document
                ?.resumeProfileId
                .takeUnless { mutableState.value.error == ProfileActionError.READ_FAILED }
        }

    /** 先禁用持久恢复，保留预留直到 TUN 和内核清理结束；清除失败仍由调用方停止服务。 */
    suspend fun clearResume(): ProfileActionResult =
        mutate {
            persist(requireNotNull(mutableState.value.document).copy(resumeProfileId = null))
        }

    suspend fun releaseSession() =
        transaction {
            mutableState.value = mutableState.value.copy(activeProfileId = null)
        }

    private suspend fun reserve(id: String): ProfileActionResult {
        val document = requireNotNull(mutableState.value.document)
        val profile = document.profiles.find { it.id == id } ?: return ProfileActionResult(error = ProfileActionError.NOT_FOUND)
        if (profile.config.validationError() != null) return ProfileActionResult(error = ProfileActionError.INVALID_CONFIG)
        return persist(document.copy(selectedProfileId = id, resumeProfileId = id), profile, activeProfileId = id)
    }

    private fun validateInput(
        name: String,
        config: ConnectionProfile,
    ): ProfileActionError? =
        when {
            !isProfileNameValid(name) -> ProfileActionError.INVALID_NAME
            config.validationError() != null -> ProfileActionError.INVALID_CONFIG
            else -> null
        }

    private suspend fun <T> transaction(action: suspend () -> T): T = withContext(dispatcher) { mutex.withLock { action() } }

    private suspend fun mutate(action: suspend () -> ProfileActionResult): ProfileActionResult =
        transaction {
            ensureLoaded()
            if (mutableState.value.error == ProfileActionError.READ_FAILED) {
                ProfileActionResult(error = ProfileActionError.READ_FAILED)
            } else {
                action()
            }
        }

    private fun ensureLoaded() {
        if (mutableState.value.document == null && mutableState.value.error == null) readDocument()
    }

    private fun readDocument() {
        try {
            val document = store.read().also { it.validateStructure() }
            mutableState.value =
                mutableState.value.copy(document = document, isLoading = false, error = null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(isLoading = false, error = ProfileActionError.READ_FAILED)
        }
    }

    private suspend fun persist(
        document: ProfileDocument,
        profile: SavedProfile? = null,
        activeProfileId: String? = mutableState.value.activeProfileId,
    ): ProfileActionResult =
        // 同步提交与状态发布作为一个不可取消的小事务，避免磁盘已写入但 UI 仍保留旧集合。
        withContext(NonCancellable) {
            try {
                document.validateStructure()
                store.write(document)
                mutableState.value =
                    mutableState.value.copy(document = document, isLoading = false, error = null, activeProfileId = activeProfileId)
                ProfileActionResult(profile)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ProfileActionResult(error = ProfileActionError.WRITE_FAILED)
            }
        }
}
