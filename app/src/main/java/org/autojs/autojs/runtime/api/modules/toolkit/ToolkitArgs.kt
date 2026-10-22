package org.autojs.autojs.runtime.api.modules.toolkit

import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.flow.FlowArgs
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.regexp.NativeRegExp

/**
 * The argument checks of the automation toolkit on the new script module layer (roadmap P3.3, D10, appendix C Q65): the
 * tools (`smartClick`, `clickIfExists`, `clickAny`, `findAny`, `scrollUntil`, `typeInto`, `dismissPopups`,
 * `collectList`, `launchAndWait`, `backUntil`, `backToApp`, `toggle`, `retry`) as the synchronous globals of `automator`
 * and the roots of `flow`, and the event-driven waits (`waitForIdle`, `waitForEvent`, `waitForToast`,
 * `waitForNotification`). Checked here: the count and the top-level shape of the positional arguments (the required
 * targets, the options object or its number shorthand, the callbacks of the flow forms, the timeouts); the tools and the
 * waits themselves are the current ones (`augment/toolkit/RuntimeToolkit`, `augment/automator/EventWaitApi`), which still
 * check the fields of the options, the target grammar and the filters (IllegalArgumentException, appendix E "P3.3") until
 * P5.2 moves them here.
 *
 * zh-CN: 新脚本模块层上自动化工具集的参数检查 (P3.3, D10, 附录 C Q65): 各工具 (automator 的同步全局函数与 flow 的起点) 与事件驱动等待. 这里检查
 * 个数与位置参数的顶层形态 (必需的目标, 选项对象或其数值简写, flow 形式的回调, 超时); 工具与等待本身沿用现有实现 (augment/toolkit/RuntimeToolkit,
 * augment/automator/EventWaitApi), 选项字段, 目标语法与过滤器仍由它们检查 (IllegalArgumentException, 附录 E "P3.3"), P5.2 迁入新层后改正.
 */
internal object ToolkitArgs {

    /** The positional parameters of each tool (the synchronous and root forms); an optional one is marked by `?`. */
    private val POSITIONALS = mapOf(
        "smartClick" to listOf("target"),
        "smartClickBounds" to listOf("target"),
        "clickIfExists" to listOf("target"),
        "clickBoundsIfExists" to listOf("target"),
        "clickAny" to listOf("targets"),
        "clickBoundsAny" to listOf("targets"),
        "findAny" to listOf("targets"),
        "scrollUntil" to listOf("target"),
        "typeInto" to listOf("target?", "text"),
        "dismissPopups" to listOf("targets"),
        "collectList" to listOf("container?", "item"),
        "launchAndWait" to listOf("app"),
        "backUntil" to listOf("cond"),
        "backToApp" to listOf("app"),
        "toggle" to listOf("target", "checked"),
        "retry" to listOf("fn"),
    )

    val TOOLS: Set<String> get() = POSITIONALS.keys

    /**
     * `tool(...positionals, options?, onOk?, onErr?)`: the required positionals, then an options object or a number (the
     * shorthand of the tool), then, for a flow root ([callbacks]), up to two functions; a synchronous tool takes nothing
     * after the options.
     */
    fun toolArgs(function: String, tool: String, args: Array<out Any?>, callbacks: Boolean) {
        val params = POSITIONALS.getValue(tool)
        params.forEachIndexed { i, param ->
            if (!param.endsWith('?')) ScriptArgs.nonNullish(args.getOrNull(i), function, param)
        }
        if (tool == "retry" && args[0] !is Callable) throw GlobalValues.typeError(function, "fn", "function", args[0])
        var index = params.size
        when (val slot = args.getOrNull(index)) {
            is Callable -> Unit
            is Number -> {
                if (slot.toDouble().isNaN()) throw GlobalValues.rangeError(function, "options", "number", slot)
                index++
            }
            else -> {
                if (index < args.size) FlowArgs.optionsArg(function, "options", slot)
                index++
            }
        }
        var functions = 0
        for (i in index until args.size) {
            val a = args[i]
            when {
                isNullish(a) -> Unit
                !callbacks -> throw ScriptArgs.arityError(function, params.count { !it.endsWith('?') }, params.size + 1, args.size)
                a !is Callable -> throw GlobalValues.typeError(function, "argument $i", "function", a)
                ++functions > 2 -> throw GlobalValues.typeError(function, "argument $i", "undefined", a)
            }
        }
    }

    /** `waitForIdle(quietFor?)` / `waitForIdle(options?)`. */
    fun idleArgs(function: String, args: Array<out Any?>) {
        atMost(function, args, 1)
        when (val a = args.getOrNull(0)) {
            null, is org.mozilla.javascript.Undefined, is NativeObject -> Unit
            is Number -> millis(function, "quietFor", a)
            else -> throw GlobalValues.typeError(function, "options", "number | Object", a)
        }
    }

    /** `waitForEvent(type?, filter?, timeout?)`: a type name or an array of them, a function or an object, a number of milliseconds. */
    fun eventArgs(function: String, args: Array<out Any?>) {
        atMost(function, args, 3)
        val type = args.getOrNull(0)
        if (!isNullish(type) && type !is CharSequence && type !is NativeArray) throw GlobalValues.typeError(function, "type", "string | Array", type)
        var filter = false
        var timeout = false
        for (i in 1 until args.size) {
            val a = args[i]
            when {
                isNullish(a) -> Unit
                a is Number && !timeout -> millis(function, "timeout", a).also { timeout = true }
                (a is Callable || a is NativeObject) && !filter -> filter = true
                else -> throw GlobalValues.typeError(function, "argument $i", if (filter) "number" else "function | Object", a)
            }
        }
    }

    /** `waitForToast(filter?, timeout?)` / `waitForNotification(filter?, timeout?)`, or `(timeout)` alone. */
    fun textAndTimeoutArgs(function: String, args: Array<out Any?>) {
        atMost(function, args, 2)
        val first = args.getOrNull(0)
        if (first is Number) {
            atMost(function, args, 1)
            millis(function, "timeout", first)
            return
        }
        if (!isNullish(first) && first !is CharSequence && first !is NativeRegExp && first !is Callable && first !is NativeObject) {
            throw GlobalValues.typeError(function, "filter", "string | RegExp | function | Object", first)
        }
        args.getOrNull(1).takeUnless { isNullish(it) }?.let { millis(function, "timeout", it) }
    }

    private fun atMost(function: String, args: Array<out Any?>, max: Int) {
        if (args.size > max) throw ScriptArgs.arityError(function, 0, max, args.size)
    }

    /** A number of milliseconds (a numeric string as well, as before): not NaN, not negative; `Infinity` waits without a limit. */
    private fun millis(function: String, param: String, value: Any?) {
        val d = when (value) {
            is Number -> value.toDouble()
            is CharSequence -> value.toString().toDoubleOrNull() ?: throw GlobalValues.typeError(function, param, "number", value)
            else -> throw GlobalValues.typeError(function, param, "number", value)
        }
        if (d.isNaN() || d < 0) throw GlobalValues.rangeError(function, param, "non-negative number", value)
    }

}
