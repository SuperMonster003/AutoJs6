package org.autojs.autojs.permission

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.autojs.autojs.ui.enhancedfloaty.util.FloatingWindowPermissionUtil
import ezy.assist.compat.SettingsCompat
import org.autojs.autojs.ui.common.NotAskAgainDialog
import org.autojs.autojs.ui.main.drawer.PermissionItemHelper
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs.util.ThreadUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

/**
 * Created by Stardust on 2018/1/30.
 * Modified by SuperMonster003 as of Apr 10, 2022.
 * Transformed by SuperMonster003 on Jun 21, 2022.
 */
class DisplayOverOtherAppsPermission(override val context: Context) : PermissionItemHelper, AbleToUrge {

    override fun has(): Boolean {
        return SettingsCompat.canDrawOverlays(context)
    }

    override fun request() {
        config()
    }

    override fun revoke() {
        config()
    }

    fun config() {
        try {
            SettingsCompat.manageDrawOverlays(context)
        } catch (ex: Exception) {
            FloatingWindowPermissionUtil.goToAppDetailSettings(context, context.packageName)
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
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post(r)
        } else {
            r.run()
        }
        if (!ThreadUtils.wait({ has() }, 60 * 1000)) {
            ViewUtils.showToast(context, R.string.text_failed_to_grant_draw_overlays_permission, true)
        }
    }

    override fun urge() {
        NotAskAgainDialog.Builder(context, key(R.string.key_dialog_check_display_over_other_apps))
            .title(R.string.text_display_over_other_app)
            .content(
                String.format(
                    "%s\n\n%s",
                    context.getString(R.string.text_display_over_other_app_is_recommended),
                    context.getString(R.string.text_click_ok_to_go_to_access_settings),
                )
            )
            .negativeText(R.string.text_cancel)
            .positiveText(R.string.text_ok)
            .onNegative { dialog, _ -> dialog.dismiss() }
            .onPositive { dialog, _ -> dialog.dismiss().also { config() } }
            .cancelable(false)
            .autoDismiss(false)
            .show()
    }

    override fun urgeIfNeeded() {
        if (!has()) urge()
    }

}