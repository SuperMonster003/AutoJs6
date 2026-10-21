package org.autojs.autojs.core.plugin.jvm

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import org.autojs.autojs.core.pref.keys.NamedStores
import org.autojs.autojs.core.pref.keys.PluginCenterKeys
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.SharedPreferencesStore
import org.autojs.plugin.jvmsource.api.JvmSourceLanguage

/**
 * The exact JVM source provider per language in the named store `jvm_source_provider_selection`
 * ([PluginCenterKeys.JVM_SOURCE_EXACT_COMPONENT_JAVA] / `_KOTLIN`, docs/settings/ROADMAP.md P3.3, names unchanged).
 * Writes commit on the SharedPreferences of the store and fail when the commit fails, as before.
 */
object JvmSourceProviderSelection {
    // Keep the original key Java-only. Kotlin must never inherit an existing Java selection.

    @JvmStatic
    fun getExact(context: Context): ComponentName? = getExact(context, JvmSourceLanguage.JAVA)

    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun getExact(context: Context, language: JvmSourceLanguage): ComponentName? {
        val value = key(language).storedOrNull()
        val parsed = JvmSourceComponent.parseExact(value) ?: return null
        return ComponentName(parsed.packageName, parsed.className)
    }

    @JvmStatic
    fun setExact(context: Context, component: ComponentName?) =
        setExact(context, JvmSourceLanguage.JAVA, component)

    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun setExact(context: Context, language: JvmSourceLanguage, component: ComponentName?) {
        // The selection must reach the disk, so it commits on the preferences of the store and checks the result.
        val preferences: SharedPreferences = (SettingsStores[NamedStores.JVM_SOURCE_PROVIDER_SELECTION] as SharedPreferencesStore).preferences
        val key = storageKey(language)
        if (component == null) {
            check(preferences.edit().remove(key).commit()) {
                "Unable to persist the cleared JVM source provider selection"
            }
            return
        }
        val selected = JvmSourceComponent(component.packageName, component.className)
        require(JvmSourceComponent.parseExact(selected.flattened) == selected) {
            "JVM source provider must be persisted as an exact fully-qualified ComponentName"
        }
        check(preferences.edit().putString(key, selected.flattened).commit()) {
            "Unable to persist the exact JVM source provider selection"
        }
    }

    internal fun storageKey(language: JvmSourceLanguage): String = key(language).name

    private fun key(language: JvmSourceLanguage) = when (language) {
        JvmSourceLanguage.JAVA -> PluginCenterKeys.JVM_SOURCE_EXACT_COMPONENT_JAVA
        JvmSourceLanguage.KOTLIN -> PluginCenterKeys.JVM_SOURCE_EXACT_COMPONENT_KOTLIN
    }

    internal fun getComponent(
        context: Context,
        language: JvmSourceLanguage = JvmSourceLanguage.JAVA,
    ): JvmSourceComponent? = getExact(context, language)?.let {
        JvmSourceComponent(it.packageName, it.className)
    }
}
