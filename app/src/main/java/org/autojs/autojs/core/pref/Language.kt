package org.autojs.autojs.core.pref

import android.content.Context
import org.autojs.autojs.annotation.KeyRes
import org.autojs.autojs.util.LocaleUtils
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs6.R
import java.util.Locale

enum class Language(@JvmField val languageTag: String, @KeyRes private val keyRes: Int, private val entryNameRes: Int) {

    ZH_HANS("zh-Hans", R.string.key_app_language_zh_hans, R.string.entry_app_language_zh_hans),
    ZH_HANT_HK("zh-Hant-HK", R.string.key_app_language_zh_hant_hk, R.string.entry_app_language_zh_hant_hk),
    ZH_HANT_TW("zh-Hant-TW", R.string.key_app_language_zh_hant_tw, R.string.entry_app_language_zh_hant_tw),
    EN("en", R.string.key_app_language_en, R.string.entry_app_language_en),
    FR("fr", R.string.key_app_language_fr, R.string.entry_app_language_fr),
    ES("es", R.string.key_app_language_es, R.string.entry_app_language_es),
    JA("ja", R.string.key_app_language_ja, R.string.entry_app_language_ja),
    KO("ko", R.string.key_app_language_ko, R.string.entry_app_language_ko),
    RU("ru", R.string.key_app_language_ru, R.string.entry_app_language_ru),
    AR("ar", R.string.key_app_language_ar, R.string.entry_app_language_ar),
    AUTO("", R.string.key_app_language_auto, R.string.entry_app_language_auto),
    ;

    val locale: Locale = when (languageTag.isBlank()) {
        true -> LocaleUtils.getSystemLocale()
        else -> Locale.forLanguageTag(languageTag)
    }

    fun getLocalCompatibleLanguageTag(): String {
        if (!languageTag.isBlank()) {
            return languageTag
        }

        val availableTags = values().map { it.languageTag }
        val systemTag = LocaleUtils.getSystemLocale().toLanguageTag()

        return when {
            systemTag in availableTags -> systemTag
            systemTag.contains("-") -> {
                val primaryLanguage = systemTag.substringBefore("-")
                if (primaryLanguage in availableTags) primaryLanguage
                else EN.languageTag
            }
            else -> EN.languageTag
        }
    }
    fun getEntryName(context: Context) = context.getString(entryNameRes)

    fun getKey() = key(keyRes)

    open fun isAuto() = this == AUTO

    companion object {

        @JvmStatic
        fun getPrefLanguage() = getPrefLanguageOrNull() ?: AUTO

        @JvmStatic
        fun getPrefLanguageOrNull() = values().find {
            it.getKey() == Pref.getStringOrNull(R.string.key_app_language)
        }

        fun setPrefLanguage(locale: Locale) {
            val candidates = values().filter { it != AUTO }
            val splitLocale = locale.toLanguageTag().split("-")
            var maxLettersLen = 3
            do {
                candidates.find {
                    if (maxLettersLen > splitLocale.size) {
                        return@find false
                    }
                    val splitCandidate = it.locale.toLanguageTag().split("-")
                    if (maxLettersLen > splitCandidate.size) {
                        return@find false
                    }
                    return@find splitCandidate.subList(0, maxLettersLen).joinToString("-")
                        .startsWith(splitLocale.subList(0, maxLettersLen).joinToString("-"), true)
                }?.let {
                    Pref.putString(R.string.key_app_language, it.getKey()).also { return }
                }
            } while (--maxLettersLen > 0)

            Pref.putString(R.string.key_app_language, null)
        }

        fun setPrefLanguageAuto() {
            Pref.putString(R.string.key_app_language, AUTO.getKey())
        }

    }

}