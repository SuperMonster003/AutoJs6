package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.res.Configuration
import org.autojs.autojs.core.pref.Language
import java.util.Locale

object PluginDescriptionResolver {

    private const val DEFAULT_DESCRIPTION_RES_NAME = "plugin_description"

    fun resolve(context: Context, packageName: String, fallback: String?): String? {
        val locale = Language.getPrefLanguage().locale
        return getStringByName(context, packageName, DEFAULT_DESCRIPTION_RES_NAME, locale) ?: fallback
    }

    private fun getStringByName(context: Context, packageName: String, resName: String, locale: Locale): String? {
        val res = runCatching { context.packageManager.getResourcesForApplication(packageName) }.getOrNull() ?: return null
        val resId = res.getIdentifier(resName, "string", packageName).takeIf { it != 0 } ?: return null
        return runCatching {
            val pkgCtx = context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
            val config = Configuration(pkgCtx.resources.configuration).apply { setLocale(locale) }
            val localizedCtx = pkgCtx.createConfigurationContext(config)
            localizedCtx.getString(resId)
        }.getOrNull()
    }
}
