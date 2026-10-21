package org.autojs.autojs.core.plugin.center

import org.autojs.autojs.core.pref.keys.PluginCenterKeys

/**
 * Last install and uninstall times per plugin in the named store `plugin_center_recent`, the state key families
 * [PluginCenterKeys.LAST_INSTALL_TIME] and [PluginCenterKeys.LAST_UNINSTALL_TIME] (docs/settings/ROADMAP.md P3.1,
 * names unchanged).
 */
object PluginRecentStore {

    fun setLastInstalled(packageName: String, ts: Long = System.currentTimeMillis()) {
        PluginCenterKeys.LAST_INSTALL_TIME.key(packageName).set(ts)
    }

    fun setLastUninstalled(packageName: String, ts: Long = System.currentTimeMillis()) {
        PluginCenterKeys.LAST_UNINSTALL_TIME.key(packageName).set(ts)
    }

    fun getLastInstalled(packageName: String): Long? {
        return PluginCenterKeys.LAST_INSTALL_TIME.key(packageName).get().takeIf { it > 0 }
    }

    fun getLastUninstalled(packageName: String): Long? {
        return PluginCenterKeys.LAST_UNINSTALL_TIME.key(packageName).get().takeIf { it > 0 }
    }

}