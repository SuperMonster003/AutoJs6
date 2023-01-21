package org.autojs.autojs.app

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import org.autojs.autojs.util.JavaUtils

object AppOps {

    private fun getAppOps(context: Context) = object {
        val uid = context.applicationInfo.uid
        val packageName = context.applicationInfo.packageName
        val manager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    }

    private fun isOpPermissionGranted(context: Context, permission: Any): Boolean = try {
        getAppOps(context).run {
            when (permission) {
                is Int -> {
                    val cIntPrim = Int::class.javaPrimitiveType
                    val cString = String::class.java
                    manager.javaClass
                        .getMethod("checkOpNoThrow", cIntPrim, cIntPrim, cString)
                        .invoke(manager, permission, uid, packageName)
                }
                else -> permission.toString().let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        manager.unsafeCheckOpNoThrow(it, uid, packageName)
                    } else {
                        @Suppress("DEPRECATION")
                        manager.checkOpNoThrow(it, uid, packageName)
                    }
                }
            } == AppOpsManager.MODE_ALLOWED
        }
    } catch (e: Exception) {
        e.printStackTrace().let { false }
    }

    @JvmStatic
    fun isUsageStatsPermissionGranted(context: Context) = isOpPermissionGranted(context, AppOpsManager.OPSTR_GET_USAGE_STATS)

    @JvmStatic
    fun isProjectMediaAccessGranted(context: Context) = try {
        AppOpsManager::class.java.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // @Const "android:project_media"
                JavaUtils.reflect(it, "OPSTR_PROJECT_MEDIA") as String
            } else {
                // @Const 46
                JavaUtils.reflect(it, "OP_PROJECT_MEDIA") as Int
            }
        }.let { isOpPermissionGranted(context, it) }
    } catch (e: Exception) {
        e.printStackTrace().let { false }
    }

}