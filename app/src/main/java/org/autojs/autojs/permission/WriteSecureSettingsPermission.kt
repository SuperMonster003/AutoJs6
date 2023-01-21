package org.autojs.autojs.permission

import android.content.Context
import org.autojs.autojs.ui.main.drawer.CommandBasedPermissionItemHelper
import org.autojs.autojs.ui.main.drawer.IPermissionItem.Companion.ACTION
import org.autojs.autojs.util.SettingsUtils

class WriteSecureSettingsPermission(override val context: Context) : CommandBasedPermissionItemHelper {

    override fun has(): Boolean = SettingsUtils.SecureSettings.isGranted(context)

    override fun getCommand(action: ACTION): String {
        val actionString = when (action) {
            ACTION.REQUEST -> "grant"
            ACTION.REVOKE -> "revoke"
        }
        return "pm $actionString ${context.packageName} ${Base.WRITE_SECURE_SETTINGS_PERMISSION}"
    }

}