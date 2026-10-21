package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.SharedPreferences
import org.autojs.autojs.core.pref.keys.NamedStores
import org.autojs.autojs.core.pref.keys.PluginCenterKeys.HIDDEN_PACKAGES
import org.autojs.autojs.core.pref.keys.PluginCenterKeys.HIDDEN_SOURCE
import org.autojs.autojs.core.pref.keys.PluginCenterKeys.HIDDEN_TITLE
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.SharedPreferencesStore
import org.autojs.autojs.core.pref.store.set

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 26, 2026.
 *
 * Hidden plugins in the named store `plugin_center_visibility_state`, declared in [org.autojs.autojs.core.pref.keys.PluginCenterKeys]
 * (docs/settings/ROADMAP.md P3.1, names unchanged). The [Context] parameters are kept for the callers; the store is
 * opened by the settings layer with the application context, as before.
 */
object PluginVisibilityStore {

    private val store
        get() = SettingsStores[NamedStores.PLUGIN_CENTER_VISIBILITY_STATE]

    /** The SharedPreferences of the store, for change listeners. */
    private fun preferences(): SharedPreferences = (store as SharedPreferencesStore).preferences

    fun isHidden(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return hiddenPackages(context).contains(packageName)
    }

    fun hide(
        context: Context,
        packageName: String,
        title: String?,
        source: PluginIndexSource?,
    ) {
        setHidden(context, packageName, hidden = true, title = title, source = source)
    }

    fun show(context: Context, packageName: String) {
        setHidden(context, packageName, hidden = false)
    }

    fun setHidden(
        context: Context,
        packageName: String,
        hidden: Boolean,
        title: String? = null,
        source: PluginIndexSource? = null,
    ) {
        val normalized = packageName.trim()
        if (normalized.isBlank()) return

        val nextPackages = hiddenPackages(context).toMutableSet()
        store.edit {
            if (hidden) {
                nextPackages += normalized
                // A null title or source removes the entry, as putString(key, null) did.
                this[HIDDEN_TITLE.key(normalized)] = title?.trim()?.takeIf { it.isNotEmpty() }
                this[HIDDEN_SOURCE.key(normalized)] = source?.name
            } else {
                nextPackages -= normalized
                remove(HIDDEN_TITLE.nameOf(normalized))
                remove(HIDDEN_SOURCE.nameOf(normalized))
            }
            this[HIDDEN_PACKAGES] = nextPackages
        }
    }

    fun listHidden(context: Context): List<HiddenPlugin> {
        return hiddenPackages(context)
            .sortedBy { it.lowercase() }
            .map { pkg ->
                val source = HIDDEN_SOURCE.key(pkg).storedOrNull()
                    ?.runCatching { PluginIndexSource.valueOf(this) }
                    ?.getOrNull()
                val title = HIDDEN_TITLE.key(pkg).storedOrNull()
                    ?.takeIf { it.isNotBlank() }
                HiddenPlugin(pkg, title, source)
            }
    }

    fun hiddenCount(context: Context): Int = hiddenPackages(context).size

    @Suppress("UNUSED_PARAMETER")
    fun registerOnSharedPreferenceChangeListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        preferences().registerOnSharedPreferenceChangeListener(listener)
    }

    @Suppress("UNUSED_PARAMETER")
    fun unregisterOnSharedPreferenceChangeListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        preferences().unregisterOnSharedPreferenceChangeListener(listener)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun hiddenPackages(context: Context): Set<String> = HIDDEN_PACKAGES.get()

    data class HiddenPlugin(
        val packageName: String,
        val title: String?,
        val source: PluginIndexSource?,
    )
}

