package com.github.zzwtsy.easytierkt.data.profile

import kotlinx.serialization.Serializable

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
    @kotlinx.serialization.Required
    val schemaVersion: Int = 4,
    val profiles: List<SavedProfile> = emptyList(),
    val selectedProfileId: String? = null,
    val resumeProfileId: String? = null,
) {
    fun validateStructure() {
        require(schemaVersion == 4) { "Unsupported profile schema" }
        val ids = profiles.map { it.id }
        require(ids.all(String::isNotBlank) && ids.distinct().size == ids.size) { "Invalid profile identities" }
        require(selectedProfileId == null || selectedProfileId in ids) { "Missing selected profile" }
        require(resumeProfileId == null || resumeProfileId in ids) { "Missing resumable profile" }
    }
}

fun isProfileNameValid(name: String): Boolean = name.trim().let { it.isNotEmpty() && it.length <= 128 && it.none(Char::isISOControl) }

/** Android 加密存储与内存测试替身的边界；失败抛出异常，由 Repository 映射。 */
interface ProfileStore {
    suspend fun read(): ProfileDocument

    suspend fun write(document: ProfileDocument)
}
