package net.dongliu.apk.parser.utils

import java.util.Locale

/**
 * @author dongliu
 */
object Locales {
    /**
     * when do localize, any locale will match this
     */
    @JvmField
    val any = Locale("", "")

    /**
     * How much the given locale match the expected locale.
     */
    @JvmStatic
    fun match(locale: Locale?, targetLocale: Locale): Int {
        if (locale == null) {
            return -1
        }
        return when {
            locale.language == targetLocale.language -> {
                when {
                    locale.country == targetLocale.country -> {
                        3
                    }

                    targetLocale.country.isEmpty() -> {
                        2
                    }

                    else -> {
                        0
                    }
                }
            }

            targetLocale.country.isEmpty() || targetLocale.language.isEmpty() -> {
                1
            }

            else -> {
                0
            }
        }
    }
}
