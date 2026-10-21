package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.SharedPreferences
import org.autojs.autojs.core.plugin.center.PluginCenterFragment.Sort
import org.autojs.autojs.core.pref.keys.NamedStores
import org.autojs.autojs.core.pref.keys.PluginCenterKeys
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.SharedPreferencesStore

/**
 * Sort memory of the list of all plugins in the named store `plugin_center_sort_state`, shared with `PluginSortStoreOfficial` and `PluginSortStoreThirdParty`; the keys are
 * [PluginCenterKeys.SORT_ALL] (docs/settings/ROADMAP.md P3.1, names unchanged). The [Context] parameters are kept for the
 * callers; the store is opened by the settings layer with the application context, as before.
 */
object PluginSortStore {

    private val keys = PluginCenterKeys.SORT_ALL

    private val store
        get() = SettingsStores[NamedStores.PLUGIN_CENTER_SORT_STATE]

    /** The SharedPreferences of the store, for change listeners. */
    private fun preferences(): SharedPreferences = (store as SharedPreferencesStore).preferences

    fun getSort(context: Context, defaultSort: Int = Sort.defaultPrimarySort().ordinal): Sort {
        val ordinal = getSortOrdinal(context, defaultSort)
        return sortByOrdinalOrDefault(ordinal, defaultSort)
    }

    @Suppress("UNUSED_PARAMETER")
    fun getSortOrdinal(context: Context, defaultSortOrdinal: Int = Sort.defaultPrimarySort().ordinal): Int {
        return keys.sort.storedOrNull() ?: defaultSortOrdinal
    }

    fun setSort(context: Context, sort: Sort) {
        setSortOrdinal(context, sort.ordinal)
    }

    @Suppress("UNUSED_PARAMETER")
    fun setSortOrdinal(context: Context, sortOrdinal: Int) {
        keys.sort.set(sortOrdinal)
    }

    @Suppress("UNUSED_PARAMETER")
    fun getAscending(
        context: Context,
        sort: Sort,
        defaultAscending: Boolean = sort.defaultAscending,
    ): Boolean {
        return keys.ascending.key(sort.name).storedOrNull() ?: defaultAscending
    }

    @Suppress("UNUSED_PARAMETER")
    fun setAscending(context: Context, sort: Sort, ascending: Boolean) {
        keys.ascending.key(sort.name).set(ascending)
    }

    @Suppress("UNUSED_PARAMETER")
    fun getSecondarySort(context: Context, primary: Sort): Sort {
        val defaultSort = primary.defaultSecondarySort()
        val ordinal = secondaryKey(primary).storedOrNull() ?: defaultSort.ordinal
        val resolved = sortByOrdinalOrDefault(ordinal, defaultSort.ordinal)
        return if (resolved == primary) defaultSort else resolved
    }

    @Suppress("UNUSED_PARAMETER")
    fun setSecondarySort(context: Context, primary: Sort, secondary: Sort) {
        if (secondary == primary) return
        secondaryKey(primary).set(secondary.ordinal)
    }

    // Retain the existing preference keys for mechanism and source sorting.
    // zh-CN: 保留机制与来源排序的既有偏好键.
    private fun secondaryKey(primary: Sort) = when (primary) {
        Sort.MECHANISM -> keys.mechanismSecondary
        Sort.SOURCE -> keys.sourceSecondary
        else -> keys.secondary.key(primary.name)
    }

    @Suppress("UNUSED_PARAMETER")
    fun clearAllSortMemory(context: Context) {
        store.edit {
            remove(keys.sort.name)
            remove(keys.mechanismSecondary.name)
            remove(keys.sourceSecondary.name)
            Sort.values().forEach { sort ->
                remove(keys.ascending.nameOf(sort.name))
                remove(secondaryKey(sort).name)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun registerOnSharedPreferenceChangeListener(context: Context, onSharedPreferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences().registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
    }

    @Suppress("UNUSED_PARAMETER")
    fun unregisterOnSharedPreferenceChangeListener(context: Context, onSharedPreferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences().unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
    }

    private fun sortByOrdinalOrDefault(ordinal: Int, defaultOrdinal: Int): Sort {
        val values = Sort.values()
        return values.getOrNull(ordinal) ?: values.getOrNull(defaultOrdinal) ?: Sort.defaultPrimarySort()
    }
}
