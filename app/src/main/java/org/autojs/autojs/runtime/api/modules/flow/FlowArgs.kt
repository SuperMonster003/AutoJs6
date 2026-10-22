package org.autojs.autojs.runtime.api.modules.flow

import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.ScriptableObject

/**
 * The argument checks of the Flow API on the new script module layer (roadmap P3.3, D10, appendix C Q65): the count and
 * the top-level shape of every positional argument, before the current parser (`augment/flow/FlowArguments`) reads them
 * again and builds the steps. A shape the documented signatures do not accept is a TypeError, a number out of its range
 * (NaN, negative, an infinite interval) a RangeError; the fields of an options object and the grammar of a condition are
 * still checked by the current parser (IllegalArgumentException, appendix E "P3.3"), until P5.2 moves it here.
 *
 * zh-CN: 新脚本模块层上 Flow API 的参数检查 (P3.3, D10, 附录 C Q65): 在现有解析器 (augment/flow/FlowArguments) 再次读取并构建步骤之前, 检查个数与
 * 各位置参数的顶层形态. 文档签名不接受的形态为 TypeError, 越界的数值 (NaN, 负数, 无穷的间隔) 为 RangeError; 选项对象的字段与条件的语法仍由现有解析器检查
 * (IllegalArgumentException, 附录 E "P3.3"), P5.2 迁入新层后改正.
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
        if (withDelay && tail.numbers <= 2 && !tail.options) {
            throw GlobalValues.cannotBe(function, "delay", "missing (a number after the interval, or options.delay)")
        }
    }

    /** `(name, timeout?, interval?, onOk?, onErr?)` or `(name, options, onOk?, onErr?)`: the activity or package name is a string. */
    fun nativeWaitArgs(function: String, args: Array<out Any?>) {
        val name = args.getOrNull(0)
        if (name !is CharSequence) throw GlobalValues.typeError(function, "name", "string", name)
        tail(function, args, maxNumbers = 2)
    }

    /** `sleep(millis, millisMax?)`: a non-negative finite number, then a number or a string with a number (the bounds of `sleep`). */
    fun sleepArgs(function: String, millis: Any?, millisMax: Any?) {
        nonNegative(function, "millis", GlobalValues.number(function, "millis", millis), millis)
        when {
            isNullish(millisMax) -> Unit
            millisMax is Number -> if (millisMax.toDouble().isNaN()) throw GlobalValues.rangeError(function, "millisMax", "number", millisMax)
            millisMax is CharSequence -> if (millisMax.none { it in '0'..'9' }) throw GlobalValues.rangeError(function, "millisMax", "string with a number", millisMax)
            else -> throw GlobalValues.typeError(function, "millisMax", "number | string", millisMax)
        }
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

    fun isOptions(value: Any?) = value is ScriptableObject && value !is NativeArray && value !is Callable

    /** What follows the first argument: numbers up to [maxNumbers], one options object and up to two callbacks, in any order. */
    private fun tail(function: String, args: Array<out Any?>, maxNumbers: Int): Tail {
        var numbers = 0
        var callbacks = 0
        var options = false
        for (i in 1 until args.size) {
            val a = args[i]
            when {
                isNullish(a) -> Unit
                a is Callable -> if (++callbacks > 2) throw GlobalValues.typeError(function, "argument $i", "number | Object", a)
                a is Number -> {
                    if (numbers == maxNumbers) throw GlobalValues.typeError(function, "argument $i", "function | Object", a)
                    number(function, numbers++, a)
                }
                isOptions(a) -> if (options) throw GlobalValues.typeError(function, "argument $i", "number | function", a) else options = true
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

    private class Tail(val numbers: Int, val options: Boolean)

    private val NUMBER_NAMES = arrayOf("timeout", "interval", "delayMin", "delayMax")

}
