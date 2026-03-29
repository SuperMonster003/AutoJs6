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

    private fun fetchLocalizedStringResource(context: Context, packageName: String, locale: Locale, resName: String): String? {
        val res = runCatching { context.packageManager.getResourcesForApplication(packageName) }.getOrNull() ?: return null
        val resId = res.getIdentifier(resName, ResType.STRING.typeName, packageName).takeIf { it != 0 } ?: return null
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
}
