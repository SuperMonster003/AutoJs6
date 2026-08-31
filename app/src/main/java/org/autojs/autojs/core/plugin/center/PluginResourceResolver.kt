package org.autojs.autojs.core.plugin.center

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import org.autojs.autojs.core.pref.Language
import java.util.Locale

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 25, 2026.
 */
@SuppressLint("DiscouragedApi")
object PluginResourceResolver {

    private val RESOURCE_REFERENCE = Regex("""^@(?:([A-Za-z0-9_.]+):)?(string|raw)/([A-Za-z0-9_.]+)$""")

    fun resolve(
        context: Context,
        packageName: String,
        identifier: Pair<String, ResType>,
        fallback: String? = null,
    ): String? {
        val locale = Language.getPrefLanguage().locale
        val (resName, resType) = identifier
        return when (resType) {
            ResType.STRING -> fetchLocalizedStringResource(context, packageName, locale, resName)
            ResType.RAW -> fetchLocalizedRawResource(context, packageName, locale, resName)
        } ?: fallback
    }

    fun resolveStringValue(
        context: Context,
        packageName: String,
        value: Any?,
        fallback: String? = null,
    ): String? {
        val locale = Language.getPrefLanguage().locale
        return when (value) {
            is Number -> fetchLocalizedStringResource(context, packageName, locale, value.toInt()) ?: fallback
            is CharSequence -> {
                val text = value.toString().trim().takeIf { it.isNotBlank() } ?: return fallback
                resolveResourceReference(context, packageName, locale, text) ?: text
            }
            else -> fallback
        }
    }

    private fun resolveResourceReference(context: Context, packageName: String, locale: Locale, value: String): String? {
        val reference = parseResourceReference(value) ?: return null
        val targetPackage = reference.packageName ?: packageName
        return when (reference.resType) {
            ResType.STRING -> fetchLocalizedStringResource(context, targetPackage, locale, reference.resName)
            ResType.RAW -> fetchLocalizedRawResource(context, targetPackage, locale, reference.resName)
        }
    }

    internal fun parseResourceReference(value: String): ResourceReference? {
        val match = RESOURCE_REFERENCE.matchEntire(value.trim()) ?: return null
        val packageName = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
        val resType = ResType.entries.firstOrNull { it.typeName == match.groupValues.getOrNull(2) } ?: return null
        val resName = match.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() } ?: return null
        return ResourceReference(packageName, resType, resName)
    }

    private fun fetchLocalizedStringResource(context: Context, packageName: String, locale: Locale, resName: String): String? {
        val res = runCatching { context.packageManager.getResourcesForApplication(packageName) }.getOrNull() ?: return null
        val resId = res.getIdentifier(resName, ResType.STRING.typeName, packageName).takeIf { it != 0 } ?: return null
        return fetchLocalizedStringResource(context, packageName, locale, resId)
    }

    private fun fetchLocalizedStringResource(context: Context, packageName: String, locale: Locale, resId: Int): String? {
        return runCatching {
            val pkgCtx = context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
            val config = Configuration(pkgCtx.resources.configuration).apply { setLocale(locale) }
            val localizedCtx = pkgCtx.createConfigurationContext(config)
            localizedCtx.getString(resId)
        }.getOrNull()
    }

    private fun fetchLocalizedRawResource(context: Context, packageName: String, locale: Locale, resName: String): String? {
        val res = runCatching { context.packageManager.getResourcesForApplication(packageName) }.getOrNull() ?: return null
        val resId = res.getIdentifier(resName, ResType.RAW.typeName, packageName).takeIf { it != 0 } ?: return null
        return runCatching {
            val pkgCtx = context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
            val config = Configuration(pkgCtx.resources.configuration).apply { setLocale(locale) }
            val localizedCtx = pkgCtx.createConfigurationContext(config)
            localizedCtx.resources.openRawResource(resId).bufferedReader().use {
                it.lineSequence().joinToString(System.lineSeparator())
            }
        }.getOrNull()
    }

    enum class ResType(val typeName: String) {
        STRING("string"),
        RAW("raw"),
    }

    internal data class ResourceReference(
        val packageName: String?,
        val resType: ResType,
        val resName: String,
    )
}
