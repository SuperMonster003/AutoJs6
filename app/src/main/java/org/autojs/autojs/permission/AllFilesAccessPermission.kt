package org.autojs.autojs.permission

import android.Manifest.permission
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import org.autojs.autojs.permission.Base.getPermissionsToRequest
import org.autojs.autojs.ui.main.drawer.PermissionItemHelper
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs6.R

/**
 * Created by SuperMonster003 on Jun 21, 2022.
 */
class AllFilesAccessPermission(override val context: Context) : PermissionItemHelper {

    override fun has(): Boolean =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                Environment.isExternalStorageManager()
            }
            else -> getPermissionsToRequest(context, LEGACY_STORAGE_PERMISSIONS).isEmpty()
        }

    override fun request(): Boolean =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                manageAllFilesAccess()
            }
            else -> requestLegacyStoragePermissions() || launchAppDetailsSettings()
        }

    private fun requestLegacyStoragePermissions(): Boolean =
        runCatching {
            ActivityCompat.requestPermissions((context as Activity), LEGACY_STORAGE_PERMISSIONS, Base.REQUEST_CODE)
        }.isSuccess

    override fun revoke(): Boolean {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                manageAllFilesAccess()
            }
            else -> launchAppDetailsSettings()
        }
        return false
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun manageAllFilesAccess(): Boolean = when {
        Intent()
            .setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            .setData(Uri.fromParts("package", context.packageName, null))
            .startSafely(context) -> true
        Intent()
            .setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            .startSafely(context) -> true
        else -> false
    }

    private fun launchAppDetailsSettings() = IntentUtils.launchAppDetailsSettings(context)

    fun config(): Boolean =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                manageAllFilesAccess()
            }
            else -> launchAppDetailsSettings()
        }

    companion object {
        private val LEGACY_STORAGE_PERMISSIONS = arrayOf(permission.READ_EXTERNAL_STORAGE, permission.WRITE_EXTERNAL_STORAGE)
    }

}