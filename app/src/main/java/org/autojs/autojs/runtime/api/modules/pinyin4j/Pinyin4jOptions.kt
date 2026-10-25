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

    /**
     * The names each normalizer accepts (as [normalized] reads them; romanization names also with `-` / space read as `_`)
     * and what each one stands for. The script module checks options against the same tables before the call (R2).
     * zh-CN: 各规范化函数接受的名称 (按 [normalized] 读取; 罗马化方案名另把 `-` / 空格读作 `_`) 及其对应值; 脚本模块调用前以同一份表检查选项 (R2).
     */
    val ROMANIZATIONS: Map<String, String> = names(
        Pinyin4jRomanizationSystems.HANYU to listOf("HANYU", "HANYU_PINYIN", "PINYIN"),
        Pinyin4jRomanizationSystems.TONGYONG to listOf("TONGYONG", "TONGYONG_PINYIN"),
        Pinyin4jRomanizationSystems.WADE_GILES to listOf("WADE", "WADE_GILES", "WADEGILES", "WADE_GILES_PINYIN"),
        Pinyin4jRomanizationSystems.MPS2 to listOf("MPS2", "MANDARIN_PHONETIC_SYMBOLS_2"),
        Pinyin4jRomanizationSystems.YALE to listOf("YALE", "YALE_PINYIN"),
        Pinyin4jRomanizationSystems.GWOYEU_ROMATZYH to listOf("GWOYEU", "GWOYEU_ROMATZYH", "GR"),
    )

    val CASE_TYPES: Map<String, String> = names(
        "LOWERCASE" to listOf("LOWERCASE", "LOW", "L", "0"),
        "UPPERCASE" to listOf("UPPERCASE", "UP", "U", "1"),
    )

    val TONE_TYPES: Map<String, String> = names(
        "WITH_TONE_NUMBER" to listOf("WITH_TONE_NUMBER", "WITH_NUMBER", "NUMBER", "NUM"),
        "WITHOUT_TONE" to listOf("WITHOUT_TONE", "NO_TONE", "NO", "FALSE", "0"),
        "WITH_TONE_MARK" to listOf("WITH_TONE_MARK", "WITH_MARK", "MARK", "TRUE", "1"),
    )

    val V_CHAR_TYPES: Map<String, String> = names(
        "WITH_U_AND_COLON" to listOf("WITH_U_AND_COLON", "U_AND_COLON", "U_COLON", "U:"),
        "WITH_V" to listOf("WITH_V", "V"),
        "WITH_U_UNICODE" to listOf("WITH_U_UNICODE", "U_UNICODE", "UNICODE", "U", "Ü"),
    )

    fun normalizeRomanization(value: String?): String =
        ROMANIZATIONS[value.normalizedSystemOr(Pinyin4jRomanizationSystems.HANYU)] ?: throw IllegalArgumentException("Invalid romanization system: $value")

    fun normalizeCaseType(value: String?): String =
        CASE_TYPES[value.normalizedOr(DEFAULT_CASE_TYPE)] ?: throw IllegalArgumentException("Invalid case type: $value")

    fun normalizeToneType(value: String?, defaultValue: String): String =
        TONE_TYPES[value.normalizedOr(defaultValue)] ?: throw IllegalArgumentException("Invalid tone type: $value")

    fun normalizeVCharType(value: String?, toneType: String): String {
        val defaultValue = if (toneType == "WITH_TONE_MARK") "WITH_U_UNICODE" else DEFAULT_V_CHAR_TYPE
        return V_CHAR_TYPES[value.normalizedOr(defaultValue)] ?: throw IllegalArgumentException("Invalid v char type: $value")
    }

    /** An option value as the normalizers read it (trimmed, upper case), null when absent or blank. */
    fun normalized(value: String?): String? = value?.trim()?.uppercase(Locale.ROOT)?.takeIf(String::isNotEmpty)

    private fun names(vararg groups: Pair<String, List<String>>): Map<String, String> =
        LinkedHashMap<String, String>().apply { groups.forEach { (canonical, names) -> names.forEach { put(it, canonical) } } }

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

    private fun String?.normalizedOr(defaultValue: String): String = normalized(this) ?: defaultValue

    private fun String?.normalizedSystemOr(defaultValue: String): String {
        return normalizedOr(defaultValue).replace('-', '_').replace(' ', '_')
    }
}
