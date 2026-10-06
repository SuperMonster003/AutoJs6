package org.autojs.autojs.ui.wizard.permission

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.permission.AllFilesAccessPermission
import org.autojs.autojs.permission.DisplayOverOtherAppsPermission
import org.autojs.autojs.permission.IgnoreBatteryOptimizationsPermission
import org.autojs.autojs.permission.LocalNetworkPermission
import org.autojs.autojs.permission.MediaProjectionPermission
import org.autojs.autojs.permission.PostNotificationsPermission
import org.autojs.autojs.permission.ShizukuPermission
import org.autojs.autojs.permission.UsageStatsPermission
import org.autojs.autojs.permission.VivoBackgroundPopupPermission
import org.autojs.autojs.permission.WriteSecureSettingsPermission
import org.autojs.autojs.permission.WriteSystemSettingsPermission
import org.autojs.autojs.permission.XiaomiBackgroundPopupPermission
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.service.NotificationService
import org.autojs.autojs.util.RomUtils
import org.autojs.autojs.util.RootUtils
import org.autojs.autojs6.R
import rikka.shizuku.Shizuku

/**
 * Device-dependent facts of the permission wizard: which drawer permissions this device shows,
 * which are granted and which cannot be requested from the wizard right now.
 *
 * zh-CN: 权限申请向导中与设备相关的事实: 当前设备显示哪些抽屉权限, 哪些已授予, 以及哪些暂时无法在向导中申请.
 */
object PermissionWizardStates {

    /** Mirrors the visibility conditions of the drawer items. */
    fun isVisible(context: Context, entry: PermissionWizardEntry): Boolean = when (entry) {
        PermissionWizardEntry.LOCAL_NETWORK -> LocalNetworkPermission.isRequired(context)
        PermissionWizardEntry.XIAOMI_BACKGROUND_POPUP -> RomUtils.isMiui()
        PermissionWizardEntry.VIVO_BACKGROUND_POPUP -> RomUtils.isVivo()
        else -> true
    }

    fun isGranted(context: Context, entry: PermissionWizardEntry): Boolean = runCatching {
        when (entry) {
            PermissionWizardEntry.POST_NOTIFICATIONS -> PostNotificationsPermission(context).has()
            PermissionWizardEntry.LOCAL_NETWORK -> LocalNetworkPermission.isGranted(context)
            PermissionWizardEntry.NOTIFICATION_ACCESS -> NotificationService(context).isRunning
            PermissionWizardEntry.ALL_FILES_ACCESS -> AllFilesAccessPermission(context).has()
            PermissionWizardEntry.USAGE_STATS -> UsageStatsPermission(context).has()
            PermissionWizardEntry.IGNORE_BATTERY_OPTIMIZATIONS -> IgnoreBatteryOptimizationsPermission(context).has()
            PermissionWizardEntry.DISPLAY_OVER_OTHER_APPS -> DisplayOverOtherAppsPermission(context).has()
            PermissionWizardEntry.XIAOMI_BACKGROUND_POPUP -> XiaomiBackgroundPopupPermission(context).has()
            PermissionWizardEntry.VIVO_BACKGROUND_POPUP -> VivoBackgroundPopupPermission(context).has()
            PermissionWizardEntry.WRITE_SYSTEM_SETTINGS -> WriteSystemSettingsPermission(context).has()
            PermissionWizardEntry.WRITE_SECURE_SETTINGS -> WriteSecureSettingsPermission(context).has()
            PermissionWizardEntry.PROJECT_MEDIA -> MediaProjectionPermission(context).has()
            PermissionWizardEntry.SHIZUKU -> ShizukuPermission(context).has()
        }
    }.getOrDefault(false)

    /** Ids of the visible permissions that [entries] does not have granted yet, in order. */
    fun missing(context: Context, entries: Collection<PermissionWizardEntry>): List<PermissionWizardEntry> =
        entries.filter { isVisible(context, it) && !isGranted(context, it) }

    /** Builds the wizard rows; the Root check may run a shell, so this stays off the main thread. */
    suspend fun load(context: Context): List<PermissionWizardItem> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val privileged by lazy { hasPrivilegedShell() }
        PermissionWizardEntry.entries
            .filter { isVisible(appContext, it) }
            .map { entry ->
                val granted = isGranted(appContext, entry)
                PermissionWizardItem(
                    entry = entry,
                    isGranted = granted,
                    unavailableReason = if (granted) null else unavailableReason(appContext, entry) { privileged },
                )
            }
    }

    private fun unavailableReason(context: Context, entry: PermissionWizardEntry, privileged: () -> Boolean): String? = when (entry) {
        // Granted through Root or Shizuku commands; without them only the ADB command remains.
        // zh-CN: 通过 Root 或 Shizuku 命令授予; 两者均不可用时只能使用 ADB 命令.
        PermissionWizardEntry.WRITE_SECURE_SETTINGS,
        PermissionWizardEntry.PROJECT_MEDIA,
            -> context.getString(R.string.text_permission_wizard_requires_root_or_shizuku).takeUnless { privileged() }
        PermissionWizardEntry.SHIZUKU -> when {
            !WrappedShizuku.isInstalled(context) -> context.getString(R.string.error_shizuku_is_not_installed)
            !WrappedShizuku.isRunning() -> context.getString(R.string.text_shizuku_not_running)
            runCatching { Shizuku.isPreV11() }.getOrDefault(false) -> context.getString(R.string.error_shizuku_version_is_not_supported)
            else -> null
        }
        else -> null
    }

    private fun hasPrivilegedShell(): Boolean =
        (WrappedShizuku.hasPermission() && WrappedShizuku.isRunning()) || runCatching { RootUtils.isRootAvailable() }.getOrDefault(false)

}
