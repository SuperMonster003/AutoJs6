package org.autojs.autojs.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import org.autojs.autojs.ui.main.drawer.PermissionItemHelper

class WriteSystemSettingsPermission(override val context: Context) : PermissionItemHelper {

    override fun has() = Settings.System.canWrite(context)

    override fun request() = config()

    override fun revoke() = config()

    fun config() = context.startActivity(
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

}