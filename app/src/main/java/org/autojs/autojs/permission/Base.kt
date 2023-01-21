package org.autojs.autojs.permission

import android.content.Context
import android.content.pm.PackageManager
import java.util.*

object Base {

    const val REQUEST_CODE = 11186

    const val WRITE_SECURE_SETTINGS_PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"

    const val PROJECT_MEDIA_PERMISSION = "PROJECT_MEDIA"

    private const val PREFIX = "android.permission."

    private val EMPTY_STRING_ARRAY = arrayOf<String>() // new String[0]

    @JvmStatic
    fun getPermissionsToRequest(context: Context, permissions: Array<String>): Array<String> {
        val list = ArrayList<String>()
        for (permission in permissions) {
            var perm = permission
            if (!perm.startsWith(PREFIX)) {
                perm = PREFIX + perm.uppercase(Locale.getDefault())
            }
            if (context.checkSelfPermission(perm) == PackageManager.PERMISSION_DENIED) {
                list.add(perm)
            }
        }
        return list.toArray(EMPTY_STRING_ARRAY)
    }

}