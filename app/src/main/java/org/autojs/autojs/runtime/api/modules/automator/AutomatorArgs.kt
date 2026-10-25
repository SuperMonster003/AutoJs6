package org.autojs.autojs.runtime.api.modules.automator

import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Wrapper
import java.math.BigInteger
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime
import org.opencv.core.Point as OpencvPoint

/**
 * The argument checks of the `automator` actions on the new script module layer (roadmap P3.3, D10, appendix C Q65 (2)),
 * made before the current module (`augment/automator/Automator`) reads the same arguments again: which of the documented
 * forms a call takes (`click(target)`, `click(x, y)`, `click(text, index)`, `click(left, top, right, bottom)`, the forms of
 * `press` / `swipe` / `gesture`, ...), told apart by the count and the top-level types as the current module tells them
 * apart, then every value it reads: the coordinates, indexes and durations (JS ToNumber, then the 32-bit or 64-bit range
 * of its integer conversion), the elements of point lists and strokes, the fields of a point object. What the current
 * module accepts is accepted here as well; what it refused with an IllegalArgumentException (or an index out of bounds) is
 * a TypeError (no form matches, another type, an array too short) or a RangeError (NaN, infinite, out of range, a negative
 * duration). The checks of the Android gesture API itself (a duration of 0, a path with negative bounds) stay with it.
 *
 * zh-CN: 新脚本模块层上 automator 动作的参数检查 (P3.3, D10, 附录 C Q65 (2)), 在现有模块 (augment/automator/Automator) 再次读取同样的参数之前进行:
 * 先按现有模块的区分方式 (参数个数与顶层类型) 判断调用属于哪种文档形式 (click(target), click(x, y), click(text, index), click(left, top, right, bottom),
 * press / swipe / gesture 的各形式等), 再检查它读取的每个值: 坐标, 索引与时长 (JS ToNumber, 再按其整数转换的 32 位或 64 位范围), 点列表与笔画的元素, 点对象的字段.
 * 现有模块接受的这里同样接受; 它以 IllegalArgumentException (或索引越界) 拒绝的, 这里为 TypeError (没有匹配的形式, 类型不符, 数组过短) 或 RangeError (NaN, 无穷,
 * 越界, 负的时长). Android 手势 API 自身的检查 (时长为 0, 路径越出左上边界) 仍由它进行.
 */
internal object AutomatorArgs {

    private const val CLICK_FORMS = "(target), (x, y), (text, index), (left, top, right, bottom)"
    private const val SCROLL_FORMS = "(), (index), (text, index?), (left, top, right, bottom)"
    private const val PRESS_FORMS = "([x, y], duration?), (duration, [x, y]), (x, y, duration?)"
    private const val SWIPE_FORMS = "(duration, points), (points, duration), (duration, from, to), (from, to, duration), (x1, y1, x2, y2, duration)"
    private const val STROKE = "[startTime?, duration, [x, y], ...[x, y]]"

    /** `click` / `longClick`: a target (a `UiObject`, bounds, a point or the text), a point, a text with an index, or bounds. */
    fun clickArgs(function: String, args: Array<out Any?>) {
        val a = unwrap(args)
        when (a.size) {
            1 -> target(function, a[0])
            // Two numbers are a point; anything else is a text with the index of the match.
            2 -> if (isNumber(a[0]) && isNumber(a[1])) {
                ints(function, a, POINT)
            } else {
                ScriptArgs.nonNullish(a[0], function, "text")
                GlobalValues.int(function, "index", a[1])
            }
            4 -> ints(function, a, BOUNDS)
            else -> throw ScriptArgs.noOverload(function, args, CLICK_FORMS)
        }
    }

    /** `scrollDown` / `scrollUp`: the largest scrollable, the scrollable at an index, a text with an index, or bounds. */
    fun scrollArgs(function: String, args: Array<out Any?>) {
        val a = unwrap(args)
        when (a.size) {
            0 -> Unit
            1 -> if (isNumber(a[0])) GlobalValues.int(function, "index", a[0]) else ScriptArgs.nonNullish(a[0], function, "text")
            2 -> {
                ScriptArgs.nonNullish(a[0], function, "text")
                GlobalValues.int(function, "index", a[1])
            }
            4 -> ints(function, a, BOUNDS)
            else -> throw ScriptArgs.noOverload(function, args, SCROLL_FORMS)
        }
    }

