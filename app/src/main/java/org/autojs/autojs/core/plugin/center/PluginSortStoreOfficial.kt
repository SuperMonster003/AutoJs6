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

    fun getMechanismSecondarySort(
        context: Context,
        defaultSort: Sort = Sort.LAST_UPDATE_DESC,
    ): Sort {
        val ordinal = sp(context).getInt(KEY_MECHANISM_SECONDARY, defaultSort.ordinal)
        val resolved = sortByOrdinalOrDefault(ordinal, defaultSort.ordinal)
        return if (resolved == Sort.MECHANISM) defaultSort else resolved
    }

    fun setMechanismSecondarySort(context: Context, sort: Sort) {
        if (sort == Sort.MECHANISM) return
        val sp = sp(context)
        sp.edit { putInt(KEY_MECHANISM_SECONDARY, sort.ordinal) }
    }

    fun getSourceSecondarySort(
        context: Context,
        defaultSort: Sort = Sort.LAST_UPDATE_DESC,
    ): Sort {
        val ordinal = sp(context).getInt(KEY_SOURCE_SECONDARY, defaultSort.ordinal)
        val resolved = sortByOrdinalOrDefault(ordinal, defaultSort.ordinal)
        return if (resolved == Sort.SOURCE) defaultSort else resolved
    }

    fun setSourceSecondarySort(context: Context, sort: Sort) {
        if (sort == Sort.SOURCE) return
        val sp = sp(context)
        sp.edit { putInt(KEY_SOURCE_SECONDARY, sort.ordinal) }
    }

    fun clearAllSortMemory(context: Context) {
        val sp = sp(context)
        sp.edit {
            remove(KEY)
            remove(KEY_MECHANISM_SECONDARY)
            remove(KEY_SOURCE_SECONDARY)
            Sort.values().forEach { sort ->
                remove("$KEY_ASC_PREFIX${sort.name}")
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
