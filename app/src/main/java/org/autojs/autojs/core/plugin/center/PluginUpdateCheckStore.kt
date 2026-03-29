package org.autojs.autojs.core.plugin.center

import org.autojs.autojs.core.pref.Pref

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 21, 2026.
 */
object PluginUpdateCheckStore {

    private const val KEY_PREFIX = "key_\$_plugin_update_check_\$_"

    fun isEnabled(packageName: String, defaultEnabled: Boolean = true): Boolean {
        if (packageName.isBlank()) return defaultEnabled
        return Pref.getBoolean(keyOf(packageName), defaultEnabled)
    }

    fun setEnabled(packageName: String, enabled: Boolean) {
        if (packageName.isBlank()) return
        Pref.putBoolean(keyOf(packageName), enabled)
    }

    private fun keyOf(packageName: String) = "$KEY_PREFIX$packageName"
}
