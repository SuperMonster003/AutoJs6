package org.autojs.autojs.permission

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import org.autojs.autojs.ui.main.drawer.PermissionItemHelper
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

/**
 * Created by SuperMonster003 on Jun 21, 2022.
 */
class IgnoreBatteryOptimizationsPermission(override val context: Context) : PermissionItemHelper {

    override fun has(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife")
    override fun request() = Intent()
        .setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData("package:${context.packageName}".toUri())
        .startSafely(context, true) {
            ViewUtils.showToast(context, R.string.text_failed)
        }

    override fun revoke() = Intent()
        .setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .startSafely(context, true) {
            ViewUtils.showToast(context, R.string.text_failed)
        }

}