package org.autojs.autojs.core.plugin.center

import android.content.Context
import androidx.core.content.edit

object PluginActivationStore {

    private const val SP_NAME = "plugin_center_activation"

    fun markActivated(context: Context, pluginId: String, timeMillis: Long = System.currentTimeMillis()) {
        val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        sp.edit { putLong(key(pluginId), timeMillis) }
    }

    fun getLastActivatedAt(context: Context, pluginId: String): Long? {
        val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        val value = sp.getLong(key(pluginId), -1L)
        return value.takeIf { it > 0L }
    }

    private fun key(pluginId: String) = "key_\$_plugin_activated_\$_$pluginId"
}
