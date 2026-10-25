package org.autojs.autojs.runtime.api.modules.pinyin4j

import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.api.modules.AugmentMembers
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.util.UtilValues
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.plugin.pinyin4j.api.Pinyin4jRomanizationSystems
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
            val system = Pinyin4jOptions.normalized(romanization)?.replace('-', '_')?.replace(' ', '_')
            if (system != null && system !in Pinyin4jOptions.ROMANIZATIONS) throw UtilValues.rangeError(FUNCTION_READINGS, "Invalid romanization system: $romanization")
            if (system == null || Pinyin4jOptions.ROMANIZATIONS[system] == Pinyin4jRomanizationSystems.HANYU) {
                formatting(FUNCTION_READINGS, options)
            } else {
                val explicit = listOf("case" to Pinyin4jOptions.CASE_TYPE_NAMES, "tone" to Pinyin4jOptions.TONE_TYPE_NAMES, "v" to Pinyin4jOptions.V_CHAR_TYPE_NAMES)
                    .filter { (_, names) -> options.hasAny(names) }.map { it.first }
                if (explicit.isNotEmpty()) throw UtilValues.rangeError(FUNCTION_READINGS, "Pinyin4j ${explicit.joinToString(", ")} options are only supported with HANYU romanization")
            }
        }
        return AugmentMembers.asCurrent(MODULE, "readings") { Pinyin4j.readings(arrayOf(s, options)) }
    }

    @ScriptFunction(name = "as")
    fun asTone(s: Any?): Any = AugmentMembers.asCurrent(MODULE, "as") { Pinyin4j.`as`(arrayOf(s)) }

    private fun of(function: String, s: Any?, options: Any?): Any {
        // The current module reads the options only for a text that is not empty.
        // zh-CN: 现有模块只在文本非空时读取选项.
        if (options is NativeObject && !isEmptyText(s)) formatting(function, options)
        return AugmentMembers.asCurrent(MODULE, if (function == MODULE) null else "of") { Pinyin4j.of(arrayOf(s, options)) }
    }

    /**
     * Checks the tone, case and v char types of [options] in the order of the parsers, against the tables of their
     * normalizers (`Pinyin4jOptions.TONE_TYPES` / `CASE_TYPES` / `V_CHAR_TYPES`) and with the same messages, without their
     * IllegalArgumentException, which would make the failure path a Java exception more expensive (R2). An absent or blank
     * value takes the default.
     *
     * zh-CN: 按解析器的顺序检查 options 的声调, 大小写与 v 字符类型, 对照其规范化函数的表 (Pinyin4jOptions.TONE_TYPES / CASE_TYPES / V_CHAR_TYPES),
     * 消息相同, 不经它们的 IllegalArgumentException (否则失败路径多一个 Java 异常, R2). 未给出或为空白的值取默认值.
     */
    private fun formatting(function: String, options: NativeObject) {
        FORMATTING.forEach { formatting ->
            val value = options.optionalString(formatting.names)
            if (Pinyin4jOptions.normalized(value)?.let { it !in formatting.accepted } == true) throw UtilValues.rangeError(function, "Invalid ${formatting.label}: $value")
        }
    }

    private class Formatting(val names: List<String>, val label: String, val accepted: Set<String>)

    private fun isEmptyText(value: Any?) = ScriptArgs.isNullish(value) || Context.toString(value).isEmpty()

    private fun NativeObject.hasAny(names: List<String>): Boolean = names.any { name -> has(name, this) }

    private fun NativeObject.optionalString(names: List<String>): String? = if (hasAny(names)) inquire(names, ::coerceString, "") else null

    private companion object {
        const val MODULE = "pinyin4j"
        const val FUNCTION_OF = "pinyin4j.of"
        const val FUNCTION_READINGS = "pinyin4j.readings"

        // The option values the parsers check, in their order of checking.
        // zh-CN: 解析器检查的选项值, 按其检查顺序.
        val FORMATTING = listOf(
            Formatting(Pinyin4jOptions.TONE_TYPE_NAMES, "tone type", Pinyin4jOptions.TONE_TYPES.keys),
            Formatting(Pinyin4jOptions.CASE_TYPE_NAMES, "case type", Pinyin4jOptions.CASE_TYPES.keys),
            Formatting(Pinyin4jOptions.V_CHAR_TYPE_NAMES, "v char type", Pinyin4jOptions.V_CHAR_TYPES.keys),
        )
    }

}
