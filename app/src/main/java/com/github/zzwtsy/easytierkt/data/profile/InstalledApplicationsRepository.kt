package com.github.zzwtsy.easytierkt.data.profile

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApplication(
    val packageName: String,
    val label: String,
)

fun interface ApplicationsRepository {
    suspend fun load(): List<InstalledApplication>
}

/** 在后台读取当前用户可见的全部应用，包括无启动器入口的联网应用。 */
class InstalledApplicationsRepository(
    context: Context,
) : ApplicationsRepository {
    private val context = context.applicationContext

    override suspend fun load(): List<InstalledApplication> =
        withContext(Dispatchers.IO) {
            val manager = context.packageManager
            manager
                .getInstalledApplications(0)
                .filter { it.packageName != context.packageName }
                .map { InstalledApplication(it.packageName, it.loadLabel(manager).toString()) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        }
}
