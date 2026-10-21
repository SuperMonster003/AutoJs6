package org.autojs.autojs.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.runtime.api.Permissions
import org.autojs.autojs.ui.main.drawer.PermissionItemHelper
import org.autojs.autojs.util.ContextUtils.findActivity
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.NotificationUtils
import org.autojs.autojs6.R
import java.util.WeakHashMap

/**
 * Created by SuperMonster003 on May 4, 2023.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Jan 24, 2026.
 * Modified by SuperMonster003 as of Jan 25, 2026.
 */
class PostNotificationsPermission(override val context: Context) : PermissionItemHelper {

    override val isInMainThread = true

    override fun has() = NotificationUtils.isEnabled()

    override fun request(): Boolean = false.also {
        // Manual request from UI interaction.
        // zh-CN: 来自 UI 手动触发的请求.
        if (!requestForTiramisuAndAbove()) config()
    }

    private fun requestForTiramisuAndAbove(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        when (val activity = context.findActivity()) {
            is FragmentActivity -> {
                val isGrantedNow =
                    ActivityCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (isGrantedNow) return true

                val hasAttemptedRequest =
                    MiscStateKeys.POST_NOTIFICATIONS_PERMISSION_REQUEST_ATTEMPTED.get()

                val shouldShowRationale =
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)

                // If request has been attempted before and system will no longer show the permission dialog,
                // redirect to settings.
                // zh-CN: 若之前已尝试过请求且系统不再弹出授权对话框, 则直接跳转设置页.
                if (hasAttemptedRequest && !shouldShowRationale) {
                    config()
                    return true
                }

                val launchRequest = {
                    // Mark request attempt.
                    // zh-CN: 记录已尝试发起系统权限请求.
                    MiscStateKeys.POST_NOTIFICATIONS_PERMISSION_REQUEST_ATTEMPTED.set(true)

                    // Set one-shot callback to fallback to settings when denied.
                    // zh-CN: 设置一次性回调; 若用户拒绝授权, 则回退到设置页.
                    setPostNotificationsResultCallback(activity) { granted ->
                        if (!granted) {
                            config()
                        }
                    }

                    val launcher = Permissions.getRequestMultiplePermissionsLauncher(activity)
                    NotificationUtils.requestPermission(launcher)
                }

                // If system recommends showing rationale, show an in-app dialog first, then re-request.
                // zh-CN: 若系统建议展示 rationale, 先弹出应用内说明对话框, 再次发起系统权限请求.
                if (shouldShowRationale) {
                    showRationaleDialog(activity, onContinue = launchRequest)
                    return true
                }

                // First request (or still allowed without rationale).
                // zh-CN: 首次请求 (或无需 rationale 也允许再次请求) 时直接发起系统权限请求.
                launchRequest()
                return true
            }
        }
        return false
    }

    private fun showRationaleDialog(activity: FragmentActivity, onContinue: () -> Unit) {
        // Show a rationale dialog before requesting permission again.
        // zh-CN: 在再次请求权限前展示 rationale 对话框.
        runCatching {
            MaterialDialog.Builder(activity)
                .title(R.string.text_post_notifications_permission)
                .content(R.string.text_post_notifications_permission_rationale)
                .widgetThemeColor()
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_continue)
                .positiveColorRes(R.color.dialog_button_attraction)
                .onPositive { d, _ ->
                    d.dismiss()
                    onContinue()
                }
                .onNegative { d, _ -> d.dismiss() }
                .cancelable(true)
                .autoDismiss(false)
                .show()
        }
    }

    override fun revoke(): Boolean = false.also { config() }

    fun config() = NotificationUtils.launchSettings()

    companion object {

        /**
         * One-shot callback for POST_NOTIFICATIONS permission result.
         * zh-CN: POST_NOTIFICATIONS 权限请求的一次性回调, 用于在用户拒绝时回退到设置页.
         */
        val postNotificationsResultCallbackCache =
            WeakHashMap<FragmentActivity, (Boolean) -> Unit>()

        /**
         * Set one-shot result callback for POST_NOTIFICATIONS.
         * zh-CN: 设置 POST_NOTIFICATIONS 的一次性结果回调 (触发一次后自动移除).
         */
        @JvmStatic
        fun setPostNotificationsResultCallback(activity: FragmentActivity, callback: (Boolean) -> Unit) {
            postNotificationsResultCallbackCache[activity] = callback
        }

    }

}