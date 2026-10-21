package org.autojs.autojs.core.plugin.r8

import org.autojs.autojs.core.pref.keys.DeveloperKeys
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.set

/**
 * Android persistence boundary for explicit R8 selection; both keys are R8-only, default off and declared in
 * [DeveloperKeys] (docs/settings/ROADMAP.md P3.6, names unchanged).
 */
internal object R8CompilerOptInStore {
    fun read(): R8CompilerSelectionOptions {
        val component = R8CompilerComponentCodec.decodeOrNull(
            runCatching { DeveloperKeys.R8_COMPILER_EXPLICIT_COMPONENT.storedOrNull() }.getOrNull(),
        )
        val enabled = runCatching { DeveloperKeys.R8_COMPILER_EXPLICIT_ENABLED.get() }.getOrDefault(false) && component != null
        return R8CompilerSelectionOptions(enabled, component)
    }

    fun disable(): R8CompilerSelectionOptions {
        DeveloperKeys.R8_COMPILER_EXPLICIT_ENABLED.set(false)
        return read()
    }

    fun select(component: R8CompilerComponent): R8CompilerSelectionOptions {
        val encoded = R8CompilerComponentCodec.encode(component)
        SettingsStores.default.edit {
            this[DeveloperKeys.R8_COMPILER_EXPLICIT_COMPONENT] = encoded
            this[DeveloperKeys.R8_COMPILER_EXPLICIT_ENABLED] = true
        }
        return R8CompilerSelectionOptions(enabled = true, selectedComponent = component)
    }
}
