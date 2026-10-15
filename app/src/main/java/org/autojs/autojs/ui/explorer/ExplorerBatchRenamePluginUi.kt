package org.autojs.autojs.ui.explorer

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginIndexSource
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs6.R
import org.autojs.plugin.batchrename.api.BatchRenamePluginIds
import java.util.concurrent.atomic.AtomicBoolean

internal enum class ExplorerBatchRenameAvailability {
    MISSING,
    DISABLED,
    UNAUTHORIZED,
    UNAVAILABLE,
}

internal object ExplorerBatchRenameAvailabilityPolicy {

    fun resolve(
        installed: Boolean,
        enabled: Boolean,
        authorized: Boolean,
    ): ExplorerBatchRenameAvailability = when {
        !installed -> ExplorerBatchRenameAvailability.MISSING
        !enabled -> ExplorerBatchRenameAvailability.DISABLED
        !authorized -> ExplorerBatchRenameAvailability.UNAUTHORIZED
        else -> ExplorerBatchRenameAvailability.UNAVAILABLE
    }
}

/**
 * Guidance behind the "Batch rename..." entry of the rename dialog, shown when no installed plugin offers a rename
 * dialog action (Batch Rename plugin roadmap D29). It names the reason and opens the Plugin Center at the plugin.
 * zh-CN: 重命名对话框 "批量重命名..." 条目背后的引导, 在没有已安装插件提供重命名对话框动作时显示 (Batch Rename 插件路线图 D29).
 * 说明原因并在插件中心中定位该插件.
 */
internal object ExplorerBatchRenamePluginUi {

    private val dialogShowing = AtomicBoolean(false)

    fun showUnavailableDialog(context: Context) {
        if (!dialogShowing.compareAndSet(false, true)) return
        val applicationInfo = findApplicationInfo(context)
        val displayName = applicationInfo
            ?.runCatching { loadLabel(context.packageManager).toString() }
            ?.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.plugin_batch_rename_name)
        val availability = ExplorerBatchRenameAvailabilityPolicy.resolve(
            installed = applicationInfo != null,
            enabled = applicationInfo?.enabled == true &&
                PluginEnableStore.isEnabled(context.applicationContext, BatchRenamePluginIds.PACKAGE_NAME, true),
            authorized = applicationInfo != null &&
                PluginTrustManager.isAuthorized(context.applicationContext, BatchRenamePluginIds.PACKAGE_NAME),
        )
        val content = when (availability) {
            ExplorerBatchRenameAvailability.MISSING -> context.getString(
                R.string.error_plugin_not_found_in_installed_apps,
                displayName,
            )
            ExplorerBatchRenameAvailability.DISABLED -> context.getString(
                R.string.error_plugin_is_not_enabled_in_plugin_center,
                displayName,
            )
            ExplorerBatchRenameAvailability.UNAUTHORIZED -> context.getString(
                R.string.error_plugin_is_not_authorized_in_plugin_center,
                displayName,
            )
            ExplorerBatchRenameAvailability.UNAVAILABLE -> context.getString(
                R.string.hint_plugin_upgrade_required_due_to_protocol_mismatch,
            )
        }
        try {
            MaterialDialog.Builder(context)
                .title(displayName)
                .content(content)
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.text_plugin_center)
                .positiveColorRes(R.color.dialog_button_attraction)
                .widgetThemeColor()
                .onPositive { _, _ ->
                    PluginCenterActivity.launch(
                        context = context,
                        sourceScope = PluginIndexSource.OFFICIAL,
                        initialQuery = BatchRenamePluginIds.PACKAGE_NAME,
                    )
                }
                .dismissListener { dialogShowing.set(false) }
                .show()
        } catch (error: RuntimeException) {
            dialogShowing.set(false)
            throw error
        }
    }

    private fun findApplicationInfo(context: Context): ApplicationInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getApplicationInfo(
                BatchRenamePluginIds.PACKAGE_NAME,
                PackageManager.ApplicationInfoFlags.of(
                    PackageManager.MATCH_DISABLED_COMPONENTS.toLong(),
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(
                BatchRenamePluginIds.PACKAGE_NAME,
                PackageManager.MATCH_DISABLED_COMPONENTS,
            )
        }
    }.getOrNull()
}
