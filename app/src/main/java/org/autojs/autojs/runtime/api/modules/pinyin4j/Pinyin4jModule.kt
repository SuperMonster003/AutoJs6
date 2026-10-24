package org.autojs.autojs.runtime.api.modules.pinyin4j

import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.api.augment.pinyin4j.Pinyin4j
import org.autojs.autojs.runtime.api.augment.pinyin4j.Pinyin4jOptions
import org.autojs.autojs.runtime.api.modules.AugmentMembers
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.plugin.pinyin4j.api.Pinyin4jRomanizationSystems
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject

/**
 * `pinyin4j` on the new script module layer (roadmap P4.1): `pinyin4j(s, options?)` and `of` / `readings` / `as` of the
 * current `augment/pinyin4j/Pinyin4j`, through the pinyin4j plugin.
 *
 * Arguments follow the real signatures (T4): a missing or surplus argument is a TypeError of the generated layer. What
 * the current module accepts stays accepted (a nullish text is "", a string as the options of `of` is the separator,
 * other options that are not an object are ignored); what it rejects is checked here first (D10, IllegalArgumentException
 * in the current module): an unknown romanization system, case, tone or v char type name is a RangeError, and so are
 * case / tone / v options given for a romanization other than HANYU in `readings` (same messages as the current module).
 *
 * zh-CN: 新脚本模块层上的 pinyin4j (P4.1): pinyin4j(s, options?) 与现有 augment/pinyin4j/Pinyin4j 的 of / readings / as (经 pinyin4j 插件).
 * 参数按真实签名 (T4): 缺少或多余参数为生成层的 TypeError. 现有模块接受的照旧接受 (nullish 文本即 "", of 的字符串选项即分隔符, 其他不是对象的选项被忽略);
 * 它拒绝的在此先行检查 (D10, 现有模块均为 IllegalArgumentException): 未知的拼音方案, 大小写, 声调或 v 字符类型名为 RangeError, readings 中对 HANYU 以外的
 * 拼音方案给出 case / tone / v 选项同样为 RangeError (消息同现有模块).
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
            AugmentMembers.values(FUNCTION_READINGS) {
                val romanization = Pinyin4jOptions.normalizeRomanization(options.optionalString(Pinyin4jOptions.ROMANIZATION_NAMES))
                val explicit = listOf("case" to Pinyin4jOptions.CASE_TYPE_NAMES, "tone" to Pinyin4jOptions.TONE_TYPE_NAMES, "v" to Pinyin4jOptions.V_CHAR_TYPE_NAMES)
                    .filter { (_, names) -> options.hasAny(names) }.map { it.first }
                require(romanization == Pinyin4jRomanizationSystems.HANYU || explicit.isEmpty()) {
                    "Pinyin4j ${explicit.joinToString(", ")} options are only supported with HANYU romanization"
                }
                if (romanization == Pinyin4jRomanizationSystems.HANYU) formatting(options, Pinyin4jOptions.DEFAULT_READINGS_TONE_TYPE)
            }
        }
        return Pinyin4j.readings(arrayOf(s, options))
    }

    @ScriptFunction(name = "as")
    fun asTone(s: Any?): Any = Pinyin4j.`as`(arrayOf(s))

    private fun of(function: String, s: Any?, options: Any?): Any {
        // The current module reads the options only for a text that is not empty.
        // zh-CN: 现有模块只在文本非空时读取选项.
        if (options is NativeObject && !isEmptyText(s)) AugmentMembers.values(function) { formatting(options, Pinyin4jOptions.DEFAULT_OF_TONE_TYPE) }
        return Pinyin4j.of(arrayOf(s, options))
    }

    /** The case, tone and v char types of [options], normalized as the current module does (an unknown name throws). */
    private fun formatting(options: NativeObject, defaultToneType: String) {
        val toneType = Pinyin4jOptions.normalizeToneType(options.optionalString(Pinyin4jOptions.TONE_TYPE_NAMES), defaultToneType)
        Pinyin4jOptions.normalizeCaseType(options.optionalString(Pinyin4jOptions.CASE_TYPE_NAMES))
        Pinyin4jOptions.normalizeVCharType(options.optionalString(Pinyin4jOptions.V_CHAR_TYPE_NAMES), toneType)
    }

    private fun isEmptyText(value: Any?) = ScriptArgs.isNullish(value) || Context.toString(value).isEmpty()

    private fun NativeObject.hasAny(names: List<String>): Boolean = names.any { name -> has(name, this) }

    private fun NativeObject.optionalString(names: List<String>): String? = if (hasAny(names)) inquire(names, ::coerceString, "") else null

    private companion object {
        const val MODULE = "pinyin4j"
        const val FUNCTION_OF = "pinyin4j.of"
        const val FUNCTION_READINGS = "pinyin4j.readings"
    }

}
