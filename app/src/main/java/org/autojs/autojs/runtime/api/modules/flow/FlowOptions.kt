package org.autojs.autojs.runtime.api.modules.flow

import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.pick.PickArguments
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Wrapper
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The checks of the fields of an options object of `flow` and the automation toolkit on the new script module layer
 * (roadmap P3.3, appendix C Q65 (2)), made before the current parsers (`augment/flow/FlowArguments`,
 * `augment/toolkit/ToolkitArguments`) read the same object again. The values they accept are accepted here as well:
 * numbers are read by JS ToNumber (numeric strings and booleans count, as before), switches by truthiness (any value). What
 * they refused is a TypeError (another type, an unknown key, two fields that cannot go together) or a RangeError (a number
 * that is NaN, negative, infinite where a limit is needed or not an integer, a name outside the documented ones), D10. A
 * missing or nullish field is the default, as before; a number given in place of the options object stands for the
 * tool's [shorthandKey] field.
 *
 * zh-CN: 新脚本模块层上 flow 与自动化工具集选项对象的字段检查 (P3.3, 附录 C Q65 (2)), 在现有解析器 (augment/flow/FlowArguments,
 * augment/toolkit/ToolkitArguments) 再次读取同一对象之前进行. 它们接受的值这里同样接受: 数值按 JS ToNumber 读取 (数值字符串与布尔值照旧可用), 开关按真值性
 * (任何值). 它们拒绝的值为 TypeError (其他类型, 未知的键, 不能同时给出的两个字段) 或 RangeError (NaN, 负数, 需要上限处的无穷, 非整数, 文档以外的名称), D10.
 * 缺失或 nullish 的字段取默认值 (同现有解析器); 代替选项对象给出的数值代表工具的 [shorthandKey] 字段.
 */
