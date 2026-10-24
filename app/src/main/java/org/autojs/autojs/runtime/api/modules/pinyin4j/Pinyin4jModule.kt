package org.autojs.autojs.runtime.api.modules.pinyin4j

import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.api.augment.pinyin4j.Pinyin4j
import org.autojs.autojs.runtime.api.augment.pinyin4j.Pinyin4jOptions
import org.autojs.autojs.runtime.api.modules.AugmentMembers
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.util.UtilValues
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import java.util.Locale

/**
 * `pinyin4j` on the new script module layer (roadmap P4.1): `pinyin4j(s, options?)` and `of` / `readings` / `as` of the
 * current `augment/pinyin4j/Pinyin4j`, through the pinyin4j plugin.
 *
 * Arguments follow the real signatures (T4): a missing or surplus argument is a TypeError of the generated layer. What
 * the current module accepts stays accepted (a nullish text is "", a string as the options of `of` is the separator,
 * other options that are not an object are ignored); what it rejects is checked here first (D10, IllegalArgumentException
 * in the current module): an unknown romanization system, case, tone or v char type name is a RangeError, and so are
 * case / tone / v options given for a romanization other than HANYU in `readings` (same messages as the current module).
 * Once the arguments are accepted, a failure of the current module (the plugin not installed or disabled among them)
 * is rethrown as the current augment layer does (`AugmentMembers.asCurrent`): the same InternalError in both states.
 *
 * zh-CN: 新脚本模块层上的 pinyin4j (P4.1): pinyin4j(s, options?) 与现有 augment/pinyin4j/Pinyin4j 的 of / readings / as (经 pinyin4j 插件).
 * 参数按真实签名 (T4): 缺少或多余参数为生成层的 TypeError. 现有模块接受的照旧接受 (nullish 文本即 "", of 的字符串选项即分隔符, 其他不是对象的选项被忽略);
 * 它拒绝的在此先行检查 (D10, 现有模块均为 IllegalArgumentException): 未知的拼音方案, 大小写, 声调或 v 字符类型名为 RangeError, readings 中对 HANYU 以外的
 * 拼音方案给出 case / tone / v 选项同样为 RangeError (消息同现有模块).
 * 参数通过检查后, 现有模块的失败 (含插件未安装或未启用) 按现有 augment 层的方式重新抛出 (AugmentMembers.asCurrent), 两态为同一个 InternalError.
 */
@ScriptModule(name = "pinyin4j")
class Pinyin4jModule {

    @ScriptInvoke
    fun invoke(s: Any?, options: Any? = null): Any = of(MODULE, s, options)

    @ScriptFunction
    fun of(s: Any?, options: Any? = null): Any = of(FUNCTION_OF, s, options)

    @ScriptFunction
    fun readings(s: Any?, options: Any? = null): Any {
        if (options is NativeObject && !isEmptyText(s)) {
            val romanization = options.optionalString(Pinyin4jOptions.ROMANIZATION_NAMES)
            val system = normalized(romanization)?.replace('-', '_')?.replace(' ', '_')
            if (system != null && system !in HANYU && system !in OTHER_ROMANIZATIONS) throw UtilValues.rangeError(FUNCTION_READINGS, "Invalid romanization system: $romanization")
            if (system == null || system in HANYU) {
                formatting(FUNCTION_READINGS, options)
            } else {
                val explicit = listOf("case" to Pinyin4jOptions.CASE_TYPE_NAMES, "tone" to Pinyin4jOptions.TONE_TYPE_NAMES, "v" to Pinyin4jOptions.V_CHAR_TYPE_NAMES)
                    .filter { (_, names) -> options.hasAny(names) }.map { it.first }
                if (explicit.isNotEmpty()) throw UtilValues.rangeError(FUNCTION_READINGS, "Pinyin4j ${explicit.joinToString(", ")} options are only supported with HANYU romanization")
            }
        }
        return AugmentMembers.asCurrent(Pinyin4j, "readings") { Pinyin4j.readings(arrayOf(s, options)) }
    }

    @ScriptFunction(name = "as")
    fun asTone(s: Any?): Any = AugmentMembers.asCurrent(Pinyin4j, "as") { Pinyin4j.`as`(arrayOf(s)) }