    fun pressArgs(function: String, args: Array<out Any?>) {
        val a = unwrap(args)
        when (a.size) {
            // The current module reads the two first elements of the array as the arguments of the two-argument forms.
            1 -> {
                val array = a[0] as? NativeArray ?: throw ScriptArgs.noOverload(function, args, PRESS_FORMS)
                if (array.size < 2) throw length(function, "point", 2, array.size)
                press(function, args, RhinoUtils.unwrap(array[0]), RhinoUtils.unwrap(array[1])) { i, _ -> "point[$i]" }
            }
            2 -> press(function, args, a[0], a[1]) { _, name -> name }
            3 -> ints(function, a, POINT_AND_DURATION)
            else -> throw ScriptArgs.noOverload(function, args, PRESS_FORMS)
        }
    }

    fun swipeArgs(function: String, args: Array<out Any?>) {
        val a = unwrap(args)
        val (points, duration) = when {
            a.size == 2 && isNumber(a[0]) && a[1] is NativeArray -> group(function, "points", a[1] as List<*>) to 0
            a.size == 2 && a[0] is NativeArray && isNumber(a[1]) -> group(function, "points", a[0] as List<*>) to 1
            a.size == 3 && isNumber(a[0]) && a[1] is NativeArray && a[2] is NativeArray -> element(function, "from", a[1]) + element(function, "to", a[2]) to 0
            a.size == 3 && a[0] is NativeArray && a[1] is NativeArray && isNumber(a[2]) -> element(function, "from", a[0]) + element(function, "to", a[1]) to 2
            a.size == 5 -> return ints(function, a, SEGMENT_AND_DURATION)
            else -> throw ScriptArgs.noOverload(function, args, SWIPE_FORMS)
        }
        // The first two points are the segment; further ones are ignored, as before.
        if (points < 2) throw GlobalValues.cannotBe(function, "points", "fewer than 2 points (got $points)")
        GlobalValues.int(function, "duration", a[duration])
    }

    /** `gesture(duration, ...points)` / `gestureAsync(duration, ...points, callback?)`: a duration, then a point list. */
    fun gestureArgs(function: String, args: Array<out Any?>, callback: Boolean) {
        if (args.isEmpty()) throw ScriptArgs.arityError(function, 1, -1, 0)
        val a = unwrap(args)
        duration(function, "duration", a[0])
        group(function, "points", a.subList(1, end(a, callback)))
    }

    /** `gestures(...strokes)` / `gesturesAsync(...strokes, callback?)`: each stroke is an array `[startTime?, duration, ...points]`. */
    fun gesturesArgs(function: String, args: Array<out Any?>, callback: Boolean) {
        if (args.isEmpty()) throw ScriptArgs.arityError(function, 1, -1, 0)
        val a = unwrap(args)
        val strokes = a.subList(0, end(a, callback))
        // Every stroke is an array before any of them is read, as by the current module.
        strokes.forEachIndexed { i, stroke -> if (stroke !is List<*>) throw GlobalValues.typeError(function, "strokes[$i]", "Array", args[i]) }
        strokes.forEachIndexed { i, stroke -> stroke(function, "strokes[$i]", stroke as List<*>) }
    }

    /** `input(text)` / `input(index, text)`, `setText(text)` / `setText(index, text)`. */
    fun textArgs(function: String, args: Array<out Any?>) {
        if (args.size == 2) GlobalValues.int(function, "index", args[0])
        ScriptArgs.nonNullish(args.last(), function, "text")
    }

    /** A timeout of `waitForService`: nullish (no limit), or a 64-bit integer (a negative one waits without a limit, as before). */
    fun timeoutArg(function: String, timeout: Any?) {
        if (!isNullish(timeout)) GlobalValues.long(function, "timeout", timeout)
    }

    /**
     * The one argument of `click` / `longClick`: an array is a point when its first element is a number (the second one a
     * number or nullish), an object a point with `x` and `y`; any other value (an array included) is the text to click.
     */
    private fun target(function: String, target: Any?) {
        ScriptArgs.nonNullish(target, function, "target")
        when (target) {
            is List<*> -> {
                if (target.size < 2) throw length(function, "target", 2, target.size)
                val (x, y) = target
                if (isNumber(x)) when {
                    isNumber(y) -> ints(function, listOf(x, y), listOf("target[0]", "target[1]"))
                    isNullish(y) -> GlobalValues.int(function, "target[0]", x)
                }
            }
            is OpencvPoint -> ints(function, listOf(target.x, target.y), listOf("target.x", "target.y"))
            is NativeObject -> POINT.forEach { key ->
                val value = ScriptableObject.getProperty(target, key).let { if (it is Wrapper) it.unwrap() else it }
                if (isNullish(value)) throw GlobalValues.typeError(function, "target.$key", "number", value)
                GlobalValues.int(function, "target.$key", value)
            }
        }
    }

