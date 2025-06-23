package org.autojs.autojs.runtime.api

import android.Manifest
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.fragment.app.FragmentActivity
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.util.RomUtils
import org.autojs.autojs6.R

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

    companion object {

        private val TAG = Permissions::class.java.simpleName

        @JvmStatic
        fun getRequestMultiplePermissionsLauncher(activity: FragmentActivity): ActivityResultLauncher<Array<String>> = activity.registerForActivityResult(RequestMultiplePermissions()) {
            it.forEach { (key: String, isGranted: Boolean) ->
                Log.d(TAG, "$key: $isGranted")
                if (key == Manifest.permission.POST_NOTIFICATIONS) {
                    Pref.putBoolean(R.string.key_post_notification_permission_requested, true)
                }
            }
        }

    }

}
