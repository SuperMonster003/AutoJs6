package org.autojs.autojs.ui.wizard.permission

import android.Manifest
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.permission.AllFilesAccessPermission
import org.autojs.autojs.permission.Base
import org.autojs.autojs.permission.DisplayOverOtherAppsPermission
import org.autojs.autojs.permission.IgnoreBatteryOptimizationsPermission
import org.autojs.autojs.permission.LocalNetworkPermission
import org.autojs.autojs.permission.MediaProjectionPermission
import org.autojs.autojs.permission.PostNotificationsPermission
import org.autojs.autojs.permission.UsageStatsPermission
import org.autojs.autojs.permission.VivoBackgroundPopupPermission
import org.autojs.autojs.permission.WriteSecureSettingsPermission
import org.autojs.autojs.permission.WriteSystemSettingsPermission
import org.autojs.autojs.permission.XiaomiBackgroundPopupPermission
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.service.NotificationService
import org.autojs.autojs.ui.wizard.WizardUi
import org.autojs.autojs.ui.wizard.WizardUi.ReturnResult
import org.autojs.autojs.ui.wizard.WizardUi.showAndAwait
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import rikka.shizuku.Shizuku

/**
 * Requests permissions one after another. Each step opens the system dialog or settings page of
 * one permission, and the next step starts once that dialog closed or the user came back from the
 * settings page. Permissions granted meanwhile are skipped.
 *
 * zh-CN: 依次申请权限. 每一步打开一项权限的系统对话框或设置页, 待对话框关闭或用户从设置页返回后再开始下一步.
 * 期间已被授予的权限会被跳过.
 */
object PermissionWizardRunner {

    /** A short pause after returning, so the next page does not open the very moment the user is back. */
    private const val STEP_INTERVAL_MILLIS = 300L

    /**
     * Requests [entries] in order and returns the ones still not granted afterwards. With
     * [showResult], a snackbar confirms full success and a dialog lists what is still missing.
     */
    suspend fun run(
        activity: FragmentActivity,
        entries: List<PermissionWizardEntry>,
        showResult: Boolean = true,
    ): List<PermissionWizardEntry> {
        val pending = entries.filterNot { PermissionWizardStates.isGranted(activity, it) }
        for ((index, entry) in pending.withIndex()) {
            if (activity.isFinishing || activity.isDestroyed) break
            if (PermissionWizardStates.isGranted(activity, entry)) continue
            if (index > 0) delay(STEP_INTERVAL_MILLIS)
            ViewUtils.showToast(
                activity,
                activity.getString(R.string.text_permission_wizard_step, index + 1, pending.size, activity.getString(entry.titleRes)),
            )
            if (!request(activity, entry)) break
        }
        val missing = entries.filterNot { PermissionWizardStates.isGranted(activity, it) }
        if (showResult && entries.isNotEmpty() && !activity.isFinishing && !activity.isDestroyed) {
            showResult(activity, missing)
        }
        return missing
    }

    /** Runs one step; false when the activity went away and the queue has to stop. */
    private suspend fun request(activity: FragmentActivity, entry: PermissionWizardEntry): Boolean {
        val result = when (entry) {
            PermissionWizardEntry.POST_NOTIFICATIONS -> requestPostNotifications(activity)
            PermissionWizardEntry.LOCAL_NETWORK -> requestRuntime(activity, arrayOf(LocalNetworkPermission.PERMISSION), entry) {
                IntentUtils.launchAppDetailsSettings(activity)
            }
            PermissionWizardEntry.NOTIFICATION_ACCESS -> openPage(activity) { NotificationService(activity).config() }
            PermissionWizardEntry.ALL_FILES_ACCESS -> requestAllFilesAccess(activity)
            PermissionWizardEntry.USAGE_STATS -> openPage(activity) { UsageStatsPermission(activity).config() }
            PermissionWizardEntry.IGNORE_BATTERY_OPTIMIZATIONS -> openPage(activity) { IgnoreBatteryOptimizationsPermission(activity).request() }
            PermissionWizardEntry.DISPLAY_OVER_OTHER_APPS -> openPage(activity) { DisplayOverOtherAppsPermission(activity).config() }
            PermissionWizardEntry.XIAOMI_BACKGROUND_POPUP -> openPage(activity) { XiaomiBackgroundPopupPermission(activity).config() }
            PermissionWizardEntry.VIVO_BACKGROUND_POPUP -> openPage(activity) { VivoBackgroundPopupPermission(activity).config() }
            PermissionWizardEntry.WRITE_SYSTEM_SETTINGS -> openPage(activity) { WriteSystemSettingsPermission(activity).config() }
            PermissionWizardEntry.WRITE_SECURE_SETTINGS -> runCommand { WriteSecureSettingsPermission(activity).request() }
            PermissionWizardEntry.PROJECT_MEDIA -> runCommand { MediaProjectionPermission(activity).request() }
            PermissionWizardEntry.SHIZUKU -> requestShizuku(activity)
        }
        return result != ReturnResult.DESTROYED
    }

