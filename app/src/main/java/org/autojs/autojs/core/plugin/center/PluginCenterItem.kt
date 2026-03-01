package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.core.net.toUri
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs6.R
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

    var updatableApkUrl: String? = null,
    var updatableApkSha256: String? = null,
    var updatableApkSizeBytes: Long? = null,
    var updatableChangelogUrl: String? = null,
    var updatableChangelogText: String? = null,

    val author: String? = null,
    val collaborators: List<String> = emptyList(),
    val description: String? = null,

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
    val mechanism: PluginMechanism = PluginMechanism.AIDL,
    var authorizedState: PluginAuthorizedState = PluginAuthorizedState.OFFICIAL,
    var activatedState: PluginActivatedState = PluginActivatedState.NOT_SUPPORTED,
    var enabledState: PluginEnabledState = PluginEnabledState.READY,
    var lastError: PluginError? = null,
    var signingFingerprintSha256: String? = null,
    var isOfficialVerified: Boolean = false,
    var canActivate: Boolean = false,
) {
    val versionSummary: String
        get() = formatVersionInfo(versionName, versionCode, versionDate)

    val updatableVersionSummary: String?
        get() = when {
            isUpdatable -> formatVersionInfo(
                updatableVersionName ?: versionName,
                updatableVersionCode ?: versionCode,
                updatableVersionDate,
            )
            else -> null
        }

    val isUpdatable: Boolean
        get() = updatableVersionCode != null

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

    fun uninstall(context: Context) {
        Intent(Intent.ACTION_DELETE, "package:$packageName".toUri())
            .startSafely(context)
    }

    fun uninstallWithPrompt(context: Context, dialog: MaterialDialog? = null) {
        MaterialDialog.Builder(context)
            .title(R.string.text_prompt)
            .content(R.string.text_confirm_to_uninstall)
            .negativeText(R.string.dialog_button_cancel)
            .neutralColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_caution)
            .onPositive { _, _ ->
                runCatching { uninstall(context) }
                dialog?.dismiss()
            }
            .show()
    }

}
