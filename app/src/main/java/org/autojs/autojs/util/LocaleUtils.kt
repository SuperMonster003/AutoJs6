package org.autojs.autojs.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs6.R
import java.util.Locale

object LocaleUtils {

    @JvmStatic
    fun setLocale(context: Context, locale: Locale?) {
        locale ?: throw IllegalArgumentException(context.getString(R.string.error_cannot_be_null, "Locale"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList(locale)
        } else {
            setApplicationLocale(locale)
        }
    }

    fun setApplicationLocale(locale: Locale?) {
        locale?.let { AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(it)) }
    }

    fun setApplicationLocale(localeList: LocaleListCompat) {
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    @JvmStatic
    fun setFollowSystem(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.getEmptyLocaleList()
        } else {
            setLocale(context, getPrimarySystemLocale())
        }
    }

    @JvmStatic
    @Deprecated("Use getSystemLocaleList instead", ReplaceWith("getPrimarySystemLocale()"))
    fun getSystemLocale(): Locale = Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()

    @JvmStatic
    @JvmOverloads
    fun getSystemLocaleList(context: Context? = null): List<Locale> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val localeManager = (context ?: GlobalAppContext.get()).getSystemService(LocaleManager::class.java)
                val localeList: LocaleList = localeManager.systemLocales
                List(localeList.size()) { index -> localeList[index] }
            }
            else -> {
                val localeList = Resources.getSystem().configuration.locales
                List(localeList.size()) { index -> localeList[index] }
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun getPrimarySystemLocale(context: Context? = null): Locale {
        return getSystemLocaleList(context).firstOrNull() ?: Locale.getDefault()
    }

    @JvmStatic
    fun getResources(desiredLocale: Locale?): Resources {
        return GlobalAppContext.get().let { context ->
            context.resources.configuration.let { Configuration(it) }.run {
                setLocale(desiredLocale ?: getPrimarySystemLocale())
                context.createConfigurationContext(this).resources
            }
        }
    }

    fun syncPrefWithCurrentStateIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val appLocales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (appLocales.isEmpty) {
                Language.setPrefLanguageAuto()
            } else {
                Language.setPrefLanguage(appLocales[appLocales.size() - 1])
            }
        }
    }

}