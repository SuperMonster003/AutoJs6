package org.autojs.autojs.permission

import android.content.Context
import android.os.Build
import androidx.fragment.app.FragmentActivity
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.runtime.api.Permissions
import org.autojs.autojs.ui.main.drawer.PermissionItemHelper
import org.autojs.autojs.util.NotificationUtils
import org.autojs.autojs6.R

/**
 * Created by SuperMonster003 on May 4, 2023.
 */
class PostNotificationsPermission(override val context: Context) : PermissionItemHelper, AbleToUrge {

    override fun has() = NotificationUtils.isEnabled()

    override fun request(): Boolean = false.also { config() }

    override fun revoke(): Boolean = false.also { config() }

    private fun config() = NotificationUtils.launchSettings()

    override fun urge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (val ctx = context) {
                is FragmentActivity -> Permissions.getRequestMultiplePermissionsLauncher(ctx).let {
                    NotificationUtils.requestPermission(it)
                }
            }
        }
    }

    override fun urgeIfNeeded() {
        if (!Pref.getBoolean(R.string.key_post_notification_permission_requested, false)) {
            if (!has()) {
                urge()
            }
        }
    }

}