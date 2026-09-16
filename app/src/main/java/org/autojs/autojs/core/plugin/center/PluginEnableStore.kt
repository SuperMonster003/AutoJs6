package org.autojs.autojs.core.plugin.center

import android.content.Context
import androidx.core.content.edit

object PluginEnableStore {

    private const val SP_NAME = "plugin_center_enable_state"

    fun isEnabled(context: Context, packageName: String, defaultEnabled: Boolean = true): Boolean {
        val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        val enabled = sp.getBoolean(key(packageName), defaultEnabled)
        if (enabled && !PluginLocalNetworkPermission.isGranted(context, packageName)) {
            setEnabled(context, packageName, false)
            return false
        }
        return enabled
    }

    fun setEnabled(context: Context, packageName: String, enabled: Boolean) {
        val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        val key = key(packageName)
        val allowed = enabled && PluginLocalNetworkPermission.isGranted(context, packageName)
        if (sp.contains(key) && sp.getBoolean(key, true) == allowed) return
        sp.edit { putBoolean(key, allowed) }
        PluginStateChangeRegistry.notifyChanged(packageName)
    }

    private fun key(packageName: String) = "key_\$_enabled_plugin_\$_$packageName"

}
