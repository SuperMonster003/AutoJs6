package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.core.net.toUri
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs6.R

data class PluginCenterItem(
    val title: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long? = null,
    val versionDate: String? = null,

    val updatableVersionName: String? = null,
    val updatableVersionCode: Long? = null,
    val updatableVersionDate: String? = null,

    val updatableApkUrl: String? = null,
    val updatableApkSha256: String? = null,
    val updatableApkSizeBytes: Long? = null,
    val updatableChangelogUrl: String? = null,
    val updatableChangelogText: String? = null,
    val requiresDowngradeReinstall: Boolean = false,

    val author: String? = null,
    val collaborators: List<String> = emptyList(),
    val description: String? = null,
    val instructionHardCoded: String? = null,
    val instructionMarkdownUrl: String? = null,

    // Size of installed package (aggregated base + splits), 0 for uninstalled.
    // zh-CN: 已安装包大小 (聚合 base + splits), 未安装为 0.
    val packageSize: Long = 0L,

    // Installable package metadata (from index or network detection).
    // zh-CN: 可安装包元信息 (来自索引或网络探测).
    val installableApkUrl: String? = null,
    val installableApkSha256: String? = null,
    val installableApkSizeBytes: Long? = null,
    val supportedAbis: List<String>? = null,

    // ABIs whose native libraries are packaged in the installed APKs (base + splits);
    // null for uninstalled plugins, empty for packages without native code.
    // zh-CN: 已安装 APK (base + splits) 内实际打包的 ABI; 未安装为 null, 无原生代码为空列表.
    val installedAbis: List<String>? = null,
    val remoteIconUrl: String? = null,
    val remoteNightIconUrl: String? = null,
    val forceIgnoreLocalIcon: Boolean = false,
    val iconBackgroundColor: String? = null,
    val nightIconBackgroundColor: String? = null,

    val icon: Drawable? = null,

    // Stable identity of the local icon; lets list diffing detect icon changes
    // without comparing Drawable instances.
    // zh-CN: 本地图标的稳定标识; 使列表差分无需比较 Drawable 实例即可检测图标变化.
    val iconSignature: String? = null,
    var isEnabled: Boolean = true,
    val isInstalled: Boolean,
    val firstInstallTime: Long? = null,
    val lastUpdateTime: Long? = null,
    val settings: PluginCenterItemSettings? = null,
    val source: PluginIndexSource = PluginIndexSource.OFFICIAL,
    val isFeatured: Boolean = true,
    val engine: String? = null,
    val variant: String? = null,
    val engineId: String? = null,
    val isOcrService: Boolean = false,
    val isBarcodeService: Boolean = false,
    val serviceAction: String? = null,
    val mechanism: PluginMechanism = PluginMechanism.AIDL,
    var authorizedState: PluginAuthorizedState = PluginAuthorizedState.OFFICIAL,
    var activatedState: PluginActivatedState = PluginActivatedState.NOT_SUPPORTED,
    var enabledState: PluginEnabledState = PluginEnabledState.READY,
    var lastError: PluginError? = null,
    var signingFingerprintSha256: String? = null,
    var isOfficialVerified: Boolean = false,
    var canActivate: Boolean = false,
    val requiresHostVersion: Long? = null,
    val hostVersionCode: Long? = null,
    val nativePageAlignment: Long? = null,
    val updatableNativePageAlignment: Long? = null,

    // Set by Plugin Center while the row is temporarily pinned (PluginCenterAttentionPolicy), never by discovery.
    // zh-CN: 仅在条目被临时置顶时由插件中心设置 (PluginCenterAttentionPolicy), 发现过程从不设置.
    val attention: PluginCenterAttention? = null,
) {
    val versionSummary: String = PluginVersionSummaryFormatter.format(versionName, versionCode, versionDate)

    val updatableVersionSummary: String? = when {
        updatableVersionCode != null -> PluginVersionSummaryFormatter.format(
            updatableVersionName ?: versionName,
            updatableVersionCode,
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
