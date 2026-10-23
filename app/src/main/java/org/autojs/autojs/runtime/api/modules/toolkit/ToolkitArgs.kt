package org.autojs.autojs.runtime.api.modules.toolkit

import android.view.accessibility.AccessibilityEvent
import org.autojs.autojs.core.automator.filter.JsRegexUtils
import org.autojs.autojs.runtime.api.augment.automator.EventWaitArguments
import org.autojs.autojs.runtime.api.augment.toolkit.ToolkitArguments
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.flow.FlowArgs
import org.autojs.autojs.runtime.api.modules.flow.FlowOptions
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.regexp.NativeRegExp

/**
 * The argument checks of the automation toolkit on the new script module layer (roadmap P3.3, D10, appendix C Q65 (2)): the
 * tools (`smartClick`, `clickIfExists`, `clickAny`, `findAny`, `scrollUntil`, `typeInto`, `dismissPopups`,
 * `collectList`, `launchAndWait`, `backUntil`, `backToApp`, `toggle`, `retry`) as the synchronous globals of `automator`
 * and the roots of `flow`, and the event-driven waits (`waitForIdle`, `waitForEvent`, `waitForToast`,
 * `waitForNotification`). Checked here, in the order the current parsers read them: the count and the shape of the
 * positional arguments, the options object or its number shorthand and its fields ([FlowOptions]), the callbacks of the flow
 * forms, the kind of every target (located when the tool runs), the event types and the filters. The tools and the waits
 * themselves are the current ones (`augment/toolkit/RuntimeToolkit`, `augment/automator/EventWaitApi`); what they accept is
 * accepted here, what they refused with an IllegalArgumentException is a TypeError or a RangeError.
 *
 * zh-CN: 新脚本模块层上自动化工具集的参数检查 (P3.3, D10, 附录 C Q65 (2)): 各工具 (automator 的同步全局函数与 flow 的起点) 与事件驱动等待. 这里按
 * 现有解析器的读取顺序检查: 个数与位置参数的形态, 选项对象或其数值简写及其字段 (FlowOptions), flow 形式的回调, 各目标的种类 (在工具运行时定位),
 * 事件类型与过滤器. 工具与等待本身沿用现有实现 (augment/toolkit/RuntimeToolkit, augment/automator/EventWaitApi); 它们接受的这里同样接受,
 * 它们以 IllegalArgumentException 拒绝的这里为 TypeError 或 RangeError.
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

    /** The option keys of each tool and the field its number shorthand stands for (the current `ToolkitApi`). */
    private val OPTIONS = mapOf(
        "smartClick" to Options(ToolkitArguments.CLICK_KEYS),
        "smartClickBounds" to Options(ToolkitArguments.CLICK_BOUNDS_KEYS),
        "clickIfExists" to Options(ToolkitArguments.CLICK_KEYS, "timeout"),
        "clickBoundsIfExists" to Options(ToolkitArguments.CLICK_BOUNDS_KEYS, "timeout"),
        "clickAny" to Options(ToolkitArguments.CLICK_KEYS, "timeout"),
        "clickBoundsAny" to Options(ToolkitArguments.CLICK_BOUNDS_KEYS, "timeout"),
        "findAny" to Options(ToolkitArguments.FIND_KEYS, "timeout"),
        "scrollUntil" to Options(ToolkitArguments.SCROLL_KEYS),
        "typeInto" to Options(ToolkitArguments.TEXT_KEYS),
        "dismissPopups" to Options(ToolkitArguments.POPUP_KEYS, "timeout"),
        "collectList" to Options(ToolkitArguments.COLLECT_KEYS),
        "launchAndWait" to Options(ToolkitArguments.LAUNCH_KEYS, "timeout"),
        "backUntil" to Options(ToolkitArguments.BACK_KEYS, "maxTimes"),
        "backToApp" to Options(ToolkitArguments.BACK_KEYS, "maxTimes"),
        "toggle" to Options(ToolkitArguments.TOGGLE_KEYS),
        "retry" to Options(ToolkitArguments.RETRY_KEYS, "times"),
    )

    val TOOLS: Set<String> get() = POSITIONALS.keys

    /**
     * `tool(...positionals, options?, onOk?, onErr?)`: the required positionals, then an options object or a number (the
     * shorthand of the tool), then, for a flow root ([callbacks]), up to two functions; a synchronous tool takes nothing
     * after the options. Then the fields of the options and the kinds of the targets.
     */
    fun toolArgs(function: String, tool: String, args: Array<out Any?>, callbacks: Boolean) {
        val params = POSITIONALS.getValue(tool)
        params.forEachIndexed { i, param ->
            if (!param.endsWith('?')) ScriptArgs.nonNullish(args.getOrNull(i), function, param)
        }
        if (tool == "retry" && args[0] !is Callable) throw GlobalValues.typeError(function, "fn", "function", args[0])
        var index = params.size
        var options: Scriptable? = null
        var shorthand: Number? = null
        when (val slot = args.getOrNull(index)) {
            is Callable -> Unit
            is Number -> {
                if (slot.toDouble().isNaN()) throw GlobalValues.rangeError(function, "options", "number", slot)
                shorthand = slot
                index++
            }
            else -> {
                if (index < args.size) FlowArgs.optionsArg(function, "options", slot)
                options = slot.takeIf { FlowArgs.isOptions(it) } as Scriptable?
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
        fields(function, tool, args, options, shorthand, sync = !callbacks)
    }

    /** The fields of a tool's options, in the order the current `ToolkitApi` reads them, then the kinds of its targets. */
    private fun fields(function: String, tool: String, args: Array<out Any?>, options: Scriptable?, shorthand: Number?, sync: Boolean) {
        val spec = OPTIONS.getValue(tool)
        if (shorthand != null && spec.shorthandKey == null) throw GlobalValues.typeError(function, "options", "Object", shorthand)
        val o = FlowOptions(function, options, shorthand = shorthand, shorthandKey = spec.shorthandKey).keys(spec.keys)
        when (tool) {
            "smartClick", "smartClickBounds", "clickIfExists", "clickBoundsIfExists" -> {
                o.humanize()
                click(o)
                locate(o)
                o.root()
                FlowArgs.target(function, "target", args[0])
            }
            "clickAny", "clickBoundsAny" -> {
                val targets = targets(function, args[0]!!)
                o.humanize()
                click(o)
                locate(o)
                o.root()
                targetKinds(function, targets)
            }
            "findAny" -> {
                val targets = targets(function, args[0]!!)
                locate(o)
                o.root()
                targetKinds(function, targets)
            }
            "scrollUntil" -> {
                locate(o)
                scroll(o)
                o.humanize()
                o.root()
                FlowArgs.target(function, "target", args[0])
                o.target("container")
            }
            "typeInto" -> {
                locate(o)
                o.millis("verifyTimeout")
                o.millis("verifyInterval")
                o.humanize()
                o.root()
                args.getOrNull(0).takeUnless { isNullish(it) }?.let { FlowArgs.target(function, "target", it) }
            }
            "dismissPopups" -> {
                val targets = targets(function, args[0]!!)
                val watch = o.bool("watch") ?: false
                o.function("onDismissed")
                o.function("onError")
                if (watch && !sync) throw GlobalValues.cannotBe(function, "options.watch", "true for a flow (a flow form dismisses once; watch with the synchronous form)")
                if (!watch) listOf("onDismissed", "onError").firstOrNull { o.raw(it) != null }?.let {
                    throw GlobalValues.cannotBe(function, "options.$it", "given without options.watch")
                }
                locate(o)
                o.millis("settle")
                o.count("maxRounds", positive = true)
                nestedClick(o)
                o.millis("debounce")
                o.root()
                targetKinds(function, targets)
            }
            "collectList" -> {
                locate(o)
                o.function("until")
                o.named("direction", DIRECTIONS, ignoreCase = true)
                o.count("maxSteps")
                o.named("method", METHODS, ignoreCase = true)
                o.resultType()
                o.count("maxItems", positive = true)
                o.millis("settle")
                o.millis("gestureDuration")
                o.humanize()
                o.root()
                args.getOrNull(0).takeUnless { isNullish(it) }?.let { FlowArgs.target(function, "container", it) }
                FlowArgs.target(function, "item", args[1])
            }
            "launchAndWait" -> {
                o.millis("timeout")
                o.millis("interval")
            }
            "backUntil", "backToApp" -> {
                o.count("maxTimes")
                o.millis("delay")
                o.positiveMillis("interval")
                o.humanize()
                if (tool == "backUntil") o.root()
            }
            "toggle" -> {
                locate(o)
                o.millis("verifyTimeout")
                o.millis("verifyInterval")
                nestedClick(o)
                o.root()
                FlowArgs.target(function, "target", args[0])
            }
            "retry" -> {
                o.atLeastOne("backoff")
                o.count("times")
                o.millis("delay")
            }
        }
    }

    /** The flat smart click fields after `humanize` (read by the caller: the nested one falls back to the outer one). */
    private fun click(o: FlowOptions) {
        o.offset()
        o.count("maxClimb")
        o.verify()
        o.millis("verifyTimeout")
        o.millis("verifyInterval")
    }

    /** The nested `click` of `dismissPopups` / `toggle`: its own `humanize`, else the outer one. */
    private fun nestedClick(o: FlowOptions) {
        val inner = o.nested("click")?.keys(ToolkitArguments.CLICK_KEYS.sorted()) ?: return run { o.humanize() }
        if (inner.raw("humanize") != null) inner.humanize() else o.humanize()
        click(inner)
    }

    private fun locate(o: FlowOptions) {
        o.limit("timeout")
        o.millis("interval")
    }

    private fun scroll(o: FlowOptions) {
        o.named("direction", DIRECTIONS, ignoreCase = true)
        o.count("maxSteps")
        o.named("method", METHODS, ignoreCase = true)
        o.millis("settle")
        o.millis("gestureDuration")
    }

    /** The candidates of `clickAny` / `findAny` / `dismissPopups`: an array (not empty, no nullish entry), or one target. */
    private fun targets(function: String, value: Any): List<Pair<String, Any?>> {
        if (value !is NativeArray) return listOf("targets" to value)
        // As the current parser reads them (`List.get`: wrapped values unwrapped, holes and undefined as null).
        val list = value as List<*>
        if (list.isEmpty()) throw GlobalValues.cannotBe(function, "targets", "an empty array")
        return list.mapIndexed { i, t ->
            if (isNullish(t)) throw GlobalValues.cannotBe(function, "targets[$i]", ScriptArgs.describe(t))
            "targets[$i]" to t
        }
    }

    private fun targetKinds(function: String, targets: List<Pair<String, Any?>>) = targets.forEach { (param, t) -> FlowArgs.target(function, param, t) }

    /** `waitForIdle(quietFor?)` / `waitForIdle({ quietFor?, timeout?, eventTypes? })`. */
    fun idleArgs(function: String, args: Array<out Any?>) {
        atMost(function, args, 1)
        when (val a = args.getOrNull(0)) {
            null, is org.mozilla.javascript.Undefined -> Unit
            is Number -> millis(function, "quietFor", a)
            is NativeObject -> {
                val o = FlowOptions(function, a).keys(EventWaitArguments.IDLE_KEYS)
                o.raw(EventWaitArguments.KEY_QUIET_FOR)?.let { millis(function, "options.quietFor", it) }
                o.raw(EventWaitArguments.KEY_TIMEOUT)?.let { millis(function, "options.timeout", it) }
                o.raw(EventWaitArguments.KEY_EVENT_TYPES)?.let { resolve(function, typeNames(function, "options.eventTypes", it)) }
            }
            else -> throw GlobalValues.typeError(function, "options", "number | Object", a)
        }
    }

    /** `waitForEvent(type?, filter?, timeout?)`: a type name or an array of them, a function or an object, a number of milliseconds. */
    fun eventArgs(function: String, args: Array<out Any?>) {
        atMost(function, args, 3)
        val names = typeNames(function, "type", args.getOrNull(0))
        var filter = false
        var timeout = false
        for (i in 1 until args.size) {
            val a = args[i]
            when {
                isNullish(a) -> Unit
                a is Number && !timeout -> millis(function, "timeout", a).also { timeout = true }
                a !is Number && !filter -> filter(function, "filter", a, EventWaitArguments.EVENT_FILTER_KEYS, "function | Object").also { filter = true }
                else -> {
                    val expected = listOfNotNull("number".takeUnless { timeout }, "function | Object".takeUnless { filter }).joinToString(" | ")
                    throw GlobalValues.typeError(function, "argument $i", expected.ifEmpty { "undefined" }, a)
                }
            }
        }
        resolve(function, names)
    }

    /** `waitForToast(filter?, timeout?)`, or `(timeout)` alone: a substring, a regular expression, a function or `{ packageName?, text? }`. */
    fun toastArgs(function: String, args: Array<out Any?>) = textAndTimeoutArgs(function, args, EventWaitArguments.TOAST_FILTER_KEYS)

    /** `waitForNotification(filter?, timeout?)`, or `(timeout)` alone: as [toastArgs], the object `{ packageName?, title?, text? }`. */
    fun notificationArgs(function: String, args: Array<out Any?>) = textAndTimeoutArgs(function, args, EventWaitArguments.NOTIFICATION_FILTER_KEYS)

    private fun textAndTimeoutArgs(function: String, args: Array<out Any?>, keys: Set<String>) {
        atMost(function, args, 2)
        val first = args.getOrNull(0)
        if (first is Number) {
            atMost(function, args, 1)
            millis(function, "timeout", first)
            return
        }
        when {
            isNullish(first) || first is CharSequence -> Unit
            first is NativeRegExp -> regex(function, "filter", first)
            else -> filter(function, "filter", first, keys, "string | RegExp | function | Object")
        }
        args.getOrNull(1).takeUnless { isNullish(it) }?.let { millis(function, "timeout", it) }
    }

    /** A filter of the current parser: a function, or an object of [keys] whose values are strings or regular expressions. */
    private fun filter(function: String, param: String, value: Any?, keys: Set<String>, expected: String) {
        when (value) {
            is Callable -> Unit
            is NativeObject -> {
                val o = FlowOptions(function, value, param).keys(keys)
                for (key in keys) {
                    when (val v = o.raw(key)) {
                        null, is CharSequence -> Unit
                        is NativeRegExp -> regex(function, "$param.$key", v)
                        else -> throw GlobalValues.typeError(function, "$param.$key", "string | RegExp", v)
                    }
                }
            }
            else -> throw GlobalValues.typeError(function, param, expected, value)
        }
    }

    /** A regular expression the current parser compiles for `java.util.regex` (a JavaScript-only construct cannot be). */
    private fun regex(function: String, param: String, value: NativeRegExp) {
        try {
            JsRegexUtils.compileJsOrPlainRegex(value.toString())
        } catch (_: Exception) {
            throw GlobalValues.rangeError(function, param, "regular expression supported by java.util.regex", value)
        }
    }

    /** Event type names (`windowStateChanged`, `window_state_changed`, `TYPE_WINDOW_STATE_CHANGED`), or null for any. */
    private fun typeNames(function: String, param: String, value: Any?): List<Pair<String, Any>>? = when {
        isNullish(value) -> null
        value is CharSequence -> value.toString().trim().let { if (it == EventWaitArguments.ANY_TYPE) null else listOf(param to value) }
        value is NativeArray -> (value as List<*>).mapIndexed { i, name ->
            if (name !is CharSequence) throw GlobalValues.typeError(function, "$param[$i]", "string", name)
            "$param[$i]" to name
        }
        else -> throw GlobalValues.typeError(function, param, "string | Array", value)
    }

    /** Each name is an `AccessibilityEvent.TYPE_*` constant (looked up as `AccessibilityService.eventTypeOf` does for the current wait). */
    private fun resolve(function: String, names: List<Pair<String, Any>>?) = names?.forEach { (param, name) ->
        try {
            AccessibilityEvent::class.java.getField("TYPE_${EventWaitArguments.normalizeTypeName(name.toString())}")
        } catch (_: NoSuchFieldException) {
            throw GlobalValues.rangeError(function, param, "event type name", name)
        }
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

    private class Options(keys: Set<String>, val shorthandKey: String? = null) {
        val keys: List<String> = keys.sorted()
    }

    private val DIRECTIONS = listOf("up", "down", "left", "right")

    private val METHODS = listOf("action", "gesture")

}
