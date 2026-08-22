package org.autojs.autojs.core.plugin.jvm

import android.content.ComponentName
import android.content.Context
import org.autojs.plugin.jvmsource.api.JvmSourceLanguage

object JvmSourceProviderSelection {
    private const val PREFERENCES = "jvm_source_provider_selection"
    // Keep the original key Java-only. Kotlin must never inherit an existing Java selection.
    private const val KEY_EXACT_COMPONENT_JAVA = "exact_component"
    private const val KEY_EXACT_COMPONENT_KOTLIN = "exact_component_kotlin"

    @JvmStatic
    fun getExact(context: Context): ComponentName? = getExact(context, JvmSourceLanguage.JAVA)

    @JvmStatic
    fun getExact(context: Context, language: JvmSourceLanguage): ComponentName? {
        val value = context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(storageKey(language), null)
        val parsed = JvmSourceComponent.parseExact(value) ?: return null
        return ComponentName(parsed.packageName, parsed.className)
    }

    @JvmStatic
    fun setExact(context: Context, component: ComponentName?) =
        setExact(context, JvmSourceLanguage.JAVA, component)

    @JvmStatic
    fun setExact(context: Context, language: JvmSourceLanguage, component: ComponentName?) {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
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

    internal fun storageKey(language: JvmSourceLanguage): String = when (language) {
        JvmSourceLanguage.JAVA -> KEY_EXACT_COMPONENT_JAVA
        JvmSourceLanguage.KOTLIN -> KEY_EXACT_COMPONENT_KOTLIN
    }

    internal fun getComponent(
        context: Context,
        language: JvmSourceLanguage = JvmSourceLanguage.JAVA,
    ): JvmSourceComponent? = getExact(context, language)?.let {
        JvmSourceComponent(it.packageName, it.className)
    }
}
