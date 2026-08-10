package org.autojs.autojs.core.plugin.dex

import android.content.SharedPreferences
import androidx.core.content.edit
import org.autojs.autojs.core.pref.Pref

/** Android persistence boundary for the explicitly selected experimental compiler. */
internal object DexCompilerOptInStore {

    fun read(): DexCompilerOptInOptions {
        val preferences = Pref.get()
        val component = DexCompilerComponentCodec.decodeOrNull(
            preferences.stringOrNull(KEY_COMPONENT),
        )
        val enabled = preferences.booleanOrFalse(KEY_ENABLED) && component != null
        return DexCompilerOptInOptions(enabled = enabled, selectedComponent = component)
    }

    /** Turns the experiment off without discarding a previously valid exact component. */
    fun disable(): DexCompilerOptInOptions {
        Pref.get().edit {
            putBoolean(KEY_ENABLED, false)
        }
        return read()
    }

    /** Publishes the exact component and enabled bit in one SharedPreferences transaction. */
    fun select(component: DexCompilerComponent): DexCompilerOptInOptions {
        val encoded = DexCompilerComponentCodec.encode(component)
        Pref.get().edit {
            putString(KEY_COMPONENT, encoded)
            putBoolean(KEY_ENABLED, true)
        }
        return DexCompilerOptInOptions(enabled = true, selectedComponent = component)
    }

    private fun SharedPreferences.booleanOrFalse(key: String): Boolean =
        runCatching { getBoolean(key, false) }.getOrDefault(false)

    private fun SharedPreferences.stringOrNull(key: String): String? =
        runCatching { getString(key, null) }.getOrNull()

    private const val KEY_ENABLED = "dex_compiler_experimental_enabled"
    private const val KEY_COMPONENT = "dex_compiler_experimental_component"
}
