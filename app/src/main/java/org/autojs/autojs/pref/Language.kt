package org.autojs.autojs.pref

import android.content.Context
import org.autojs.autojs.annotation.KeyRes
import org.autojs.autojs.util.LocaleUtils
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs6.R
import java.util.Locale

enum class Language(val locale: Locale, @KeyRes private val keyRes: Int, private val entryNameRes: Int) {

    ZH_HANS(Locale.forLanguageTag("zh-Hans"), R.string.key_app_language_zh_hans, R.string.entry_app_language_zh_hans),
    ZH_HANT_HK(Locale.forLanguageTag("zh-Hant-HK"), R.string.key_app_language_zh_hant_hk, R.string.entry_app_language_zh_hant_hk),
    ZH_HANT_TW(Locale.forLanguageTag("zh-Hant-TW"), R.string.key_app_language_zh_hant_tw, R.string.entry_app_language_zh_hant_tw),
    EN(Locale.forLanguageTag("en"), R.string.key_app_language_en, R.string.entry_app_language_en),
    FR(Locale.forLanguageTag("fr"), R.string.key_app_language_fr, R.string.entry_app_language_fr),
    ES(Locale.forLanguageTag("es"), R.string.key_app_language_es, R.string.entry_app_language_es),
    JA(Locale.forLanguageTag("ja"), R.string.key_app_language_ja, R.string.entry_app_language_ja),
    KO(Locale.forLanguageTag("ko"), R.string.key_app_language_ko, R.string.entry_app_language_ko),
    RU(Locale.forLanguageTag("ru"), R.string.key_app_language_ru, R.string.entry_app_language_ru),
    AR(Locale.forLanguageTag("ar"), R.string.key_app_language_ar, R.string.entry_app_language_ar),
    AUTO(LocaleUtils.getSystemLocale(), R.string.key_app_language_auto, R.string.entry_app_language_auto),
    ;

    fun getEntryName(context: Context) = context.getString(entryNameRes)

    fun getKey() = key(keyRes)

    open fun isAuto() = this == AUTO

    companion object {

        @JvmStatic
        fun getPrefLanguage() = getPrefLanguageOrNull() ?: AUTO

        @JvmStatic
        fun getPrefLanguageOrNull() = Language.values().find {
            it.getKey() == Pref.getString(R.string.key_app_language, null)
        }

        fun setPrefLanguage(locale: Locale) {
            val candidates = Language.values().filter { it != AUTO }
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