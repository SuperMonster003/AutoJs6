package org.autojs.autojs.runtime.api.modules.flow

import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.pick.PickArguments
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

/**
 * The argument checks of the Flow API on the new script module layer (roadmap P3.3, D10, appendix C Q65 (2)), before the
 * current parser (`augment/flow/FlowArguments`) reads the arguments again and builds the steps: the count and the shape of
 * every positional argument, the fields of the options objects ([FlowOptions]) and the kind of a condition. What the
 * current parser accepts is accepted here as well; what it refused with an IllegalArgumentException is a TypeError (a
 * shape, a type, a key or a field the documented signatures do not accept) or a RangeError (a number out of its range: NaN,
 * negative, an infinite interval, a name outside the documented ones).
 *
 * zh-CN: 新脚本模块层上 Flow API 的参数检查 (P3.3, D10, 附录 C Q65 (2)), 在现有解析器 (augment/flow/FlowArguments) 再次读取参数并构建步骤之前进行: 个数与
 * 各位置参数的形态, 选项对象的字段 (FlowOptions) 与条件的种类. 现有解析器接受的这里同样接受; 它以 IllegalArgumentException 拒绝的, 这里为 TypeError
 * (文档签名不接受的形态, 类型, 键或字段) 或 RangeError (越界的数值: NaN, 负数, 无穷的间隔, 文档以外的名称).
 */
internal object FlowArgs {

    /**
     * `(cond, timeout?, interval?, onOk?, onErr?)` or `(cond, options, onOk?, onErr?)`; with [withDelay] (`*After`) two
     * further numbers are the delay (`delayMin`, `delayMax`), required unless the options give it. Nullish arguments after
     * `cond` are skipped, as by the current parser.
     */
    fun waitArgs(function: String, args: Array<out Any?>, withDelay: Boolean) {
        ScriptArgs.nonNullish(args.getOrNull(0), function, "cond")
        val tail = tail(function, args, maxNumbers = if (withDelay) 4 else 2)
        waitOptions(function, tail, withDelay)
        cond(function, "cond", args[0])
    }

    /** `(name, timeout?, interval?, onOk?, onErr?)` or `(name, options, onOk?, onErr?)`: the activity or package name is a string. */
    fun nativeWaitArgs(function: String, args: Array<out Any?>) {
        val name = args.getOrNull(0)
        if (name !is CharSequence) throw GlobalValues.typeError(function, "name", "string", name)
        waitOptions(function, tail(function, args, maxNumbers = 2), withDelay = false)
    }

    /** `whenPresent(cond, handler, options?)`: the polling and the scope of the wait (the keys the current parser accepts). */
    fun whenPresentArgs(function: String, cond: Any?, options: Any?) {
        optionsArg(function, "options", options)
        val o = FlowOptions(function, options as? Scriptable).keys(WHEN_PRESENT_KEYS)
        ScriptArgs.nonNullish(cond, function, "cond")
        o.limit("timeout")
        o.millis("interval")
        o.limit("times")
        scope(o)
        cond(function, "cond", cond)
    }

    /** `repeatUntil(action, cond, options?)`: a finite timeout, the interval, the attempts, `retryOn` and the scope. */
    fun repeatUntilArgs(function: String, cond: Any?, options: Any?) {
        optionsArg(function, "options", options)
        val o = FlowOptions(function, options as? Scriptable).keys(REPEAT_UNTIL_KEYS)
        o.millis("timeout", bounded = true)
        o.millis("interval")
        o.count("maxAttempts")
        o.function("retryOn")
        scope(o)
        ScriptArgs.nonNullish(cond, function, "cond")
        cond(function, "cond", cond)
    }

    /** `defaults(options?)`: the keys of the flow defaults; nothing is written when one of them is refused. */
    fun defaultsArgs(function: String, options: Any?) {
        optionsArg(function, "options", options)
        val o = FlowOptions(function, options as? Scriptable).keys(DEFAULT_KEYS)
        o.limit("timeout")
        o.millis("interval")
        o.millis("stableFor")
        o.humanize()
    }

