package org.autojs.autojs.core.plugin.center

import android.content.Context
import androidx.core.content.edit
import org.autojs.autojs.app.GlobalAppContext

object PluginRecentStore {

    private const val SP = "plugin_center_recent"

    private val context by lazy { GlobalAppContext.get() }

    fun setLastInstalled(packageName: String, ts: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(SP, Context.MODE_PRIVATE).edit {
            putLong(keyInstalled(packageName), ts)
        }
    }

    fun setLastUninstalled(packageName: String, ts: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(SP, Context.MODE_PRIVATE).edit {
            putLong(keyUninstalled(packageName), ts)
        }
    }

    fun getLastInstalled(packageName: String): Long? {
        return context.getSharedPreferences(SP, Context.MODE_PRIVATE).getLong(keyInstalled(packageName), 0L).takeIf { it > 0 }
    }

    fun getLastUninstalled(packageName: String): Long? {
        return context.getSharedPreferences(SP, Context.MODE_PRIVATE).getLong(keyUninstalled(packageName), 0L).takeIf { it > 0 }
    }

    private fun keyInstalled(packageName: String) = "key_\$_last_install_time_\$_$packageName"

    private fun keyUninstalled(packageName: String) = "key_\$_last_uninstall_time_\$_$packageName"

}