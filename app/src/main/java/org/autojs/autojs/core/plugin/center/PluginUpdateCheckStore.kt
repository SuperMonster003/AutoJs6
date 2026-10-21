package org.autojs.autojs.core.plugin.center

import org.autojs.autojs.core.pref.keys.PluginCenterKeys

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 21, 2026.
 *
 * The "check for updates" switch per plugin in the default preferences, the key family [PluginCenterKeys.UPDATE_CHECK]
 * (a setting of `plugin_center`, docs/settings/ROADMAP.md P3.1 and appendix C Q13 c, names unchanged).
 */
object PluginUpdateCheckStore {

    fun isEnabled(packageName: String, defaultEnabled: Boolean = true): Boolean {
        if (packageName.isBlank()) return defaultEnabled
        return PluginCenterKeys.UPDATE_CHECK.key(packageName).storedOrNull() ?: defaultEnabled
    }

    fun setEnabled(packageName: String, enabled: Boolean) {
        if (packageName.isBlank()) return
        PluginCenterKeys.UPDATE_CHECK.key(packageName).set(enabled)
    }
}