    /** `sleep(millis, millisMax?)`: a non-negative finite number, then a number or a string with a number (the bounds of `sleep`). */
    fun sleepArgs(function: String, millis: Any?, millisMax: Any?) {
        nonNegative(function, "millis", GlobalValues.number(function, "millis", millis), millis)
        sleepMax(function, "millisMax", millisMax)
    }

    /** An options object: nullish, or a script object that is neither an array nor a function. */
    fun optionsArg(function: String, param: String, value: Any?) {
        if (!isNullish(value) && !isOptions(value)) throw GlobalValues.typeError(function, param, "Object", value)
    }

    /** The sources of `all` / `race` / `any`: the arguments, or one array of them; none of them nullish. */
    fun sourcesArgs(function: String, args: Array<out Any?>) {
        val single = args.singleOrNull()
        val sources: List<Any?> = when (single) {
            is NativeArray -> List(single.length.toInt()) { single.get(it) }
            is List<*> -> single
            else -> args.asList()
        }
        sources.forEachIndexed { i, source -> if (isNullish(source)) throw GlobalValues.cannotBe(function, "sources[$i]", ScriptArgs.describe(source)) }
    }

    /**
     * The condition of a wait: a selector form (a string, a regular expression, a selector, an object or an array of
     * selector fields, a number), a `UiObject`, a function, a flow or another thenable, told apart in the order of the
     * current parser; a selector form is checked as `pickup` reads it (Q66 (2)).
     */
    fun cond(function: String, param: String, value: Any?) {
        val v = RhinoUtils.unwrap(value)
        when {
            v is FlowNativeObject || v is UiObject || v is Callable || v is Scriptable && isThenable(v) -> Unit
            FlowCondition.isSelectorForm(v) -> PickArguments.uiObject.selectorError(function, param, v)?.let { throw it.exception() }
            else -> throw GlobalValues.typeError(function, param, "selector | UiObject | function | Flow | Promise", value)
        }
    }

    /** A target of a tool, located when the tool runs: a selector form (checked as `pickup` reads it), a `UiObject` or a function. */
    fun target(function: String, param: String, value: Any?) {
        val v = RhinoUtils.unwrap(value)
        when {
            v is UiObject || v is Callable || v is Function0<*> -> Unit
            FlowCondition.isSelectorForm(v) -> PickArguments.uiObject.selectorError(function, param, v)?.let { throw it.exception() }
            else -> throw GlobalValues.typeError(function, param, "selector | UiObject | function", value)
        }
    }

    fun isOptions(value: Any?) = value is ScriptableObject && value !is NativeArray && value !is Callable

    /** `root`, `compass` and `resultType`: the scope of a selector condition. */
    fun scope(o: FlowOptions) {
        o.root()
        o.compass()
        o.resultType()
    }

    /** The options of a wait, read as the current parser reads them: a field given by a positional number is not read. */
    private fun waitOptions(function: String, tail: Tail, withDelay: Boolean) {
        val o = FlowOptions(function, tail.options)
        if (tail.numbers < 1) o.limit("timeout")
        if (tail.numbers < 2) o.millis("interval")
        o.limit("times")
        o.function("snapshot")
        o.exclusive("snapshot", "compare")
        o.millis("stableFor")
        o.named("compare", COMPARE_NAMES, orFunction = true)
        o.named("missing", MISSING_NAMES)
        if (withDelay && tail.numbers > 2) return scope(o)
        val delay = o.raw("delay")
        if (delay != null) {
            delay(function, "options.delay", delay)
        } else if (withDelay) {
            throw GlobalValues.cannotBe(function, "delay", "missing (a number after the interval, or options.delay)")
        }
        scope(o)
    }

