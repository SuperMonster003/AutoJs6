package org.autojs.autojs.permission

import android.content.Context
import android.os.Handler
import android.os.Looper
import ezy.assist.compat.SettingsCompat
import org.autojs.autojs.ui.main.drawer.PermissionItemHelper
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.RhinoUtils.isMainThread
import org.autojs.autojs.util.ThreadUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

/**
 * Created by Stardust on Jan 30, 2018.
 * Modified by SuperMonster003 as of Apr 10, 2022.
 * Transformed by SuperMonster003 on Jun 21, 2022.
 */
class DisplayOverOtherAppsPermission(override val context: Context) : PermissionItemHelper {

    override fun has() = SettingsCompat.canDrawOverlays(context)

    override fun request() = false.also { config() }

    override fun revoke() = false.also { config() }

    fun config() {
        try {
            SettingsCompat.manageDrawOverlays(context)
        } catch (e: Exception) {
            e.printStackTrace()
            IntentUtils.launchAppDetailsSettings(context)
        }
    }

    @Throws(InterruptedException::class)
    fun waitFor() {
        if (has()) {
            return
        }
        val r = Runnable {
            toggle()
            ViewUtils.showToast(context, R.string.error_no_display_over_other_apps_permission)
        }
        if (!isMainThread()) {
            Handler(Looper.getMainLooper()).post(r)
        } else {
            r.run()
        }
        if (!ThreadUtils.wait({ has() }, 60 * 1000)) {
            ViewUtils.showToast(context, R.string.text_failed_to_grant_draw_overlays_permission, true)
        }
    }

}