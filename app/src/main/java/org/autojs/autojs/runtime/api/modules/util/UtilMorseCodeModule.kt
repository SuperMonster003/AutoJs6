package org.autojs.autojs.runtime.api.modules.util

import org.autojs.autojs.runtime.api.Device
import org.autojs.autojs.runtime.api.StringReadable
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.util.UtilValues.isNullish
import org.autojs.autojs.runtime.api.modules.util.UtilValues.rangeError
import org.mozilla.javascript.Context
import org.mozilla.javascript.LambdaFunction
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.ScriptableObject.DONTENUM
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY
import org.mozilla.javascript.SerializableCallable
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import java.util.function.Supplier
import kotlin.math.roundToInt
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * `util.morseCode` on the new script module layer (roadmap P2.3): `morseCode(source, timeSpan?)` gives a result object
 * (`pattern`, `code`, `getPattern()`, `getCode()`, `toString()`, `vibrate(delay?)`); `getPattern` / `getCode` / `vibrate`
 * work on a source directly. The source is converted to a string, letters ignore case; the time span (a signal unit in
 * milliseconds, default and minimum 100) is rounded. A character outside the dictionary is a RangeError (the current
 * module threw a java.lang.Error).
 *
 * zh-CN: 新脚本模块层上的 util.morseCode (P2.3): morseCode(source, timeSpan?) 返回结果对象; getPattern / getCode / vibrate 直接作用于源文本. 源文本转换为字符串,
 * 字母不区分大小写; 时间单位 (毫秒, 默认及最小 100) 四舍五入. 字典以外的字符为 RangeError (现有模块抛出 java.lang.Error).
 */
@ScriptModule(name = "morseCode", parent = UtilModule::class, dollarPrefix = false)
object UtilMorseCodeModule {

    @ScriptInvoke
    fun morseCode(cx: Context, scope: VarScope, source: Any?, timeSpan: Any? = null): Scriptable = resultObject(cx, scope, MorseCode.of("util.morseCode", source, timeSpan))

    @ScriptFunction
    fun getPattern(source: Any?, timeSpan: Any? = null): List<Long> = MorseCode.of("util.morseCode.getPattern", source, timeSpan).pattern

    @ScriptFunction
    fun getCode(source: Any?, timeSpan: Any? = null): String = MorseCode.of("util.morseCode.getCode", source, timeSpan).code

    /** Vibrates the pattern of [source] (unit 100 ms) after [delay] milliseconds (negative or NaN as 0). */
    @ScriptFunction
    fun vibrate(source: Any?, delay: Any? = null) {
        MorseCode.of("util.morseCode.vibrate", source, null).vibrate(delay)
    }

    private fun resultObject(cx: Context, scope: VarScope, code: MorseCode): Scriptable = (cx.newObject(scope) as ScriptableObject).also { o ->
        fun function(name: String, maxArgs: Int, body: (Context, Array<out Any?>) -> Any?) = LambdaFunction(scope, name, 0, SerializableCallable { c, _, _, args ->
            if (args.size > maxArgs) throw UtilValues.typeError("MorseCode.$name", ScriptMessages.arity(0, maxArgs))
            body(c, args)
        }, false)
        o.defineProperty("toString", function("toString", 0) { _, _ -> code.toString() }, PERMANENT)
        o.defineProperty("getPattern", function("getPattern", 0) { c, _ -> c.newArray(scope, code.pattern.toTypedArray<Any?>()) }, PERMANENT)
        o.defineProperty("getCode", function("getCode", 0) { _, _ -> code.code }, PERMANENT)
        o.defineProperty("vibrate", function("vibrate", 1) { _, args -> code.vibrate(args.getOrNull(0)); Undefined.instance }, PERMANENT)
        o.defineProperty(StringReadable.KEY, function(StringReadable.KEY, 0) { _, _ -> code.toString() }, READONLY or DONTENUM or PERMANENT)
        o.defineProperty("pattern", Supplier<Any?> { Context.getCurrentContext().newArray(scope, code.pattern.toTypedArray<Any?>()) }, null, PERMANENT)
        o.defineProperty("code", Supplier<Any?> { code.code }, null, PERMANENT)
    }

    /** The Morse code of a source: a dot is 1 unit, a dash 3, the gap within a character 1, between characters 3, between words 7. */
    private class MorseCode(val code: String, val pattern: List<Long>) {

        fun vibrate(delay: Any?) {
            if (pattern.isEmpty()) return
            val d = if (isNullish(delay)) 0.0 else RhinoScriptRuntime.toNumber(delay)
            val start = if (d.isNaN()) 0L else maxOf(0L, d.toLong())
            Device.doVibrate((listOf(start) + pattern).toLongArray())
        }

        override fun toString() = listOf("MorseCode {", "  code: '$code',", "  pattern: [${pattern.joinToString(", ")}],", "}").joinToString("\n")

