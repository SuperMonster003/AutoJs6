package org.autojs.autojs.ui.explorer

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginIndexSource
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.batchrename.api.BatchRenamePluginActions
import org.autojs.plugin.batchrename.api.BatchRenamePluginIds
import java.util.concurrent.atomic.AtomicBoolean

internal enum class ExplorerBatchRenameAvailability {
    MISSING,

    /** The installed plugin needs a newer AutoJs6 than this one. zh-CN: 已安装的插件需要比当前更新的 AutoJs6. */
    HOST_TOO_OLD,
    DISABLED,
    UNAUTHORIZED,
    UNAVAILABLE,
}

internal object ExplorerBatchRenameAvailabilityPolicy {

    /**
     * A plugin that needs a newer host stays unusable whatever its other states, so that comes first after the
     * installation (Batch Rename plugin roadmap P6.6).
     * zh-CN: 需要更新宿主的插件无论其他状态如何都无法使用, 因此安装之后先判断这一点 (Batch Rename 插件路线图 P6.6).
     */
    fun resolve(
        installed: Boolean,
        enabled: Boolean,
        authorized: Boolean,
        requiresHostVersion: Long? = null,
        hostVersion: Long = BuildConfig.VERSION_CODE.toLong(),
    ): ExplorerBatchRenameAvailability = when {
        !installed -> ExplorerBatchRenameAvailability.MISSING
        !PluginCapabilityResolver.isHostVersionCompatible(hostVersion, requiresHostVersion) -> ExplorerBatchRenameAvailability.HOST_TOO_OLD
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
        val requiresHostVersion = applicationInfo?.let { requiredHostVersion(context) }
        val availability = ExplorerBatchRenameAvailabilityPolicy.resolve(
            installed = applicationInfo != null,
            enabled = applicationInfo?.enabled == true &&
                PluginEnableStore.isEnabled(context.applicationContext, BatchRenamePluginIds.PACKAGE_NAME, true),
            authorized = applicationInfo != null &&
                PluginTrustManager.isAuthorized(context.applicationContext, BatchRenamePluginIds.PACKAGE_NAME),
            requiresHostVersion = requiresHostVersion,
        )
        val content = when (availability) {
            ExplorerBatchRenameAvailability.MISSING -> context.getString(
                R.string.error_plugin_not_found_in_installed_apps,
                displayName,
            )
            ExplorerBatchRenameAvailability.HOST_TOO_OLD -> context.getString(
                R.string.hint_plugin_requires_higher_host_version_with_required_and_current,
                requiresHostVersion ?: 0L,
                BuildConfig.VERSION_CODE.toLong(),
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

    /**
     * The host build the installed plugin requires, from the `requiresHostVersion` meta-data of its Batch Rename
     * service (read without binding, on the main thread); null when it declares none.
     * zh-CN: 已安装插件要求的宿主构建号, 取自其 Batch Rename 服务的 `requiresHostVersion` meta-data (不绑定服务, 可在主线程读取);
     * 未声明时为 null.
     */
    @Suppress("DEPRECATION")
    private fun requiredHostVersion(context: Context): Long? = runCatching {
        context.packageManager.queryIntentServices(
            Intent(BatchRenamePluginActions.BATCH_RENAME)
                .addCategory(BatchRenamePluginActions.SERVICE_CATEGORY)
                .setPackage(BatchRenamePluginIds.PACKAGE_NAME),
            PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS,
        ).mapNotNull { it.serviceInfo?.metaData?.get(META_REQUIRES_HOST_VERSION)?.let(PluginCapabilityResolver::coerceLong) }.maxOrNull()
    }.getOrNull()

    private const val META_REQUIRES_HOST_VERSION = "requiresHostVersion"

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
