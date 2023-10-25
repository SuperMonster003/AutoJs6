package org.autojs.autojs.permission

import android.Manifest.permission
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.ActivityCompat
import org.autojs.autojs.permission.Base.getPermissionsToRequest
import org.autojs.autojs.ui.common.NotAskAgainDialog
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.ui.main.drawer.PermissionItemHelper
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs6.R

/**
 * Created by SuperMonster003 on Jun 21, 2022.
 */
class ManageAllFilesPermission(override val context: Context) : PermissionItemHelper, AbleToUrge {

    override fun has(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            getPermissionsToRequest(context, LEGACY_STORAGE_PERMISSIONS).isEmpty()
        }
    }

    override fun request() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent()
                    .setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    .setData(Uri.fromParts("package", context.packageName, null))
                    .let { context.startActivity(it) }
            } catch (e: Exception) {
                Intent()
                    .setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    .let { context.startActivity(it) }
            }
        } else {
            try {
                ActivityCompat.requestPermissions((context as Activity), LEGACY_STORAGE_PERMISSIONS, Base.REQUEST_CODE)
            } catch (e: Exception) {
                config()
            }
        }
    }

    override fun revoke() = config()

    private fun config() {
        IntentUtils.goToAppDetailSettings(context, context.packageName)
    }

    override fun urge() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return Unit.also { request() }
        }
        NotAskAgainDialog.Builder(context, key(R.string.key_dialog_manage_all_files_permission))
            .title(R.string.text_all_files_access)
            .content(
                String.format(
                    "%s\n\n%s",
                    context.getString(R.string.text_all_files_access_is_needed),
                    context.getString(R.string.text_click_ok_to_go_to_access_settings),
                )
            )
            .negativeText(R.string.text_cancel)
            .positiveText(R.string.text_ok)
            .onNegative { dialog, _ -> dialog.dismiss() }
            .onPositive { dialog, _ ->
                request()
                MainActivity.shouldRecreateMainActivity = true
                dialog.dismiss()
            }
            .cancelable(false)
            .autoDismiss(false)
            .show()
    }

    override fun urgeIfNeeded() {
        if (!has()) urge()
    }

    companion object {
        private val LEGACY_STORAGE_PERMISSIONS = arrayOf(permission.READ_EXTERNAL_STORAGE, permission.WRITE_EXTERNAL_STORAGE)
    }

}