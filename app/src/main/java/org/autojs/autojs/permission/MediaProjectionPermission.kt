package org.autojs.autojs.permission

import android.content.Context
import org.autojs.autojs.app.AppOps.isProjectMediaAccessGranted
import org.autojs.autojs.permission.Base.PROJECT_MEDIA_PERMISSION
import org.autojs.autojs.ui.main.drawer.CommandBasedPermissionItemHelper
import org.autojs.autojs.ui.main.drawer.IPermissionItem.Companion.ACTION

class MediaProjectionPermission(override val context: Context) : CommandBasedPermissionItemHelper {

    override fun has() = isProjectMediaAccessGranted(context)

    override fun getCommand(action: ACTION): String {
        val actionString = when (action) {
            ACTION.REQUEST -> "allow"
            ACTION.REVOKE -> "ignore"
        }
        return "appops set ${context.packageName} $PROJECT_MEDIA_PERMISSION $actionString"
    }

}