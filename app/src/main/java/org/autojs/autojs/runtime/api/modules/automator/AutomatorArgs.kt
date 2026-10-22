package org.autojs.autojs.runtime.api.modules.automator

import org.autojs.autojs.runtime.api.augment.automator.Automator
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Wrapper

/**
 * The argument checks of the `automator` actions on the new script module layer (roadmap P3.3, D10, appendix C Q65): which
 * of the documented forms a call takes (`click(target)`, `click(x, y)`, `click(text, index)`, `click(left, top, right,
 * bottom)`, the forms of `press` / `swipe` / `gesture`, ...), from the count and the top-level types of the arguments. A call
 * that matches none is a TypeError listing the forms, a coordinate, index or duration that is NaN a RangeError; the
 * elements of a point list and the fields of a target object are still checked by the current module
 * (`augment/automator/Automator`, IllegalArgumentException, appendix E "P3.3") until P5.2 moves them here.
 *
 * zh-CN: 新脚本模块层上 automator 动作的参数检查 (P3.3, D10, 附录 C Q65): 由参数个数与顶层类型判断调用属于哪种文档形式 (click(target), click(x, y),
 * click(text, index), click(left, top, right, bottom), press / swipe / gesture 的各形式等). 哪种都不符为 TypeError (列出各形式), 坐标, 索引或时长为 NaN
 * 时为 RangeError; 点列表的元素与目标对象的字段仍由现有模块检查 (augment/automator/Automator, IllegalArgumentException, 附录 E "P3.3"), P5.2 迁入新层后改正.
 */
internal object AutomatorArgs {

    private const val CLICK_FORMS = "(target), (x, y), (text, index), (left, top, right, bottom)"
    private const val SCROLL_FORMS = "(), (index), (text, index?), (left, top, right, bottom)"
    private const val PRESS_FORMS = "([x, y], duration?), (duration, [x, y]), (x, y, duration?)"
    private const val SWIPE_FORMS = "(duration, points), (points, duration), (duration, from, to), (from, to, duration), (x1, y1, x2, y2, duration)"

    /** `click` / `longClick`: a target (a `UiObject`, bounds, a point or the text), a point, a text with an index, or bounds. */
    fun clickArgs(function: String, args: Array<out Any?>) {
        when (args.size) {
            1 -> ScriptArgs.nonNullish(args[0], function, "target")
            2 -> if (args[0] is Number) {
                number(function, "x", args[0])
                number(function, "y", args[1])
            } else {
                ScriptArgs.nonNullish(args[0], function, "text")
                number(function, "index", args[1])
            }
            4 -> numbers(function, args, BOUNDS)
            else -> throw ScriptArgs.noOverload(function, args, CLICK_FORMS)
        }
    }

    /** `scrollDown` / `scrollUp`: the largest scrollable, the scrollable at an index, a text with an index, or bounds. */
    fun scrollArgs(function: String, args: Array<out Any?>) {
        when (args.size) {
            0 -> Unit
            1 -> if (args[0] is Number) number(function, "index", args[0]) else ScriptArgs.nonNullish(args[0], function, "text")
            2 -> {
                ScriptArgs.nonNullish(args[0], function, "text")
                number(function, "index", args[1])
            }
            4 -> numbers(function, args, BOUNDS)
            else -> throw ScriptArgs.noOverload(function, args, SCROLL_FORMS)
        }
    }

    fun pressArgs(function: String, args: Array<out Any?>) {
        val ok = when (args.size) {
            1 -> isArray(args[0])
            2 -> args[0] is Number && isArray(args[1]) || isArray(args[0]) && args[1] is Number || args[0] is Number && args[1] is Number
            3 -> true.also { numbers(function, args, POINT_AND_DURATION) }
            else -> false
        }
        if (!ok) throw ScriptArgs.noOverload(function, args, PRESS_FORMS)
        args.forEachIndexed { i, a -> if (a is Number) number(function, "argument $i", a) }
    }

