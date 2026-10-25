package org.autojs.autojs.runtime.api.modules.pinyin4j

import android.os.Bundle
import org.autojs.plugin.pinyin4j.api.Pinyin4jOptionKeys
import org.autojs.plugin.pinyin4j.api.Pinyin4jRomanizationSystems
import java.util.Locale

internal object Pinyin4jOptions {

    const val DEFAULT_SEPARATOR = ""
    const val DEFAULT_CASE_TYPE = "LOWERCASE"
    const val DEFAULT_OF_TONE_TYPE = "WITHOUT_TONE"
    const val DEFAULT_READINGS_TONE_TYPE = "WITH_TONE_NUMBER"
    const val DEFAULT_V_CHAR_TYPE = "WITH_V"

    val ROMANIZATION_NAMES = listOf("romanization", "system")
    val CASE_TYPE_NAMES = listOf("case", "caseType")
    val TONE_TYPE_NAMES = listOf("tone", "toneType")
    val V_CHAR_TYPE_NAMES = listOf("v", "vChar", "vCharType")

    fun ofBundle(
        separator: String = DEFAULT_SEPARATOR,
        caseType: String? = null,
        toneType: String? = null,
        vCharType: String? = null,
    ): Bundle {
        val normalizedToneType = normalizeToneType(toneType, DEFAULT_OF_TONE_TYPE)
        return Bundle().apply {
            putString(Pinyin4jOptionKeys.SEPARATOR, separator)
            putString(Pinyin4jOptionKeys.CASE_TYPE, normalizeCaseType(caseType))
            putString(Pinyin4jOptionKeys.TONE_TYPE, normalizedToneType)
            putString(Pinyin4jOptionKeys.V_CHAR_TYPE, normalizeVCharType(vCharType, normalizedToneType))
        }
    }

    fun readingsBundle(
        romanization: String? = null,
        caseType: String? = null,
        toneType: String? = null,
        vCharType: String? = null,
        hasExplicitCaseType: Boolean = false,
        hasExplicitToneType: Boolean = false,
        hasExplicitVCharType: Boolean = false,
    ): Bundle {
        val normalizedRomanization = normalizeRomanization(romanization)
        requireHanyuFormattingCompatibility(
            normalizedRomanization,
            hasExplicitCaseType,
            hasExplicitToneType,
            hasExplicitVCharType,
        )
        return Bundle().apply {
            putString(Pinyin4jOptionKeys.ROMANIZATION, normalizedRomanization)
            if (normalizedRomanization == Pinyin4jRomanizationSystems.HANYU) {
                val normalizedToneType = normalizeToneType(toneType, DEFAULT_READINGS_TONE_TYPE)
                putString(Pinyin4jOptionKeys.CASE_TYPE, normalizeCaseType(caseType))
                putString(Pinyin4jOptionKeys.TONE_TYPE, normalizedToneType)
                putString(Pinyin4jOptionKeys.V_CHAR_TYPE, normalizeVCharType(vCharType, normalizedToneType))
            }
        }
    }

    fun normalizeRomanization(value: String?): String {
        return when (value.normalizedSystemOr(Pinyin4jRomanizationSystems.HANYU)) {
            "HANYU", "HANYU_PINYIN", "PINYIN" -> Pinyin4jRomanizationSystems.HANYU
            "TONGYONG", "TONGYONG_PINYIN" -> Pinyin4jRomanizationSystems.TONGYONG
            "WADE", "WADE_GILES", "WADEGILES", "WADE_GILES_PINYIN" -> Pinyin4jRomanizationSystems.WADE_GILES
            "MPS2", "MANDARIN_PHONETIC_SYMBOLS_2" -> Pinyin4jRomanizationSystems.MPS2
            "YALE", "YALE_PINYIN" -> Pinyin4jRomanizationSystems.YALE
            "GWOYEU", "GWOYEU_ROMATZYH", "GR" -> Pinyin4jRomanizationSystems.GWOYEU_ROMATZYH
            else -> throw IllegalArgumentException("Invalid romanization system: $value")
        }
    }

    fun normalizeCaseType(value: String?): String {
        return when (value.normalizedOr(DEFAULT_CASE_TYPE)) {
            "LOWERCASE", "LOW", "L", "0" -> "LOWERCASE"
            "UPPERCASE", "UP", "U", "1" -> "UPPERCASE"
            else -> throw IllegalArgumentException("Invalid case type: $value")
        }
    }

    fun normalizeToneType(value: String?, defaultValue: String): String {
        return when (value.normalizedOr(defaultValue)) {
            "WITH_TONE_NUMBER", "WITH_NUMBER", "NUMBER", "NUM" -> "WITH_TONE_NUMBER"
            "WITHOUT_TONE", "NO_TONE", "NO", "FALSE", "0" -> "WITHOUT_TONE"
            "WITH_TONE_MARK", "WITH_MARK", "MARK", "TRUE", "1" -> "WITH_TONE_MARK"
            else -> throw IllegalArgumentException("Invalid tone type: $value")
        }
    }

    fun normalizeVCharType(value: String?, toneType: String): String {
        val defaultValue = if (toneType == "WITH_TONE_MARK") "WITH_U_UNICODE" else DEFAULT_V_CHAR_TYPE
        return when (value.normalizedOr(defaultValue)) {
            "WITH_U_AND_COLON", "U_AND_COLON", "U_COLON", "U:" -> "WITH_U_AND_COLON"
            "WITH_V", "V" -> "WITH_V"
            "WITH_U_UNICODE", "U_UNICODE", "UNICODE", "U", "Ü" -> "WITH_U_UNICODE"
            else -> throw IllegalArgumentException("Invalid v char type: $value")
        }
    }

    private fun requireHanyuFormattingCompatibility(
        romanization: String,
        hasExplicitCaseType: Boolean,
        hasExplicitToneType: Boolean,
        hasExplicitVCharType: Boolean,
    ) {
        if (romanization == Pinyin4jRomanizationSystems.HANYU) return
        val incompatible = buildList {
            if (hasExplicitCaseType) add("case")
            if (hasExplicitToneType) add("tone")
            if (hasExplicitVCharType) add("v")
        }
        require(incompatible.isEmpty()) {
            "Pinyin4j ${incompatible.joinToString(", ")} options are only supported with HANYU romanization"
        }
    }

    private fun String?.normalizedOr(defaultValue: String): String {
        return this?.trim()?.uppercase(Locale.ROOT)?.takeIf(String::isNotEmpty) ?: defaultValue
    }

    private fun String?.normalizedSystemOr(defaultValue: String): String {
        return normalizedOr(defaultValue).replace('-', '_').replace(' ', '_')
    }
}