internal class FlowOptions(
    private val function: String,
    private val options: Scriptable?,
    private val param: String = "options",
    private val shorthand: Any? = null,
    private val shorthandKey: String? = null,
) {

    /** Every key of the options is one of [keys] (the current parsers refuse a typo rather than ignore it). */
    fun keys(keys: Words) = also {
        (options as? ScriptableObject)?.ids?.forEach { id ->
            val key = id.toString()
            if (key !in keys.set) throw GlobalValues.cannotBe(function, field(key), keys.given)
        }
    }

    /** The value of a field (the shorthand for [shorthandKey]), null when it is missing or nullish. */
    fun raw(name: String): Any? = read(name) { value, _ -> value }

    /** A number: NaN is a RangeError, a value without a number a TypeError. */
    fun number(name: String): Double? = read(name) { value, where -> GlobalValues.number(function, where, value) }

    /** Milliseconds: a finite number that is not negative ([bounded]: below 2^63 once rounded, `repeatUntil`). */
    fun millis(name: String, bounded: Boolean = false): Double? = read(name) { value, where ->
        toMillis(where, value).also { if (bounded && Math.round(it) == Long.MAX_VALUE) throw GlobalValues.rangeError(function, where, "finite number", value) }
    }

    /** Milliseconds that round to more than 0 (`interval` of `backUntil`). */
    fun positiveMillis(name: String): Double? = read(name) { value, where ->
        toMillis(where, value).also { if (Math.round(it) <= 0L) throw GlobalValues.rangeError(function, where, "positive number", value) }
    }

    /** Milliseconds or `Infinity` (no limit). */
    fun limit(name: String): Double? = read(name) { value, where ->
        GlobalValues.number(function, where, value).also { if (it != Double.POSITIVE_INFINITY) nonNegative(where, it, value) }
    }

    /** A count: an integer from 0 to 2^31 - 1 ([positive]: from 1). */
    fun count(name: String, positive: Boolean = false): Double? = read(name) { value, where ->
        GlobalValues.number(function, where, value).also {
            val ok = it.isFinite() && it == Math.floor(it) && it <= Int.MAX_VALUE && (if (positive) it >= 1 else it >= 0)
            if (!ok) throw GlobalValues.rangeError(function, where, if (positive) "positive integer" else "non-negative integer", value)
        }
    }

    /** `backoff` of `retry`: a finite number from 1. */
    fun atLeastOne(name: String): Double? = read(name) { value, where ->
        GlobalValues.number(function, where, value).also { if (!it.isFinite() || it < 1.0) throw GlobalValues.rangeError(function, where, "finite number >= 1", value) }
    }

    /** A switch: any value, by truthiness. */
    fun bool(name: String): Boolean? = read(name) { value, _ -> RhinoScriptRuntime.toBoolean(value) }

    fun function(name: String) = read(name) { value, where -> if (value !is Callable) throw GlobalValues.typeError(function, where, "function", value) }

    fun string(name: String) = read(name) { value, where -> if (value !is CharSequence) throw GlobalValues.typeError(function, where, "string", value) }

    /** `root`: a `UiObject`. */
    fun root() = read("root") { value, where -> if (RhinoUtils.unwrap(value) !is UiObject) throw GlobalValues.typeError(function, where, "UiObject", value) }

    /** `compass` of a selector condition: a string that parses (Q67). */
    fun compass() = read("compass") { value, where ->
        if (value !is CharSequence) throw GlobalValues.typeError(function, where, "string", value)
        PickArguments.uiObject.compassError(function, where, value)?.let { throw it.exception() }
    }

    /**
     * `resultType` of a pickup: a string or an array, a result type for the `compass` of the same options ([single]: the
     * result of one node, `collectList`, without a compass). Q66 (2).
     */
    fun resultType(single: Boolean = false) = read("resultType") { value, where ->
        if (value !is CharSequence && value !is List<*>) throw GlobalValues.typeError(function, where, "string | Array", value)
        val compass = if (single) null else raw("compass") as? CharSequence
        PickArguments.uiObject.resultTypeError(function, where, if (value is CharSequence) value.toString() else value, compass, single)?.let { throw it.exception() }
    }

    /** One of [names] ([ignoreCase] as the current parser compares them), or with [orFunction] a function. */
    fun named(name: String, names: Words, ignoreCase: Boolean = false, orFunction: Boolean = false) = read(name) { value, where ->
        when {
            orFunction && value is Callable -> Unit
            value is CharSequence -> value.toString().let { s ->
                if (names.set.none { it.equals(s, ignoreCase) }) throw GlobalValues.rangeError(function, where, names.quoted, value)
            }
            else -> throw GlobalValues.typeError(function, where, if (orFunction) "string | function" else "string", value)
        }
    }

    /** A target of a tool (a `UiObject`, a selector form or a function, located when the tool runs): `container`. */
    fun target(name: String) = read(name) { value, where -> FlowArgs.target(function, where, value) }

    /** The condition of a smart click's `verify`: a selector form (checked as `pickup` reads it), a `UiObject` or a function. */
    fun verify() = read("verify") { value, where ->
        val v = RhinoUtils.unwrap(value)
        when {
            v is UiObject || v is Callable -> Unit
            FlowCondition.isSelectorForm(v) -> PickArguments.uiObject.selectorError(function, where, v)?.let { throw it.exception() }
            else -> throw GlobalValues.typeError(function, where, "UiObject | selector | function", value)
        }
    }

    /** A nested options object (`click` of `dismissPopups` / `toggle`), null when it is missing. */
    fun nested(name: String): FlowOptions? = read(name) { value, where ->
        if (!FlowArgs.isOptions(value)) throw GlobalValues.typeError(function, where, "Object", value)
        FlowOptions(function, value as Scriptable, where)
    }

    /** `humanize`: a boolean, or `{ offset, delay }` (see [humanize] of the companion). */
    fun humanize() = read("humanize") { value, where -> humanize(function, where, value) }

    /** `offset` of a smart click: one signed 32-bit integer for both axes, or an array of two. */
    fun offset() = read("offset") { value, where -> pair(function, where, value) { p, v -> integer(function, p, v, signed = true) } }

    /** Two fields that cannot be given together. */
    fun exclusive(first: String, second: String) = also {
        if (raw(first) != null && raw(second) != null) throw GlobalValues.cannotBe(function, field(second), "given with ${field(first)}")
    }

    /** A field as the current parsers read it (`prop`: a wrapped Java value unwrapped), else the shorthand. */
    private inline fun <T> read(name: String, check: (Any, String) -> T): T? {
        val value = options?.let { ScriptableObject.getProperty(it, name) }?.let { if (it is Wrapper) it.unwrap() else it }
        if (!GlobalValues.isNullish(value)) return check(value!!, field(name))
        if (name == shorthandKey && !GlobalValues.isNullish(shorthand)) return check(shorthand!!, param)
        return null
    }

    private fun toMillis(where: String, value: Any): Double = GlobalValues.number(function, where, value).also { nonNegative(where, it, value) }

    private fun nonNegative(where: String, number: Double, value: Any?) {
        if (!number.isFinite() || number < 0) throw GlobalValues.rangeError(function, where, "non-negative finite number", value)
    }

    private fun field(name: String) = "$param.$name"

    /**
     * The accepted keys of an options object or the accepted names of a field, with the texts of a refusal joined once
     * (joined on each refused call, they took up to 7 % of its time on the device, R2).
     */
    class Words(words: Collection<String>) {
        val set: Set<String> = LinkedHashSet(words)
        val given = "given (accepted: ${words.joinToString(", ")})"
        val quoted = words.joinToString(" | ") { "\"$it\"" }
    }

    companion object {

        private val HUMANIZE_KEYS = Words(listOf("offset", "delay"))

        /**
         * A `humanize` value (`flow.defaults` and the tools): `true` / `false`, or an object with `offset` (pixels, a
         * non-negative integer or an array of two) and `delay` (milliseconds, a non-negative number or an array of two,
         * the second not below the first).
         */
        fun humanize(function: String, where: String, value: Any) {
            if (value is Boolean) return
            if (!FlowArgs.isOptions(value)) throw GlobalValues.typeError(function, where, "Object | boolean", value)
            val o = FlowOptions(function, value as Scriptable, where).keys(HUMANIZE_KEYS)
            o.raw("offset")?.let { pair(function, "$where.offset", it) { p, v -> integer(function, p, v, signed = false) } }
            o.raw("delay")?.let { delay ->
                val (min, max) = pair(function, "$where.delay", delay) { p, v ->
                    loose(function, p, v).also { if (!it.isFinite() || it < 0) throw GlobalValues.rangeError(function, p, "non-negative finite number", v) }
                }
                if (Math.round(max) < Math.round(min)) throw GlobalValues.rangeError(function, "$where.delay", "[min, max] with max >= min", delay)
            }
        }

        /** One value for both members, or an array of exactly two. */
        private fun <T> pair(function: String, where: String, value: Any, convert: (String, Any?) -> T): Pair<T, T> {
            if (value !is NativeArray) return convert(where, value).let { it to it }
            // The elements as the current parser reads them (`List.get`: wrapped values unwrapped, holes and undefined as null).
            val list = value as List<*>
            if (list.size != 2) throw RhinoScriptRuntime.typeError(ScriptMessages.failedToCall(function, ScriptMessages.argumentLength(where, 2, list.size)))
            return convert("$where[0]", list[0]) to convert("$where[1]", list[1])
        }

        /** An integer read by ToNumber: [signed] in the 32-bit range, otherwise from 0 to 2^31 - 1. */
        private fun integer(function: String, where: String, value: Any?, signed: Boolean): Double = loose(function, where, value).also {
            val ok = it.isFinite() && it == Math.floor(it) && it <= Int.MAX_VALUE && it >= (if (signed) Int.MIN_VALUE.toDouble() else 0.0)
            if (!ok) throw GlobalValues.rangeError(function, where, if (signed) "32-bit integer" else "non-negative integer", value)
        }

        /** JS ToNumber of an element (null is 0, as `Context.toNumber`): NaN is a RangeError, a value without a number a TypeError. */
        private fun loose(function: String, where: String, value: Any?): Double {
            val number = RhinoScriptRuntime.toNumber(value)
            if (number.isNaN()) throw if (value is Number) GlobalValues.rangeError(function, where, "number", value) else GlobalValues.typeError(function, where, "number", value)
            return number
        }

    }

}
