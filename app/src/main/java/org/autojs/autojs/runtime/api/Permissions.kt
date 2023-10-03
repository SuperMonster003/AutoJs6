package org.autojs.autojs.runtime.api

import android.content.Context
import android.content.Intent
import org.autojs.autojs.util.RomUtils

class Permissions(private val context: Context) {

    var postNotifications: IPermissionToggleable? = null
    var notificationAccess: IPermissionToggleable? = null
    var usageStatesAccess: IPermissionToggleable? = null
    var ignoreBatteryOptimizations: IPermissionToggleable? = null
    var backgroundStart = object : IPermissionToggleable {
        override val description = "后台弹出界面 / Start in background"
        override fun has() = RomUtils.isBackgroundStartGranted(context)
        override fun config() = when {
            RomUtils.isMiui() -> {
                Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    setClassName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity",
                    )
                    putExtra("extra_pkgname", context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }.let { context.startActivity(it) }
            }
            else -> super.config()
        }
    }
    var displayOverOtherApps: IPermissionToggleable? = null
    var writeSystemSettings: IPermissionToggleable? = null
    var writeSecuritySettings: IPermissionToggleable? = null
    var projectMediaAccess: IPermissionToggleable? = null
    val isBackgroundStartGranted: Boolean
        get() = RomUtils.isBackgroundStartGranted(context)

    // TODO by SuperMonster003 on Aug 28, 2023.

    /* permissions.postNotifications */
    /* permissions.notificationAccess */
    /* permissions.usageStatesAccess */
    /* permissions.ignoreBatteryOptimizations */
    /* permissions.backgroundStart */
    /* permissions.displayOverOtherApps */
    /* permissions.writeSystemSettings */
    /* permissions.writeSecuritySettings */
    /* permissions.projectMediaAccess */

}
