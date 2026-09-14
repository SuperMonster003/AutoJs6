package org.autojs.autojs.inrt

import android.Manifest.permission
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import org.autojs.autojs6.R

/** Broad shared-file access, distinct from the private directory containing the packaged project. */
object InrtStorageAccess {

    val legacyPermissions = arrayOf(permission.READ_EXTERNAL_STORAGE, permission.WRITE_EXTERNAL_STORAGE)

    fun has(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) return true
        if (Build.VERSION.SDK_INT >= 29 && !Environment.isExternalStorageLegacy()) return false
        return legacyPermissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    fun isDeclared(context: Context): Boolean {
        @Suppress("DEPRECATION")
        val declared = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
        return if (Build.VERSION.SDK_INT >= 30) {
            permission.MANAGE_EXTERNAL_STORAGE in declared
        } else {
            legacyPermissions.all { it in declared }
        }
    }

    fun settingsIntents(context: Context): List<Intent> {
        val app = Uri.fromParts("package", context.packageName, null)
        return if (Build.VERSION.SDK_INT >= 30) {
            listOf(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, app),
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
            )
        } else {
            listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app))
        }
    }

    fun explanation(context: Context): String = context.getString(
        if (isDeclared(context)) R.string.text_inrt_shared_storage_required else R.string.text_inrt_storage_permission_missing,
    )
}
