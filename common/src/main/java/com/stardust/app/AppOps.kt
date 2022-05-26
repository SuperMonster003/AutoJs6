package com.stardust.app

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

fun Context.isOpPermissionGranted(permission: String): Boolean {
    return try {
        val packageManager: PackageManager = this.packageManager
        val applicationInfo: ApplicationInfo = packageManager.getApplicationInfo(this.packageName, 0)
        val appOpsManager: AppOpsManager = this.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val uid = applicationInfo.uid
        val packageName = applicationInfo.packageName
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(permission, uid, packageName)
        } else {
            if (permission.matches(Regex("^\\d+$"))) {
                // @Reflect by SuperMonster003 on May 1, 2022.
                //  ! checkOpNoThrow(int op, int uid, String packageName): int
                appOpsManager.javaClass
                    .getMethod("checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)
                    .invoke(appOpsManager, permission.toInt(), uid, packageName)
            } else {
                @Suppress("DEPRECATION")
                appOpsManager.checkOpNoThrow(permission, uid, packageName)
            }
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

fun Context.isUsageStatsPermissionGranted(): Boolean {
    return isOpPermissionGranted(AppOpsManager.OPSTR_GET_USAGE_STATS)
}

// FIXME by SuperMonster003 as of May 1, 2022.
//  ! A better long-term maintainability is required to replace hardcoded strings.
fun Context.isProjectMediaAccessGranted(): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        /*
            FIXME by SuperMonster003 as of May 1, 2022.
             ! AppOpsManager.OPSTR_PROJECT_MEDIA is hide and is a system api.
             ! String value may change or be invalid some day.
         */
        "android:project_media" /* AppOpsManager.OPSTR_PROJECT_MEDIA */
    } else {
        /*
            FIXME by SuperMonster003 as of May 1, 2022.
             ! AppOpsManager.OP_PROJECT_MEDIA is annotated with "UnsupportedAppUsage".
             ! As is known to all, hardcoded string usage is always the worst plan.
         */
        "46" /* String of AppOpsManager.OP_PROJECT_MEDIA */
    }
    return isOpPermissionGranted(permission)
}