    /** The two-argument forms of `press`: a duration and a point array (either order), or a point. */
    private inline fun press(function: String, args: Array<out Any?>, first: Any?, second: Any?, param: (Int, String) -> String) {
        when {
            isNumber(first) && second is NativeArray -> {
                point(function, param(1, "point"), second)
                GlobalValues.int(function, param(0, "duration"), first)
            }
            first is NativeArray && isNumber(second) -> {
                point(function, param(0, "point"), first)
                GlobalValues.int(function, param(1, "duration"), second)
            }
            isNumber(first) && isNumber(second) -> {
                GlobalValues.int(function, param(0, "x"), first)
                GlobalValues.int(function, param(1, "y"), second)
            }
            else -> throw ScriptArgs.noOverload(function, args, PRESS_FORMS)
        }
    }

    /**
     * A point list as the current module reads it (`toPointsGroup`), returning how many points it holds: a list of one
     * array is that array; numbers only are pairs of coordinates; otherwise every element is a point array
     * ([element]). A coordinate is any value with a number (the point is then truncated to integers).
     */
    private fun group(function: String, param: String, args: List<*>): Int {
        val single = args.size == 1 && args[0] is List<*>
        val list = if (single) args[0] as List<*> else args
        val where = if (single) "$param[0]" else param
        if (list.isNotEmpty() && list.all { it is Number }) {
            if (list.size % 2 != 0) throw GlobalValues.cannotBe(function, where, "an odd count of coordinates (${list.size})")
            list.forEachIndexed { i, v -> GlobalValues.number(function, "$where[$i]", v) }
            return list.size / 2
        }
        return list.withIndex().sumOf { (i, v) -> element(function, "$where[$i]", v) }
    }

    /** An element of a point list: `[x]` (the same value for both axes), `[x, y]`, or a nested point list. */
    private fun element(function: String, param: String, value: Any?): Int {
        if (value !is List<*>) throw GlobalValues.typeError(function, param, "[x, y]", value)
        if (value.isEmpty()) throw GlobalValues.cannotBe(function, param, "an empty array")
        return when {
            value.all { it is List<*> } -> group(function, param, value)
            value.size <= 2 -> 1.also { value.forEachIndexed { i, v -> GlobalValues.number(function, "$param[$i]", v) } }
            else -> throw length(function, param, 2, value.size)
        }
    }

    /** A stroke as the current module reads it (`toStrokes`): with a start time when the second element is a number. */
    private fun stroke(function: String, param: String, stroke: List<*>) {
        val timed = stroke.size >= 2 && stroke[1] is Number
        val first = if (timed) 2 else 1
        if (timed) duration(function, "$param[0]", stroke[0])
        if (stroke.size <= first) throw GlobalValues.typeError(function, param, STROKE, stroke)
        for (j in first until stroke.size) {
            val point = stroke[j]
            if (point !is List<*>) throw GlobalValues.typeError(function, "$param[$j]", "[x, y]", point)
            point(function, "$param[$j]", point)
        }
        duration(function, "$param[${first - 1}]", stroke[first - 1])
    }

    /** A point array `[x, y]` read by destructuring (further elements are ignored): two 32-bit integers. */
    private fun point(function: String, param: String, point: List<*>) {
        if (point.size < 2) throw length(function, param, 2, point.size)
        ints(function, point.take(2), listOf("$param[0]", "$param[1]"))
    }

    /** Milliseconds of a gesture (or the start time of a stroke): a 64-bit integer that is not negative. */
    private fun duration(function: String, param: String, value: Any?) {
        if (GlobalValues.long(function, param, value) < 0) throw GlobalValues.rangeError(function, param, "non-negative number", value)
    }

    private fun ints(function: String, values: List<Any?>, params: List<String>) {
        params.forEachIndexed { i, param -> GlobalValues.int(function, param, values[i]) }
    }

    private fun length(function: String, param: String, expected: Int, actual: Int): RuntimeException =
        RhinoScriptRuntime.typeError(ScriptMessages.failedToCall(function, ScriptMessages.argumentLength(param, expected, actual)))

    /** The arguments as the current module reads them (`unwrapArguments`): wrapped Java values unwrapped. */
    private fun unwrap(args: Array<out Any?>) = args.map { RhinoUtils.unwrap(it) }

    /** A JS number (`isJsNumber` of the current module): a BigInt is not one. */
    private fun isNumber(value: Any?) = value is Number && value !is BigInteger

    /** The end of the points or strokes: a trailing gesture callback (a function, an object with `onCompleted` / `onCancelled`) is not one. */
    private fun end(args: List<Any?>, callback: Boolean) =
        if (callback && args.size > 1 && Automator.isGestureResultCallbackLike(args.last())) args.size - 1 else args.size

    private val POINT = listOf("x", "y")
    private val BOUNDS = listOf("left", "top", "right", "bottom")
    private val POINT_AND_DURATION = listOf("x", "y", "duration")
    private val SEGMENT_AND_DURATION = listOf("x1", "y1", "x2", "y2", "duration")

}