    private fun of(function: String, s: Any?, options: Any?): Any {
        // The current module reads the options only for a text that is not empty.
        // zh-CN: 现有模块只在文本非空时读取选项.
        if (options is NativeObject && !isEmptyText(s)) formatting(function, options)
        return AugmentMembers.asCurrent(Pinyin4j, if (function == MODULE) null else "of") { Pinyin4j.of(arrayOf(s, options)) }
    }

    /**
     * Checks the tone, case and v char types of [options] in the order of the current module, with the names its
     * normalizers (`Pinyin4jOptions.normalize*`) accept and the same messages, without their IllegalArgumentException,
     * which would make the failure path a Java exception more expensive (R2). An absent or blank value takes the default.
     *
     * zh-CN: 按现有模块的顺序检查 options 的声调, 大小写与 v 字符类型, 接受的名称与消息同它的规范化函数 (Pinyin4jOptions.normalize*), 不经它们的
     * IllegalArgumentException (否则失败路径多一个 Java 异常, R2). 未给出或为空白的值取默认值.
     */
    private fun formatting(function: String, options: NativeObject) {
        FORMATTING.forEach { formatting ->
            val value = options.optionalString(formatting.names)
            if (normalized(value)?.let { it !in formatting.accepted } == true) throw UtilValues.rangeError(function, "Invalid ${formatting.label}: $value")
        }
    }

    /** A value as the normalizers of the current module read it (trimmed, upper case), null when absent or blank. */
    private fun normalized(value: String?) = value?.trim()?.uppercase(Locale.ROOT)?.takeIf(String::isNotEmpty)

    private class Formatting(val names: List<String>, val label: String, val accepted: Set<String>)

    private fun isEmptyText(value: Any?) = ScriptArgs.isNullish(value) || Context.toString(value).isEmpty()

    private fun NativeObject.hasAny(names: List<String>): Boolean = names.any { name -> has(name, this) }

    private fun NativeObject.optionalString(names: List<String>): String? = if (hasAny(names)) inquire(names, ::coerceString, "") else null

    private companion object {
        const val MODULE = "pinyin4j"
        const val FUNCTION_OF = "pinyin4j.of"
        const val FUNCTION_READINGS = "pinyin4j.readings"

        // The names the normalizers of the current module accept (Pinyin4jOptions.normalizeRomanization / normalizeToneType /
        // normalizeCaseType / normalizeVCharType), in their order of checking.
        // zh-CN: 现有模块的规范化函数接受的名称 (Pinyin4jOptions.normalizeRomanization / normalizeToneType / normalizeCaseType /
        // normalizeVCharType), 按其检查顺序.
        val HANYU = setOf("HANYU", "HANYU_PINYIN", "PINYIN")
        val OTHER_ROMANIZATIONS = setOf(
            "TONGYONG", "TONGYONG_PINYIN", "WADE", "WADE_GILES", "WADEGILES", "WADE_GILES_PINYIN", "MPS2", "MANDARIN_PHONETIC_SYMBOLS_2",
            "YALE", "YALE_PINYIN", "GWOYEU", "GWOYEU_ROMATZYH", "GR",
        )
        val FORMATTING = listOf(
            Formatting(Pinyin4jOptions.TONE_TYPE_NAMES, "tone type", setOf(
                "WITH_TONE_NUMBER", "WITH_NUMBER", "NUMBER", "NUM", "WITHOUT_TONE", "NO_TONE", "NO", "FALSE", "0", "WITH_TONE_MARK", "WITH_MARK", "MARK", "TRUE", "1",
            )),
            Formatting(Pinyin4jOptions.CASE_TYPE_NAMES, "case type", setOf("LOWERCASE", "LOW", "L", "0", "UPPERCASE", "UP", "U", "1")),
            Formatting(Pinyin4jOptions.V_CHAR_TYPE_NAMES, "v char type", setOf(
                "WITH_U_AND_COLON", "U_AND_COLON", "U_COLON", "U:", "WITH_V", "V", "WITH_U_UNICODE", "U_UNICODE", "UNICODE", "U", "Ü",
            )),
        )
    }

}
