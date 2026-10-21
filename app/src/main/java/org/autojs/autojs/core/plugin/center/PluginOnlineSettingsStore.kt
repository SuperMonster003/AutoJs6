package org.autojs.autojs.core.plugin.center

import android.content.SharedPreferences
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.core.pref.keys.PluginCenterKeys
import org.autojs.autojs6.R

object PluginOnlineSettingsStore {

    private const val AUTO_CHECK_INTERVAL_MS = 60 * 60 * 1000L

    fun isOfficialEnabled(): Boolean = PluginCenterKeys.PLUGIN_CENTER_SHOW_OFFICIAL_PLUGINS.get()

    fun isThirdPartyEnabled(): Boolean = PluginCenterKeys.PLUGIN_CENTER_SHOW_THIRD_PARTY_PLUGINS.get()

    fun isAutoCheckUpdatesEnabled(): Boolean = PluginCenterKeys.PLUGIN_CENTER_AUTO_CHECK_UPDATES.get()

    fun selectedSources(): Set<PluginIndexSource> {
        val sources = linkedSetOf<PluginIndexSource>()
        if (isOfficialEnabled()) {
            sources += PluginIndexSource.OFFICIAL
        }
        if (isThirdPartyEnabled()) {
            sources += PluginIndexSource.THIRD_PARTY
        }
        return sources
    }

    fun shouldAutoCheckNow(now: Long = System.currentTimeMillis()): Boolean {
        if (!isAutoCheckUpdatesEnabled()) {
            return false
        }
        val last = MiscStateKeys.PLUGIN_CENTER_LAST_ONLINE_CHECK_TS.get()
        return now - last >= AUTO_CHECK_INTERVAL_MS
    }

    fun markCheckedNow(now: Long = System.currentTimeMillis()) {
        MiscStateKeys.PLUGIN_CENTER_LAST_ONLINE_CHECK_TS.set(now)
    }

    fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        Pref.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        Pref.get().unregisterOnSharedPreferenceChangeListener(listener)
    }
}