    /** `options.delay`: the arguments of `sleep`, a number (a numeric string too) or an array of one or two. */
    private fun delay(function: String, param: String, value: Any) {
        val list = value as? List<*> ?: listOf(value)
        if (list.isEmpty() || list.size > 2) throw GlobalValues.typeError(function, param, "number | [millis, millisMax?]", value)
        val where = if (value is List<*>) "$param[0]" else param
        nonNegative(function, where, GlobalValues.number(function, where, list[0]), list[0])
        if (list.size == 2) sleepMax(function, "$param[1]", list[1])
    }

    /** The upper bound of `sleep`: a number, or a string holding the delta around the lower one. */
    private fun sleepMax(function: String, param: String, value: Any?) {
        when {
            isNullish(value) -> Unit
            value is Number -> if (value.toDouble().isNaN()) throw GlobalValues.rangeError(function, param, "number", value)
            value is CharSequence -> if (value.none { it in '0'..'9' }) throw GlobalValues.rangeError(function, param, "string with a number", value)
            else -> throw GlobalValues.typeError(function, param, "number | string", value)
        }
    }

    /** What follows the first argument: numbers up to [maxNumbers], one options object and up to two callbacks, in any order. */
    private fun tail(function: String, args: Array<out Any?>, maxNumbers: Int): Tail {
        var numbers = 0
        var callbacks = 0
        var options: Scriptable? = null
        for (i in 1 until args.size) {
            val a = args[i]
            when {
                isNullish(a) -> Unit
                a is Callable -> if (++callbacks > 2) throw GlobalValues.typeError(function, "argument $i", "number | Object", a)
                a is Number -> {
                    if (numbers == maxNumbers) throw GlobalValues.typeError(function, "argument $i", "function | Object", a)
                    number(function, numbers++, a)
                }
                isOptions(a) -> if (options != null) throw GlobalValues.typeError(function, "argument $i", "number | function", a) else options = a as Scriptable
                else -> throw GlobalValues.typeError(function, "argument $i", "number | Object | function", a)
            }
        }
        return Tail(numbers, options)
    }

    /** A positional number: the timeout (`Infinity` for none), the interval, then the bounds of the delay. */
    private fun number(function: String, index: Int, value: Number) {
        val param = NUMBER_NAMES[index]
        val d = value.toDouble()
        when (index) {
            // `Infinity` waits without a time limit.
            0 -> if (d != Double.POSITIVE_INFINITY) nonNegative(function, param, d, value)
            // The upper bound of the delay is only ordered with the lower one (as by `sleep`).
            3 -> if (d.isNaN()) throw GlobalValues.rangeError(function, param, "number", value)
            else -> nonNegative(function, param, d, value)
        }
    }

    private fun nonNegative(function: String, param: String, d: Double, value: Any?) {
        if (d.isNaN() || d.isInfinite() || d < 0) throw GlobalValues.rangeError(function, param, "non-negative finite number", value)
    }

    /** A thenable that is not a flow (the current executor subscribes to its `then`). */
    private fun isThenable(value: Scriptable) = value !is FlowNativeObject && ScriptableObject.getProperty(value, "then") is Callable

    private class Tail(val numbers: Int, val options: Scriptable?)

    private val NUMBER_NAMES = arrayOf("timeout", "interval", "delayMin", "delayMax")

    private val COMPARE_NAMES = FlowOptions.Words(listOf("fingerprint", "bounds", "content"))

    private val MISSING_NAMES = FlowOptions.Words(listOf("reset", "fail"))

    private val WHEN_PRESENT_KEYS = FlowOptions.Words(listOf("timeout", "interval", "times", "root", "compass", "resultType", "recoverService"))

    private val REPEAT_UNTIL_KEYS = FlowOptions.Words(listOf("timeout", "interval", "maxAttempts", "retryOn", "root", "compass", "resultType"))

    private val DEFAULT_KEYS = FlowOptions.Words(FlowArguments.DEFAULT_KEYS)

}
