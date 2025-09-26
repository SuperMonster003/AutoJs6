package net.dongliu.apk.parser.utils

import java.util.*

/**
 * @author dongliu
 *
 * Modified by SuperMonster003 as of Sep 25, 2025.
 */
object Locales {

    /**
     * when do localize, any locale will match this
     */
    @JvmField
    val any: Locale = Locale.Builder()
        .setLanguage("")
        .setRegion("")
        .build()

    /**
     * How much the given locale match the expected locale.
     */
    @JvmStatic
    fun match(locale: Locale?, targetLocale: Locale): Int = when {
        locale == null -> -1
        locale.language == targetLocale.language -> when {
            locale.country == targetLocale.country -> 3
            targetLocale.country.isEmpty() -> 2
            else -> 0
        }
        targetLocale.country.isEmpty() || targetLocale.language.isEmpty() -> 1
        else -> 0
    }

}
