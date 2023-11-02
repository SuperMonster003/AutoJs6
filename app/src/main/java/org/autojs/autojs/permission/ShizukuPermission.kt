package org.autojs.autojs.permission

import android.content.Context
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.ui.main.drawer.PermissionItemHelper
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import rikka.shizuku.Shizuku

class ShizukuPermission(override val context: Context) : PermissionItemHelper {

    override fun has() = WrappedShizuku.hasPermission() && WrappedShizuku.isRunning()

    override fun request() {
        when {
            !WrappedShizuku.isInstalled() -> ViewUtils.showToast(context, R.string.error_shizuku_is_not_installed)
            Shizuku.isPreV11() -> ViewUtils.showToast(context, R.string.error_shizuku_version_is_not_supported)
            else -> {
                try {
                    if (!Shizuku.shouldShowRequestPermissionRationale()) {
                        WrappedShizuku.requestPermission()
                        return
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
                WrappedShizuku.config(context, true) ?: ViewUtils.showToast(context, R.string.error_failed_to_grant_shizuku_access)
            }
        }
    }

    override fun revoke() {
        when {
            !WrappedShizuku.isInstalled() -> ViewUtils.showToast(context, R.string.error_shizuku_is_not_installed)
            Shizuku.isPreV11() -> ViewUtils.showToast(context, R.string.error_shizuku_version_is_not_supported)
            else -> WrappedShizuku.config(context, false) ?: ViewUtils.showToast(context, R.string.error_failed_to_revoke_shizuku_access)
        }
    }

}