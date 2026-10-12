package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.autojs.autojs.core.plugin.center.PluginCenterFragment.Sort

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 22, 2026.
 */
object PluginSortStoreOfficial {

    private const val SP_NAME = "plugin_center_sort_state"
    private const val KEY = "key_\$_plugin_center_sort_state_official"
    private const val KEY_ASC_PREFIX = "key_\$_plugin_center_sort_state_official_ascending_"
    private const val KEY_SECONDARY_PREFIX = "key_\$_plugin_center_sort_state_official_secondary_"
    private const val KEY_MECHANISM_SECONDARY = "key_\$_plugin_center_sort_state_official_mechanism_secondary"
    private const val KEY_SOURCE_SECONDARY = "key_\$_plugin_center_sort_state_official_source_secondary"

    private fun sp(context: Context) = context.applicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

    fun getSort(context: Context, defaultSort: Int = Sort.defaultPrimarySort().ordinal): Sort {
        val ordinal = getSortOrdinal(context, defaultSort)
        return sortByOrdinalOrDefault(ordinal, defaultSort)
    }

    fun getSortOrdinal(context: Context, defaultSortOrdinal: Int = Sort.defaultPrimarySort().ordinal): Int {
        val sp = sp(context)
        return sp.getInt(KEY, defaultSortOrdinal)
    }

    fun setSort(context: Context, sort: Sort) {
        setSortOrdinal(context, sort.ordinal)
    }

    fun setSortOrdinal(context: Context, sortOrdinal: Int) {
        val sp = sp(context)
        sp.edit { putInt(KEY, sortOrdinal) }
    }

    fun getAscending(
        context: Context,
        sort: Sort,
        defaultAscending: Boolean = sort.defaultAscending,
    ): Boolean {
        val sp = sp(context)
        return sp.getBoolean("$KEY_ASC_PREFIX${sort.name}", defaultAscending)
    }

    fun setAscending(context: Context, sort: Sort, ascending: Boolean) {
        val sp = sp(context)
        sp.edit { putBoolean("$KEY_ASC_PREFIX${sort.name}", ascending) }
    }

    fun getSecondarySort(context: Context, primary: Sort): Sort {
        val defaultSort = primary.defaultSecondarySort()
        val ordinal = sp(context).getInt(secondaryKey(primary), defaultSort.ordinal)
        val resolved = sortByOrdinalOrDefault(ordinal, defaultSort.ordinal)
        return if (resolved == primary) defaultSort else resolved
    }

    fun setSecondarySort(context: Context, primary: Sort, secondary: Sort) {
        if (secondary == primary) return
        sp(context).edit { putInt(secondaryKey(primary), secondary.ordinal) }
    }

    // Retain the existing preference keys for mechanism and source sorting.
    // zh-CN: 保留机制与来源排序的既有偏好键.
    private fun secondaryKey(primary: Sort): String = when (primary) {
        Sort.MECHANISM -> KEY_MECHANISM_SECONDARY
        Sort.SOURCE -> KEY_SOURCE_SECONDARY
        else -> "$KEY_SECONDARY_PREFIX${primary.name}"
    }

    fun clearAllSortMemory(context: Context) {
        val sp = sp(context)
        sp.edit {
            remove(KEY)
            remove(KEY_MECHANISM_SECONDARY)
            remove(KEY_SOURCE_SECONDARY)
            Sort.values().forEach { sort ->
                remove("$KEY_ASC_PREFIX${sort.name}")
                remove(secondaryKey(sort))
            }
        }
    }

    fun registerOnSharedPreferenceChangeListener(context: Context, onSharedPreferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener) {
        val sp = sp(context)
        sp.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
    }

    fun unregisterOnSharedPreferenceChangeListener(context: Context, onSharedPreferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener) {
        val sp = sp(context)
        sp.unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
    }

    private fun sortByOrdinalOrDefault(ordinal: Int, defaultOrdinal: Int): Sort {
        val values = Sort.values()
        return values.getOrNull(ordinal) ?: values.getOrNull(defaultOrdinal) ?: Sort.defaultPrimarySort()
    }
}