    /** Opens a settings page or system dialog; [launch] may return Unit or whether it started. */
    private suspend fun openPage(activity: FragmentActivity, launch: () -> Any?): ReturnResult =
        WizardUi.launchAndAwaitReturn(activity) { launch() != false }

    /**
     * Asks for runtime [permissions]. When the system shows no dialog (the permission was denied
     * permanently) the app details page opens instead.
     */
    private suspend fun requestRuntime(
        activity: FragmentActivity,
        permissions: Array<String>,
        entry: PermissionWizardEntry,
        openSettings: () -> Any?,
    ): ReturnResult {
        val result = WizardUi.launchAndAwaitReturn(activity) {
            ActivityCompat.requestPermissions(activity, permissions, Base.REQUEST_CODE)
            true
        }
        if (result != ReturnResult.NOT_LEFT || PermissionWizardStates.isGranted(activity, entry)) return result
        return openPage(activity, openSettings)
    }

    private suspend fun requestPostNotifications(activity: FragmentActivity): ReturnResult {
        val helper = PostNotificationsPermission(activity)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return openPage(activity) { helper.config() }
        val permission = Manifest.permission.POST_NOTIFICATIONS
        val attempted = MiscStateKeys.POST_NOTIFICATIONS_PERMISSION_REQUEST_ATTEMPTED.get()
        if (attempted && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
            // The system no longer shows the dialog; go to the notification settings directly.
            // zh-CN: 系统不再弹出授权对话框, 直接前往通知设置.
            return openPage(activity) { helper.config() }
        }
        MiscStateKeys.POST_NOTIFICATIONS_PERMISSION_REQUEST_ATTEMPTED.set(true)
        return requestRuntime(activity, arrayOf(permission), PermissionWizardEntry.POST_NOTIFICATIONS) { helper.config() }
    }

    private suspend fun requestAllFilesAccess(activity: FragmentActivity): ReturnResult {
        val helper = AllFilesAccessPermission(activity)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return openPage(activity) { helper.config() }
        return requestRuntime(
            activity,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
            PermissionWizardEntry.ALL_FILES_ACCESS,
        ) { helper.config() }
    }

    /** Root and Shizuku commands need no page; without both the ADB guide blocks until it is closed. */
    private suspend fun runCommand(request: () -> Boolean): ReturnResult {
        withContext(Dispatchers.IO) { runCatching(request) }
        return ReturnResult.NOT_LEFT
    }

    private suspend fun requestShizuku(activity: FragmentActivity): ReturnResult {
        val denied = runCatching { Shizuku.shouldShowRequestPermissionRationale() }.getOrDefault(false)
        return when {
            // Shizuku no longer asks; its app has to grant the access.
            // zh-CN: Shizuku 不再询问, 需要在其应用中授予访问权限.
            denied -> openPage(activity) { WrappedShizuku.configWithContext(activity, true) != null }
            else -> openPage(activity) { WrappedShizuku.requestPermission() }
        }
    }

    private suspend fun showResult(activity: FragmentActivity, missing: List<PermissionWizardEntry>) {
        if (missing.isEmpty()) {
            WizardUi.showSnack(activity, activity.getString(R.string.text_permission_wizard_all_granted))
            return
        }
        MaterialDialog.Builder(activity)
            .title(R.string.text_permission_wizard)
            .content(
                activity.getString(
                    R.string.text_permission_wizard_not_granted_summary,
                    missing.joinToString("\n") { "- ${activity.getString(it.titleRes)}" },
                ),
            )
            .positiveText(R.string.dialog_button_dismiss)
            .positiveColorRes(R.color.dialog_button_default)
            .showAndAwait()
    }

}
