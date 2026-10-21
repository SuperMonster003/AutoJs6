package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.SharedPreferences
import org.autojs.autojs.core.pref.keys.NamedStores
import org.autojs.autojs.core.pref.keys.PluginCenterKeys.FILTER_ENABLED_STATE
import org.autojs.autojs.core.pref.keys.PluginCenterKeys.FILTER_INSTALL_STATE
import org.autojs.autojs.core.pref.keys.PluginCenterKeys.FILTER_LEGACY_ORDINAL
import org.autojs.autojs.core.pref.keys.PluginCenterKeys.FILTER_MECHANISM_STATE
import org.autojs.autojs.core.pref.keys.PluginCenterKeys.FILTER_SOURCE_STATE
import org.autojs.autojs.core.pref.keys.PluginCenterKeys.FILTER_UPDATABLE_ONLY
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.SharedPreferencesStore
import org.autojs.autojs.core.pref.store.set
import org.autojs.autojs6.R

/**
 * Filters of the plugin list in the named store `plugin_center_filter_state`, declared in [org.autojs.autojs.core.pref.keys.PluginCenterKeys]
 * (docs/settings/ROADMAP.md P3.1, names unchanged). The [Context] parameters are kept for the callers; the store is
 * opened by the settings layer with the application context, as before.
 */
object PluginFilterStore {

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

    private val store
        get() = SettingsStores[NamedStores.PLUGIN_CENTER_FILTER_STATE]

    /** The SharedPreferences of the store, for change listeners. */
    private fun preferences(): SharedPreferences = (store as SharedPreferencesStore).preferences

    fun defaultFilterState(): FilterState = FilterState()

    @Suppress("UNUSED_PARAMETER")
    fun getFilterState(context: Context): FilterState {
        return if (hasModernFilterState()) {
            FilterState(
                installState = InstallState.fromOrdinal(FILTER_INSTALL_STATE.get()),
                enabledState = EnabledState.fromOrdinal(FILTER_ENABLED_STATE.get()),
                mechanismState = MechanismState.fromOrdinal(FILTER_MECHANISM_STATE.get()),
                sourceState = SourceState.fromOrdinal(FILTER_SOURCE_STATE.get()),
                updatableOnly = FILTER_UPDATABLE_ONLY.get(),
            )
        } else {
            legacyOrdinalToFilterState(FILTER_LEGACY_ORDINAL.get())
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun setFilterState(context: Context, state: FilterState) {
        store.edit {
            this[FILTER_INSTALL_STATE] = state.installState.ordinal
            this[FILTER_ENABLED_STATE] = state.enabledState.ordinal
            this[FILTER_MECHANISM_STATE] = state.mechanismState.ordinal
            this[FILTER_SOURCE_STATE] = state.sourceState.ordinal
            this[FILTER_UPDATABLE_ONLY] = state.updatableOnly
            remove(FILTER_LEGACY_ORDINAL.name)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun clearAllFilterMemory(context: Context) {
        store.edit {
            listOf(FILTER_LEGACY_ORDINAL, FILTER_INSTALL_STATE, FILTER_ENABLED_STATE, FILTER_MECHANISM_STATE, FILTER_SOURCE_STATE, FILTER_UPDATABLE_ONLY)
                .forEach { remove(it.name) }
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

    private fun hasModernFilterState(): Boolean =
        listOf(FILTER_INSTALL_STATE, FILTER_ENABLED_STATE, FILTER_MECHANISM_STATE, FILTER_SOURCE_STATE, FILTER_UPDATABLE_ONLY).any { it.isStored() }

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
