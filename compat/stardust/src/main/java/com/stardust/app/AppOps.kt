package com.stardust.app

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager

fun Context.isOpPermissionGranted(permission: String): Boolean {
    val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    @Suppress("DEPRECATION") val mode = appOps.checkOpNoThrow(permission, android.os.Process.myUid(), packageName)

    return if (mode == AppOpsManager.MODE_DEFAULT) {
        checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
    } else {
        mode == AppOpsManager.MODE_ALLOWED
    }
}