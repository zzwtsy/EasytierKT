package com.github.zzwtsy.easytierkt.data.profile

import kotlinx.serialization.Serializable
import java.util.UUID

/** 配置身份与显示名称独立于 EasyTier 网络身份，同一网络可保存多份方案。 */
@Serializable
data class SavedProfile(
    val id: String,
    val displayName: String,
    val config: ConnectionProfile,
)

/** 恢复记录只标识原配置，不代表服务正在运行，也不保存参数快照。 */
@Serializable
data class ProfileDocument(
    val schemaVersion: Int = 2,
    val profiles: List<SavedProfile> = emptyList(),
    val selectedProfileId: String? = null,
    val resumeProfileId: String? = null,
) {
    fun validateStructure() {
        require(schemaVersion == 2) { "Unsupported profile schema" }
        val ids = profiles.map { it.id }
        require(ids.all(String::isNotBlank) && ids.distinct().size == ids.size) { "Invalid profile identities" }
        require(selectedProfileId == null || selectedProfileId in ids) { "Missing selected profile" }
        require(resumeProfileId == null || resumeProfileId in ids) { "Missing resumable profile" }
    }

    companion object {
        /** 保留旧配置的全部字段，包括需要用户修正的不完整字段。 */
        fun migrate(
            profile: ConnectionProfile,
            id: String = UUID.randomUUID().toString(),
        ): ProfileDocument =
            ProfileDocument(
                profiles = listOf(SavedProfile(id, profile.networkName.trim().ifBlank { "默认配置" }, profile)),
                selectedProfileId = id,
            )
    }
}

fun isProfileNameValid(name: String): Boolean = name.trim().let { it.isNotEmpty() && it.length <= 128 && it.none(Char::isISOControl) }

/** Android 加密存储与内存测试替身的边界；失败抛出异常，由 Repository 映射。 */
interface ProfileStore {
    fun read(): ProfileDocument

    fun write(document: ProfileDocument)
}

/** 迁移只有在新记录写入并读取校验成功后才删除旧记录，写入或校验失败保留旧数据。 */
internal fun migrateLegacyRecord(
    legacy: ConnectionProfile,
    writeNew: (ProfileDocument) -> Unit,
    readNew: () -> ProfileDocument,
    removeOld: () -> Unit,
): ProfileDocument {
    val document = ProfileDocument.migrate(legacy)
    writeNew(document)
    check(readNew() == document) { "Profile migration verification failed" }
    removeOld()
    return document
}
