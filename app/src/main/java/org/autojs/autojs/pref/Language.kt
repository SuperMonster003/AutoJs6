package org.autojs.autojs.pref

import android.content.Context
import org.autojs.autojs.annotation.KeyRes
import org.autojs.autojs.util.LocaleUtils
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs6.R
import java.util.Locale

enum class Language(val locale: Locale, @KeyRes private val keyRes: Int, private val entryNameRes: Int) {

    AUTO(LocaleUtils.getSystemLocale(), R.string.key_app_language_auto, R.string.entry_app_language_auto),
    ZH(Locale.forLanguageTag("zh"), R.string.key_app_language_zh, R.string.entry_app_language_zh),
    ZH_HK(Locale.forLanguageTag("zh-hk"), R.string.key_app_language_zh_hk, R.string.entry_app_language_zh_hk),
    ZH_TW(Locale.forLanguageTag("zh-tw"), R.string.key_app_language_zh_tw, R.string.entry_app_language_zh_tw),
    EN(Locale.forLanguageTag("en"), R.string.key_app_language_en, R.string.entry_app_language_en),
    FR(Locale.forLanguageTag("fr"), R.string.key_app_language_fr, R.string.entry_app_language_fr),
    ES(Locale.forLanguageTag("es"), R.string.key_app_language_es, R.string.entry_app_language_es),
    JA(Locale.forLanguageTag("ja"), R.string.key_app_language_ja, R.string.entry_app_language_ja),
    KO(Locale.forLanguageTag("ko"), R.string.key_app_language_ko, R.string.entry_app_language_ko),
    RU(Locale.forLanguageTag("ru"), R.string.key_app_language_ru, R.string.entry_app_language_ru),
    AR(Locale.forLanguageTag("ar"), R.string.key_app_language_ar, R.string.entry_app_language_ar),
    ;

    fun getEntryName(context: Context) = context.getString(entryNameRes)

    fun getKey() = key(keyRes)

    open fun isAuto() = this == AUTO

    companion object {

        @JvmStatic
        fun getPrefLanguage() = Language.values().find {
            it.getKey() == Pref.getString(R.string.key_app_language, null)
        } ?: AUTO

    }

}