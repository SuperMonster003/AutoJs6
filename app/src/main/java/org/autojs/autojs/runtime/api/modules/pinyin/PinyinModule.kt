package org.autojs.autojs.runtime.api.modules.pinyin

import org.autojs.autojs.rhino.extension.AnyExtensions.jsUnwrapped
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.api.augment.pinyin.Pinyin
import org.autojs.autojs.runtime.api.augment.pinyin.PinyinCustomDictionary
import org.autojs.autojs.runtime.api.augment.pinyin.PinyinOptions
import org.autojs.autojs.runtime.api.modules.AugmentMembers
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInstall
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptModuleInstallation
import org.autojs.autojs.runtime.api.modules.util.UtilValues
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.pinyin.api.PinyinCustomDictionaryContract
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Symbol
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * `pinyin` on the new script module layer (roadmap P4.1): `pinyin(hans, options?)` and the functions of the current
 * `augment/pinyin/Pinyin` (convert, simple, compare, compact, fromCodePoint, fromPhrase, through the pinyin plugin),
 * with its STYLE_* / MODE_* constants as they are.
 *
 * Arguments follow the real signatures (T4): a missing or surplus argument is a TypeError of the generated layer. What
 * the current module accepts stays accepted (a nullish text is "", options that are not an object are ignored, the
 * switches convert by truthiness); what it rejects is checked here first (D10, IllegalArgumentException in the current
 * module): an unknown `mode` / `style` name or constant is a RangeError, a `customDictionary` that is not a plain object
 * of string keys and arrays of string arrays a TypeError and one with too many entries a RangeError; `compact` needs a
 * two-dimensional array of strings (TypeError); `fromCodePoint` needs a number (TypeError) that is a finite 32-bit
 * integer after truncation (RangeError); `fromPhrase` needs a text that is not nullish (TypeError).
 *
 * zh-CN: 新脚本模块层上的 pinyin (P4.1): pinyin(hans, options?) 与现有 augment/pinyin/Pinyin 的函数 (convert, simple, compare, compact,
 * fromCodePoint, fromPhrase, 经拼音插件), STYLE_* / MODE_* 常量照原值. 参数按真实签名 (T4): 缺少或多余参数为生成层的 TypeError. 现有模块接受的照旧接受
 * (nullish 文本即 "", 不是对象的选项被忽略, 开关按真值性转换); 它拒绝的在此先行检查 (D10, 现有模块均为 IllegalArgumentException): 未知的 mode /
 * style 名称或常量为 RangeError, customDictionary 不是以字符串为键, 以字符串数组的数组为值的普通对象为 TypeError, 条目过多为 RangeError; compact
 * 需要字符串的二维数组 (TypeError); fromCodePoint 需要数值 (TypeError), 截断后为有限的 32 位整数 (RangeError); fromPhrase 需要非 nullish 的文本
 * (TypeError).
 */
@ScriptModule(name = "pinyin")
class PinyinModule {

    @ScriptInstall
    fun install(installation: ScriptModuleInstallation) = installation.onBuilt { AugmentMembers.defineProperties(Pinyin, it) }

    @ScriptInvoke
    fun invoke(hans: Any?, options: Any? = null): Any = convert(MODULE, hans, options)

    @ScriptFunction
    fun convert(hans: Any?, options: Any? = null): Any = convert(FUNCTION_CONVERT, hans, options)

    @ScriptFunction
    fun simple(str: Any?, enableNumericTone: Any? = null, enableSegment: Any? = null): Any = Pinyin.simple(arrayOf(str, enableNumericTone, enableSegment))

    @ScriptFunction
    fun compare(hanA: Any?, hanB: Any?): Any = Pinyin.compare(arrayOf(hanA, hanB))

    @ScriptFunction
    fun compact(matrix: Any?): Any {
        val rows = matrix.jsUnwrapped() as? NativeArray ?: throw matrixError(ScriptMessages.argumentType("matrix", "Array", ScriptArgs.describe(matrix)))
        for (i in 0 until rows.length) {
            val raw = ScriptableObject.getProperty(rows, i.toInt())
            val row = raw.jsUnwrapped() as? NativeArray ?: throw matrixError(ScriptMessages.argumentType("matrix[$i]", "Array", ScriptArgs.describe(raw)))
            for (j in 0 until row.length) {
                val item = ScriptableObject.getProperty(row, j.toInt()).jsUnwrapped()
                if (ScriptArgs.isNullish(item) || item === Scriptable.NOT_FOUND) throw matrixError(ScriptMessages.argumentType("matrix[$i][$j]", "string", ScriptArgs.describe(item)))
            }
        }
        return Pinyin.compact(arrayOf(matrix))
    }

