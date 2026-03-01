package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.autojs.autojs.core.plugin.center.PluginCenterFragment.Sort

object PluginSortStore {

    private const val SP_NAME = "plugin_center_sort_state"
    private const val KEY = "key_\$_plugin_center_sort_state"

    private fun sp(context: Context) = context.applicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

    fun getSort(context: Context, defaultSort: Int = Sort.LAST_UPDATE_DESC.ordinal): Sort {
        val ordinal = getSortOrdinal(context, defaultSort)
        return Sort.values()[ordinal]
    }

    fun getSortOrdinal(context: Context, defaultSortOrdinal: Int = Sort.LAST_UPDATE_DESC.ordinal): Int {
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

    fun registerOnSharedPreferenceChangeListener(context: Context, onSharedPreferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener) {
        val sp = sp(context)
        sp.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
    }

    fun unregisterOnSharedPreferenceChangeListener(context: Context, onSharedPreferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener) {
        val sp = sp(context)
        sp.unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
    }
}
