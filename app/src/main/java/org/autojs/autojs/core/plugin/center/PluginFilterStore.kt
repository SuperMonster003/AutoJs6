package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.autojs.autojs6.R

object PluginFilterStore {

    private const val SP_NAME = "plugin_center_filter_state"
    private const val KEY_LEGACY_FILTER_ORDINAL = "key_\$_plugin_center_filter_state"
    private const val KEY_INSTALL_STATE = "key_\$_plugin_center_filter_install_state"
    private const val KEY_ENABLED_STATE = "key_\$_plugin_center_filter_enabled_state"
    private const val KEY_MECHANISM_STATE = "key_\$_plugin_center_filter_mechanism_state"
    private const val KEY_SOURCE_STATE = "key_\$_plugin_center_filter_source_state"
    private const val KEY_UPDATABLE_ONLY = "key_\$_plugin_center_filter_updatable_only"

    data class FilterState(
        val installState: InstallState = InstallState.ALL,
        val enabledState: EnabledState = EnabledState.ALL,
        val mechanismState: MechanismState = MechanismState.ALL,
        val sourceState: SourceState = SourceState.ALL,
        val updatableOnly: Boolean = false,
    ) {
        fun isDefault(): Boolean = this == FilterState()
    }

    enum class InstallState(val titleRes: Int) {
        ALL(R.string.text_all),
        INSTALLED(R.string.text_installed),
        NOT_INSTALLED(R.string.text_not_installed),
        ;

        companion object {
            fun fromOrdinal(ordinal: Int): InstallState = entries.getOrNull(ordinal) ?: ALL
        }
    }

    enum class EnabledState(val titleRes: Int) {
        ALL(R.string.text_all),
        ENABLED(R.string.text_enabled),
        DISABLED(R.string.text_disabled),
        ;

        companion object {
            fun fromOrdinal(ordinal: Int): EnabledState = entries.getOrNull(ordinal) ?: ALL
        }
    }

    enum class MechanismState(val titleRes: Int) {
        ALL(R.string.text_all),
        AIDL(R.string.text_plugin_mechanism_aidl),
        SDK(R.string.text_plugin_mechanism_sdk),
        ;

        companion object {
            fun fromOrdinal(ordinal: Int): MechanismState = entries.getOrNull(ordinal) ?: ALL
        }
    }

    enum class SourceState(val titleRes: Int) {
        ALL(R.string.text_all),
        OFFICIAL(R.string.text_plugin_official),
        THIRD_PARTY(R.string.text_plugin_third_party),
        ;

        companion object {
            fun fromOrdinal(ordinal: Int): SourceState = entries.getOrNull(ordinal) ?: ALL
        }
    }

    private fun sp(context: Context) = context.applicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

    fun defaultFilterState(): FilterState = FilterState()

    fun getFilterState(context: Context): FilterState {
        val pref = sp(context)
        return if (hasModernFilterState(pref)) {
            FilterState(
                installState = InstallState.fromOrdinal(pref.getInt(KEY_INSTALL_STATE, InstallState.ALL.ordinal)),
                enabledState = EnabledState.fromOrdinal(pref.getInt(KEY_ENABLED_STATE, EnabledState.ALL.ordinal)),
                mechanismState = MechanismState.fromOrdinal(pref.getInt(KEY_MECHANISM_STATE, MechanismState.ALL.ordinal)),
                sourceState = SourceState.fromOrdinal(pref.getInt(KEY_SOURCE_STATE, SourceState.ALL.ordinal)),
                updatableOnly = pref.getBoolean(KEY_UPDATABLE_ONLY, false),
            )
        } else {
            legacyOrdinalToFilterState(pref.getInt(KEY_LEGACY_FILTER_ORDINAL, LEGACY_FILTER_ALL))
        }
    }

    fun setFilterState(context: Context, state: FilterState) {
        val pref = sp(context)
        pref.edit {
            putInt(KEY_INSTALL_STATE, state.installState.ordinal)
            putInt(KEY_ENABLED_STATE, state.enabledState.ordinal)
            putInt(KEY_MECHANISM_STATE, state.mechanismState.ordinal)
            putInt(KEY_SOURCE_STATE, state.sourceState.ordinal)
            putBoolean(KEY_UPDATABLE_ONLY, state.updatableOnly)
            remove(KEY_LEGACY_FILTER_ORDINAL)
        }
    }

    fun clearAllFilterMemory(context: Context) {
        val pref = sp(context)
        pref.edit {
            remove(KEY_LEGACY_FILTER_ORDINAL)
            remove(KEY_INSTALL_STATE)
            remove(KEY_ENABLED_STATE)
            remove(KEY_MECHANISM_STATE)
            remove(KEY_SOURCE_STATE)
            remove(KEY_UPDATABLE_ONLY)
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

    private fun hasModernFilterState(pref: SharedPreferences): Boolean =
        pref.contains(KEY_INSTALL_STATE) ||
                pref.contains(KEY_ENABLED_STATE) ||
                pref.contains(KEY_MECHANISM_STATE) ||
                pref.contains(KEY_SOURCE_STATE) ||
                pref.contains(KEY_UPDATABLE_ONLY)

    private fun legacyOrdinalToFilterState(ordinal: Int): FilterState = when (ordinal) {
        LEGACY_FILTER_INSTALLED -> FilterState(installState = InstallState.INSTALLED)
        LEGACY_FILTER_NOT_INSTALLED -> FilterState(installState = InstallState.NOT_INSTALLED)
        LEGACY_FILTER_ENABLED -> FilterState(enabledState = EnabledState.ENABLED)
        LEGACY_FILTER_DISABLED -> FilterState(enabledState = EnabledState.DISABLED)
        LEGACY_FILTER_UPDATABLE -> FilterState(updatableOnly = true)
        LEGACY_FILTER_AIDL -> FilterState(mechanismState = MechanismState.AIDL)
        LEGACY_FILTER_SDK -> FilterState(mechanismState = MechanismState.SDK)
        else -> FilterState()
    }

    private const val LEGACY_FILTER_ALL = 0
    private const val LEGACY_FILTER_INSTALLED = 1
    private const val LEGACY_FILTER_NOT_INSTALLED = 2
    private const val LEGACY_FILTER_ENABLED = 3
    private const val LEGACY_FILTER_DISABLED = 4
    private const val LEGACY_FILTER_UPDATABLE = 5
    private const val LEGACY_FILTER_AIDL = 6
    private const val LEGACY_FILTER_SDK = 7
}