    @ScriptFunction
    fun fromCodePoint(codePoint: Any?): Any? {
        val raw = RhinoUtils.unwrap(codePoint)
        val number = if (raw is Symbol || ScriptArgs.isNullish(raw)) Double.NaN else RhinoScriptRuntime.toNumber(raw)
        if (number.isNaN()) throw UtilValues.typeError(FUNCTION_FROM_CODE_POINT, ScriptMessages.argumentType("codePoint", "number", ScriptArgs.describe(codePoint)))
        val truncated = if (number > 0.0) kotlin.math.floor(number) else kotlin.math.ceil(number)
        if (!(truncated >= Int.MIN_VALUE && truncated <= Int.MAX_VALUE)) {
            throw UtilValues.rangeError(FUNCTION_FROM_CODE_POINT, ScriptMessages.argumentInteger("codePoint", 32, UtilValues.quote(codePoint)))
        }
        return Pinyin.fromCodePoint(arrayOf(codePoint))
    }

    @ScriptFunction
    fun fromPhrase(phrase: Any?): Any {
        if (ScriptArgs.isNullish(phrase)) throw UtilValues.typeError(FUNCTION_FROM_PHRASE, ScriptMessages.argumentType("phrase", "string", ScriptArgs.describe(phrase)))
        return Pinyin.fromPhrase(arrayOf(phrase))
    }

    private fun convert(function: String, hans: Any?, options: Any?): Any {
        // The current module reads the options only for a text that is not empty.
        // zh-CN: 现有模块只在文本非空时读取选项.
        if (options is NativeObject && !isEmptyText(hans)) checkOptions(function, options)
        return Pinyin.convert(arrayOf(hans, options))
    }

    private fun checkOptions(function: String, options: NativeObject) {
        // Read as the current module reads them (inquire: getProp, nullish values skipped).
        // zh-CN: 读取方式同现有模块 (inquire: getProp, 跳过 nullish 值).
        listOf("mode" to PinyinOptions::parseMode, "style" to PinyinOptions::parseStyle).forEach { (key, parse) ->
            options.inquire(key)?.let { value -> AugmentMembers.values(function) { parse(value) } }
        }
        if (!options.has(CUSTOM_DICTIONARY, options)) return
        val dictionary = options.get(CUSTOM_DICTIONARY, options)
        if (ScriptArgs.isNullish(dictionary)) return
        if (dictionary !is NativeObject) {
            throw UtilValues.typeError(function, ScriptMessages.argumentType("options.$CUSTOM_DICTIONARY", "Object", ScriptArgs.describe(dictionary)))
        }
        if (dictionary.ids.size > PinyinCustomDictionaryContract.MAX_ENTRIES) {
            val expected = "Object of at most ${PinyinCustomDictionaryContract.MAX_ENTRIES} entries"
            throw UtilValues.rangeError(function, ScriptMessages.argumentType("options.$CUSTOM_DICTIONARY", expected, "${dictionary.ids.size} entries"))
        }
        // The remaining failures concern the shape of the entries (keys, rows, candidates).
        // zh-CN: 其余失败都关乎条目的形状 (键, 行, 候选).
        try {
            PinyinCustomDictionary.encodeRhino(dictionary)
        } catch (e: IllegalArgumentException) {
            throw UtilValues.typeError(function, e.message ?: CUSTOM_DICTIONARY)
        }
    }

    private fun isEmptyText(value: Any?) = ScriptArgs.isNullish(value) || Context.toString(value).isEmpty()

    private fun matrixError(cause: String) = UtilValues.typeError(FUNCTION_COMPACT, cause)

    private companion object {
        const val MODULE = "pinyin"
        const val FUNCTION_CONVERT = "pinyin.convert"
        const val FUNCTION_COMPACT = "pinyin.compact"
        const val FUNCTION_FROM_CODE_POINT = "pinyin.fromCodePoint"
        const val FUNCTION_FROM_PHRASE = "pinyin.fromPhrase"
        const val CUSTOM_DICTIONARY = "customDictionary"
    }

}
