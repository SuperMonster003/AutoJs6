package org.autojs.autojs.core.plugin.r8

import android.content.SharedPreferences
import androidx.core.content.edit
import org.autojs.autojs.core.pref.Pref

/** Android persistence boundary for explicit R8 selection; both keys are R8-only and default off. */
internal object R8CompilerOptInStore {
    fun read(): R8CompilerSelectionOptions {
        val preferences = Pref.get()
        val component = R8CompilerComponentCodec.decodeOrNull(preferences.stringOrNull(KEY_COMPONENT))
        val enabled = preferences.booleanOrFalse(KEY_ENABLED) && component != null
        return R8CompilerSelectionOptions(enabled, component)
    }

    fun disable(): R8CompilerSelectionOptions {
        Pref.get().edit { putBoolean(KEY_ENABLED, false) }
        return read()
    }

    fun select(component: R8CompilerComponent): R8CompilerSelectionOptions {
        val encoded = R8CompilerComponentCodec.encode(component)
        Pref.get().edit {
            putString(KEY_COMPONENT, encoded)
            putBoolean(KEY_ENABLED, true)
        }
        return R8CompilerSelectionOptions(enabled = true, selectedComponent = component)
    }

    private fun SharedPreferences.booleanOrFalse(key: String): Boolean =
        runCatching { getBoolean(key, false) }.getOrDefault(false)

    private fun SharedPreferences.stringOrNull(key: String): String? =
        runCatching { getString(key, null) }.getOrNull()

    private const val KEY_ENABLED = "r8_compiler_explicit_enabled"
    private const val KEY_COMPONENT = "r8_compiler_explicit_component"
}