    fun swipeArgs(function: String, args: Array<out Any?>) {
        val ok = when (args.size) {
            2 -> args[0] is Number && isArray(args[1]) || isArray(args[0]) && args[1] is Number
            3 -> args[0] is Number && isArray(args[1]) && isArray(args[2]) || isArray(args[0]) && isArray(args[1]) && args[2] is Number
            5 -> true.also { numbers(function, args, SEGMENT_AND_DURATION) }
            else -> false
        }
        if (!ok) throw ScriptArgs.noOverload(function, args, SWIPE_FORMS)
        args.forEachIndexed { i, a -> if (a is Number) number(function, "argument $i", a) }
    }

    /** `gesture(duration, ...points)` / `gestureAsync(duration, ...points, callback?)`: a duration, then arrays or numbers. */
    fun gestureArgs(function: String, args: Array<out Any?>, callback: Boolean) {
        if (args.isEmpty()) throw ScriptArgs.arityError(function, 1, -1, 0)
        duration(function, "duration", args[0])
        val points = if (callback && args.size > 1 && isCallbackLike(args.last())) args.size - 1 else args.size
        for (i in 1 until points) {
            val a = args[i]
            if (a !is Number && !isArray(a)) throw GlobalValues.typeError(function, "points[${i - 1}]", "number[] | number", a)
        }
    }

    /** `gestures(...strokes)` / `gesturesAsync(...strokes, callback?)`: each stroke is an array `[delay?, duration, ...points]`. */
    fun gesturesArgs(function: String, args: Array<out Any?>, callback: Boolean) {
        if (args.isEmpty()) throw ScriptArgs.arityError(function, 1, -1, 0)
        val strokes = if (callback && args.size > 1 && isCallbackLike(args.last())) args.size - 1 else args.size
        for (i in 0 until strokes) {
            if (!isArray(args[i])) throw GlobalValues.typeError(function, "strokes[$i]", "Array", args[i])
        }
    }

    /** `input(text)` / `input(index, text)`, `setText(text)` / `setText(index, text)`. */
    fun textArgs(function: String, args: Array<out Any?>) {
        if (args.size == 2) number(function, "index", args[0])
        ScriptArgs.nonNullish(args.last(), function, "text")
    }

    /** A timeout of `waitForService`: nullish (no limit), or a number that is not NaN (a negative one waits without a limit, as before). */
    fun timeoutArg(function: String, timeout: Any?) {
        if (isNullish(timeout)) return
        if (timeout !is Number) throw GlobalValues.typeError(function, "timeout", "number", timeout)
        if (timeout.toDouble().isNaN()) throw GlobalValues.rangeError(function, "timeout", "number", timeout)
    }

    private fun duration(function: String, param: String, value: Any?) {
        if (value !is Number) throw GlobalValues.typeError(function, param, "number", value)
        val d = value.toDouble()
        if (d.isNaN() || d < 0) throw GlobalValues.rangeError(function, param, "non-negative number", value)
    }

    private fun numbers(function: String, args: Array<out Any?>, params: List<String>) {
        params.forEachIndexed { i, param -> number(function, param, args[i]) }
    }

    private fun number(function: String, param: String, value: Any?) {
        if (value !is Number) throw GlobalValues.typeError(function, param, "number", value)
        if (value.toDouble().isNaN()) throw GlobalValues.rangeError(function, param, "number", value)
    }

    private fun isArray(value: Any?) = value is NativeArray || (if (value is Wrapper) value.unwrap() else value) is List<*>

    /** A gesture callback: a function, an object with `onCompleted` / `onCancelled`, or a `GestureResultCallback`. */
    private fun isCallbackLike(value: Any?) = Automator.isGestureResultCallbackLike(if (value is Wrapper) value.unwrap() else value)

    private val BOUNDS = listOf("left", "top", "right", "bottom")
    private val POINT_AND_DURATION = listOf("x", "y", "duration")
    private val SEGMENT_AND_DURATION = listOf("x1", "y1", "x2", "y2", "duration")

}
