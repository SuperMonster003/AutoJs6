package org.autojs.autojs.core.plugin.center

import android.content.Context
import androidx.core.content.edit

object PluginEnableStore {

    private const val SP_NAME = "plugin_center_enable_state"

    fun isEnabled(context: Context, packageName: String, defaultEnabled: Boolean = true): Boolean {
        val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(key(packageName), defaultEnabled)
    }

    fun setEnabled(context: Context, packageName: String, enabled: Boolean) {
        val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        sp.edit { putBoolean(key(packageName), enabled) }
    }

    private fun key(packageName: String) = "key_\$_enabled_plugin_\$_$packageName"

}
