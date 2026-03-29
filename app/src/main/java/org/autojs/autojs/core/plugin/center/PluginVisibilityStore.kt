package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 26, 2026.
 */
object PluginVisibilityStore {

    private const val SP_NAME = "plugin_center_visibility_state"
    private const val KEY_HIDDEN_PACKAGES = "key_\$_plugin_center_hidden_packages"
    private const val KEY_HIDDEN_TITLE_PREFIX = "key_\$_plugin_center_hidden_title_"
    private const val KEY_HIDDEN_SOURCE_PREFIX = "key_\$_plugin_center_hidden_source_"

    private fun sp(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

    fun isHidden(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return hiddenPackages(context).contains(packageName)
    }

    fun hide(
        context: Context,
        packageName: String,
        title: String?,
        source: PluginIndexSource?,
    ) {
        setHidden(context, packageName, hidden = true, title = title, source = source)
    }

    fun show(context: Context, packageName: String) {
        setHidden(context, packageName, hidden = false)
    }

    fun setHidden(
        context: Context,
        packageName: String,
        hidden: Boolean,
        title: String? = null,
        source: PluginIndexSource? = null,
    ) {
        val normalized = packageName.trim()
        if (normalized.isBlank()) return

        val nextPackages = hiddenPackages(context).toMutableSet()
        val pref = sp(context)
        pref.edit {
            if (hidden) {
                nextPackages += normalized
                putString(KEY_HIDDEN_TITLE_PREFIX + normalized, title?.trim()?.takeIf { it.isNotEmpty() })
                putString(KEY_HIDDEN_SOURCE_PREFIX + normalized, source?.name)
            } else {
                nextPackages -= normalized
                remove(KEY_HIDDEN_TITLE_PREFIX + normalized)
                remove(KEY_HIDDEN_SOURCE_PREFIX + normalized)
            }
            putStringSet(KEY_HIDDEN_PACKAGES, nextPackages)
        }
    }

    fun listHidden(context: Context): List<HiddenPlugin> {
        val pref = sp(context)
        return hiddenPackages(context)
            .sortedBy { it.lowercase() }
            .map { pkg ->
                val source = pref.getString(KEY_HIDDEN_SOURCE_PREFIX + pkg, null)
                    ?.runCatching { PluginIndexSource.valueOf(this) }
                    ?.getOrNull()
                val title = pref.getString(KEY_HIDDEN_TITLE_PREFIX + pkg, null)
                    ?.takeIf { it.isNotBlank() }
                HiddenPlugin(pkg, title, source)
            }
    }

    fun hiddenCount(context: Context): Int = hiddenPackages(context).size

    fun registerOnSharedPreferenceChangeListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        sp(context).registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnSharedPreferenceChangeListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        sp(context).unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun hiddenPackages(context: Context): Set<String> =
        sp(context).getStringSet(KEY_HIDDEN_PACKAGES, emptySet()).orEmpty()

    data class HiddenPlugin(
        val packageName: String,
        val title: String?,
        val source: PluginIndexSource?,
    )
}

