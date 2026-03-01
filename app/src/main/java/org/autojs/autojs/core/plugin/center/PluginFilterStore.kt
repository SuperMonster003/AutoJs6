package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.autojs.autojs.core.plugin.center.PluginCenterFragment.Filter

object PluginFilterStore {

    private const val SP_NAME = "plugin_center_filter_state"
    internal const val KEY = "key_\$_plugin_center_filter_state"

    private fun sp(context: Context) = context.applicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

    fun getFilter(context: Context, defaultFilter: Int = Filter.ALL.ordinal): Filter {
        val ordinal = getFilterOrdinal(context, defaultFilter)
        return Filter.values()[ordinal]
    }

    fun getFilterOrdinal(context: Context, defaultFilterOrdinal: Int = Filter.ALL.ordinal): Int {
        val sp = sp(context)
        return sp.getInt(KEY, defaultFilterOrdinal)
    }

    fun setFilter(context: Context, filter: Filter) {
        setFilterOrdinal(context, filter.ordinal)
    }

    fun setFilterOrdinal(context: Context, filterOrdinal: Int) {
        val sp = sp(context)
        sp.edit { putInt(KEY, filterOrdinal) }
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
