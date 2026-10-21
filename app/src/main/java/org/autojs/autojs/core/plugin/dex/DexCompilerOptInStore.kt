package org.autojs.autojs.core.plugin.dex

import org.autojs.autojs.core.pref.keys.DeveloperKeys
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.set

/**
 * Android persistence boundary for the explicitly selected experimental compiler; the two keys are declared in
 * [DeveloperKeys] (docs/settings/ROADMAP.md P3.6, names unchanged).
 */
internal object DexCompilerOptInStore {

    fun read(): DexCompilerOptInOptions {
        val component = DexCompilerComponentCodec.decodeOrNull(
            runCatching { DeveloperKeys.DEX_COMPILER_EXPERIMENTAL_COMPONENT.storedOrNull() }.getOrNull(),
        )
        val enabled = runCatching { DeveloperKeys.DEX_COMPILER_EXPERIMENTAL_ENABLED.get() }.getOrDefault(false) && component != null
        return DexCompilerOptInOptions(enabled = enabled, selectedComponent = component)
    }

    /** Turns the experiment off without discarding a previously valid exact component. */
    fun disable(): DexCompilerOptInOptions {
        DeveloperKeys.DEX_COMPILER_EXPERIMENTAL_ENABLED.set(false)
        return read()
    }

    /** Publishes the exact component and enabled bit in one SharedPreferences transaction. */
    fun select(component: DexCompilerComponent): DexCompilerOptInOptions {
        val encoded = DexCompilerComponentCodec.encode(component)
        SettingsStores.default.edit {
            this[DeveloperKeys.DEX_COMPILER_EXPERIMENTAL_COMPONENT] = encoded
            this[DeveloperKeys.DEX_COMPILER_EXPERIMENTAL_ENABLED] = true
        }
        return DexCompilerOptInOptions(enabled = true, selectedComponent = component)
    }
}