        companion object {

            private const val DEFAULT_TIME_SPAN = 100
            private const val DIT = 1
            private const val DAH = 3
            private const val INTRA_CHARACTER_GAP = 1
            private const val SHORT_GAP = 3
            private const val MEDIUM_GAP = 7

            /** Dots (U+00B7) and dashes. */
            private val CHARS: Map<Char, String> = mapOf(
                'A' to "\u00B7-", 'B' to "-\u00B7\u00B7\u00B7", 'C' to "-\u00B7-\u00B7", 'D' to "-\u00B7\u00B7", 'E' to "\u00B7", 'F' to "\u00B7\u00B7-\u00B7",
                'G' to "--\u00B7", 'H' to "\u00B7\u00B7\u00B7\u00B7", 'I' to "\u00B7\u00B7", 'J' to "\u00B7---", 'K' to "-\u00B7-", 'L' to "\u00B7-\u00B7\u00B7",
                'M' to "--", 'N' to "-\u00B7", 'O' to "---", 'P' to "\u00B7--\u00B7", 'Q' to "--\u00B7-", 'R' to "\u00B7-\u00B7", 'S' to "\u00B7\u00B7\u00B7",
                'T' to "-", 'U' to "\u00B7\u00B7-", 'V' to "\u00B7\u00B7\u00B7-", 'W' to "\u00B7--", 'X' to "-\u00B7\u00B7-", 'Y' to "-\u00B7--", 'Z' to "--\u00B7\u00B7",
                '1' to "\u00B7----", '2' to "\u00B7\u00B7---", '3' to "\u00B7\u00B7\u00B7--", '4' to "\u00B7\u00B7\u00B7\u00B7-", '5' to "\u00B7\u00B7\u00B7\u00B7\u00B7",
                '6' to "-\u00B7\u00B7\u00B7\u00B7", '7' to "--\u00B7\u00B7\u00B7", '8' to "---\u00B7\u00B7", '9' to "----\u00B7", '0' to "-----",
                '.' to "\u00B7-\u00B7-\u00B7-", ':' to "---\u00B7\u00B7\u00B7", ',' to "--\u00B7\u00B7--", ';' to "-\u00B7-\u00B7-\u00B7", '?' to "\u00B7\u00B7--\u00B7\u00B7",
                '=' to "-\u00B7\u00B7\u00B7-", '\'' to "\u00B7----\u00B7", '/' to "-\u00B7\u00B7-\u00B7", '!' to "-\u00B7-\u00B7--", '-' to "-\u00B7\u00B7\u00B7\u00B7-",
                '_' to "\u00B7\u00B7--\u00B7-", '"' to "\u00B7-\u00B7\u00B7-\u00B7", '(' to "-\u00B7--\u00B7", ')' to "-\u00B7--\u00B7-",
                '$' to "\u00B7\u00B7\u00B7-\u00B7\u00B7-", '&' to "\u00B7-\u00B7\u00B7\u00B7", '@' to "\u00B7--\u00B7-\u00B7", '+' to "\u00B7-\u00B7-\u00B7",
            )

            private val WORDS = Regex("\\s+")

            fun of(function: String, source: Any?, timeSpan: Any?): MorseCode {
                val text = if (source == null) "" else RhinoScriptRuntime.toString(source)
                val span = when {
                    isNullish(timeSpan) -> DEFAULT_TIME_SPAN
                    else -> RhinoScriptRuntime.toNumber(timeSpan).let { x ->
                        if (x.isNaN()) throw rangeError(function, ScriptMessages.argumentType("timeSpan", "number", UtilValues.quote(timeSpan)))
                        if (x.isInfinite()) Int.MAX_VALUE else x.roundToInt()
                    }
                }.coerceAtLeast(DEFAULT_TIME_SPAN).toLong()
                val code = StringBuilder()
                val pattern = ArrayList<Long>()
                val words = text.split(WORDS)
                words.forEachIndexed { i, word ->
                    word.forEachIndexed { j, letter ->
                        val upper = letter.uppercaseChar()
                        val marks = CHARS[upper] ?: throw rangeError(function, ScriptMessages.argumentType("source", "text of A-Z, 0-9 and . : , ; ? = ' / ! - _ \" ( ) \$ & @ +", UtilValues.quote(upper.toString())))
                        marks.forEachIndexed { k, mark ->
                            pattern += (if (mark == '-') DAH else DIT) * span
                            if (k != marks.lastIndex) pattern += INTRA_CHARACTER_GAP * span
                        }
                        code.append(marks)
                        if (j != word.lastIndex) {
                            pattern += SHORT_GAP * span
                            code.append(" ".repeat(SHORT_GAP))
                        }
                    }
                    if (i != words.lastIndex) {
                        pattern += MEDIUM_GAP * span
                        code.append(" ".repeat(MEDIUM_GAP))
                    }
                }
                return MorseCode(code.toString(), pattern)
            }

        }

    }

}
