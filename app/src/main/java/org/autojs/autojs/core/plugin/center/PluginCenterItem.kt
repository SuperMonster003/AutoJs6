package org.autojs.autojs.core.plugin.center

import android.graphics.drawable.Drawable
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

data class PluginCenterItem(
    val title: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long? = null,
    val versionDate: String? = null,
    var updatableVersionName: String? = null,
    var updatableVersionCode: Long? = null,
    var updatableVersionDate: String? = null,
    val author: String? = null,
    val collaborators: List<String> = emptyList(),
    val description: String,

    // Size of installed package (aggregated base + splits), 0 for uninstalled.
    // zh-CN: 已安装包大小 (聚合 base + splits), 未安装为 0.
    val packageSize: Long = 0L,

    // Installable package metadata (from index or network detection).
    // zh-CN: 可安装包元信息 (来自索引或网络探测).
    val installableApkUrl: String? = null,
    val installableApkSha256: String? = null,
    val installableApkSizeBytes: Long? = null,

    val icon: Drawable? = null,
    var isEnabled: Boolean = true,
    val isInstalled: Boolean,
    val firstInstallTime: Long? = null,
    val lastUpdateTime: Long? = null,
    val settings: PluginCenterItemSettings? = null,
) {
    val versionSummary: String
        get() = formatVersionInfo(versionName, versionCode, versionDate)

    val updatableVersionSummary: String?
        get() = updatableVersionName?.let { formatVersionInfo(it, updatableVersionCode, updatableVersionDate) }

    val isUpdatable: Boolean
        get() = updatableVersionName != null

    var lastInstallTime: Long?
        get() = PluginRecentStore.getLastInstalled(packageName)
        set(value) = PluginRecentStore.setLastInstalled(packageName, value ?: System.currentTimeMillis())

    var lastUninstallTime: Long?
        get() = PluginRecentStore.getLastUninstalled(packageName)
        set(value) = PluginRecentStore.setLastUninstalled(packageName, value ?: System.currentTimeMillis())

    private fun formatVersionInfo(versionName: String, versionCode: Long?, versionDate: String?): String {
        val code = versionCode?.takeIf { it > 0 }
        val date = versionDate?.runCatching {
            DateTimeFormat.forPattern("yyyy-MM-dd").print(DateTime.parse(this))
        }?.getOrNull()
        return buildString {
            append(versionName)
            code?.let { append(" ($it)") }
            date?.let { append(" | $it") }
        }
    }

}
