package com.stardust.app

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.KITKAT)
fun Context.isOpPermissionGranted(permission: String): Boolean {
    return try {
        val packageManager: PackageManager = this.packageManager
        val applicationInfo: ApplicationInfo = packageManager.getApplicationInfo(this.packageName, 0)
        val appOpsManager: AppOpsManager = this.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29 /* Build.VERSION_CODES.Q */) {
            appOpsManager.unsafeCheckOpNoThrow(permission, applicationInfo.uid, applicationInfo.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(permission, applicationInfo.uid, applicationInfo.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

fun Context.isUsageStatsPermissionGranted(): Boolean {
    return this.isOpPermissionGranted(AppOpsManager.OPSTR_GET_USAGE_STATS)
}