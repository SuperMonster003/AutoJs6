package org.autojs.autojs.timing

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.ui.common.NotAskAgainDialog
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

object ExactAlarmPermissionHelper {

    @RequiresApi(Build.VERSION_CODES.S)
    fun canScheduleExactAlarms(context: Context): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun requestExactAlarmPermission(context: Context): Boolean {
        val intent = Intent()
            .setAction(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData("package:${context.packageName}".toUri())
        return runCatching { intent.startSafely(context) }.isSuccess
    }

    // Check canScheduleExactAlarms permission and prompt user if needed.
    // zh-CN: 在此检查 canScheduleExactAlarms 并按需提示用户打开权限.
    fun checkExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (canScheduleExactAlarms(context)) return
        NotAskAgainDialog.Builder(context, key(R.string.key_timed_task_backend_alarm))
            .title(R.string.text_prompt)
            .content(
                String.format(
                    "%s\n\n%s",
                    context.getString(R.string.text_allow_setting_alarms_and_reminders_is_recommended),
                    context.getString(R.string.text_click_ok_to_go_to_settings),
                )
            )
            .widgetThemeColor()
            .negativeText(R.string.text_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { dialog, _ -> dialog.dismiss() }
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { dialog, _ ->
                when {
                    requestExactAlarmPermission(context) -> dialog.dismiss()
                    else -> ViewUtils.showSnack(dialog.view, R.string.error_failed_to_go_to_access_settings, true)
                }
            }
            .cancelable(false)
            .autoDismiss(false)
            .showAdaptive()
    }

}