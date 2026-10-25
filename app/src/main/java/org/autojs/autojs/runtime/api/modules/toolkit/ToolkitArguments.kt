package org.autojs.autojs.runtime.api.modules.toolkit

import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.flow.FlowScope
import org.autojs.autojs.core.automator.query.PollPolicy
import org.autojs.autojs.core.automator.toolkit.AppLauncher
import org.autojs.autojs.core.automator.toolkit.Humanize
import org.autojs.autojs.core.automator.toolkit.ListCollector
import org.autojs.autojs.core.automator.toolkit.PopupDismisser
import org.autojs.autojs.core.automator.toolkit.ScrollDirection
import org.autojs.autojs.core.automator.toolkit.ScrollMethod
import org.autojs.autojs.core.automator.toolkit.ScrollSearch
import org.autojs.autojs.core.automator.toolkit.SmartClick
import org.autojs.autojs.core.automator.toolkit.TextEntry
import org.autojs.autojs.core.automator.toolkit.Toggle
import org.autojs.autojs.core.automator.toolkit.Toolkit
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsArray
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.api.modules.flow.FlowCondition
import org.autojs.autojs.runtime.api.modules.flow.FlowDefaults
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import kotlin.math.roundToLong

/**
 * Turns the JavaScript arguments of the toolkit surface (roadmap P4.2, Appendix B) into the
 * option classes of the core tools. Every call is `(positionals..., options?, onOk?, onErr?)`:
 * a fixed number of positionals per tool, then an options object (or, for a few tools, a bare
 * number standing for one option, such as `findAny(sels, timeout)`), then, on the Flow forms only,
 * up to two trailing callbacks. Option keys are checked against the tool's known set, so a typo
 * is an error rather than a silent default. The `humanize` option (`{ offset, delay }`, `false`
 * to disable) falls back to `flow.defaults({ humanize })`.
 * zh-CN: 把工具集表面 (Roadmap P4.2, 附录 B) 的 JavaScript 参数转换为核心工具的选项类. 每个调用形如 `(位置参数..., options?, onOk?,
 * onErr?)`: 每个工具固定个数的位置参数, 然后是选项对象 (少数工具也接受一个裸数字代表某个选项, 如 `findAny(sels, timeout)`), 然后
 * (仅 Flow 形式) 至多两个尾随回调. 选项键按工具的已知集合检查, 拼写错误报错而非静默取默认值. `humanize` 选项 (`{ offset, delay }`,
 * `false` 为禁用) 缺省时取 `flow.defaults({ humanize })`.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
internal class ToolkitArguments(
    private val defaults: FlowDefaults,
    /** How a script function is called with arguments (the `until` predicate). zh-CN: 如何以参数调用脚本函数 (`until` 断言). */
    private val invoke: (Callable, List<Any?>) -> Any?,
) {

    /** One call, split. zh-CN: 拆分后的一次调用. */
    class Call(
        val name: String,
        val positionals: List<Any?>,
        val options: ScriptableObject?,
        /** The bare number given in place of the options object. zh-CN: 代替选项对象给出的裸数字. */
        val shorthand: Double?,
        val onOk: Callable?,
        val onErr: Callable?,
    ) {

        /** Positional [index], `null` when missing or nullish. zh-CN: 第 [index] 个位置参数, 缺失或为空值时 `null`. */
        fun positional(index: Int): Any? = positionals.getOrNull(index).takeUnless { it.isJsNullish() }

        fun require(index: Int, what: String): Any = positional(index) ?: throw WrappedIllegalArgumentException("Argument \"$what\" is required for $name")

    }

    /**
     * `(p0, ..., p[n-1], options | number?, onOk?, onErr?)`: the first [positional] arguments are the
     * positionals (missing ones are `null`; a function in a positional slot is fine, e.g. the
     * condition of `backUntil`), then an optional options object or a bare number, then, when
     * [callbacks], up to two trailing functions; anything else is an error.
     * zh-CN: 见类注释: 前 [positional] 个参数为位置参数 (缺失为 `null`; 位置参数槽里的函数是正常的, 如 `backUntil` 的条件), 然后是可选的
     * 选项对象或裸数字, 然后 ([callbacks] 时) 至多两个尾随函数; 其余报错.
     */
    fun split(name: String, args: Array<out Any?>, positional: Int, callbacks: Boolean): Call {
        val positionals = List(positional) { args.getOrNull(it) }
        var index = positional
        var options: ScriptableObject? = null
        var shorthand: Double? = null
        val slot = args.getOrNull(index)
        when {
            slot == null && index < args.size -> index += 1
            slot == null -> Unit
            slot.isJsNullish() -> index += 1
            slot is Callable -> Unit
            slot is Number -> {
                shorthand = number(slot, "argument $index", name)
                index += 1
            }
            slot is ScriptableObject && !slot.isJsArray() -> {
                options = slot
                index += 1
            }
            else -> throw WrappedIllegalArgumentException("Argument ${slot.jsBrief()} at index $index must be an options object for $name")
        }
        val rest = args.drop(index)
        require(callbacks || rest.none { !it.isJsNullish() }) { "Too many arguments (${args.size}) for $name" }
        val fns = ArrayList<Callable>(2)
        rest.forEachIndexed { i, a ->
            when {
                a.isJsNullish() -> Unit
                a is Callable -> fns += a
                else -> throw WrappedIllegalArgumentException("Argument ${a.jsBrief()} at index ${index + i} must be a function for $name")
            }
        }
        require(fns.size <= 2) { "Too many callbacks (${fns.size}) for $name" }
        return Call(name, positionals, options, shorthand, fns.getOrNull(0), fns.getOrNull(1))
    }

    /** Typed reads of the options of [call], restricted to [keys]; a bare number stands for [shorthandKey]. zh-CN: 对 [call] 选项的类型化读取, 限于 [keys]; 裸数字代表 [shorthandKey]. */
    inner class Reader(val call: Call, private val keys: Set<String>, private val shorthandKey: String? = null) {

        val name: String get() = call.name

        init {
            call.options?.ids?.forEach { key ->
                require(key.toString() in keys) { "Unknown option \"$key\" for $name; expected one of ${keys.sorted().joinToString(", ")}" }
            }
            require(call.shorthand == null || shorthandKey != null) { "A number is not accepted in place of the options object for $name" }
        }

        fun raw(key: String): Any? = call.options?.prop(key).takeUnless { it.isJsNullish() } ?: call.shorthand?.takeIf { key == shorthandKey }

        fun number(key: String): Double? = raw(key)?.let { number(it, key, name) }

        fun millis(key: String, default: Long): Long = number(key)?.let { nonNegativeMillis(it, key, name) } ?: default

        fun count(key: String, default: Int): Int = number(key)?.let {
            require(it.isFinite() && it >= 0 && it == Math.floor(it)) { "Option \"$key\" ($it) must be a non-negative integer for $name" }
            require(it <= Int.MAX_VALUE) { "Option \"$key\" ($it) is too large for $name" }
            it.toInt()
        } ?: default

        fun bool(key: String, default: Boolean): Boolean = raw(key)?.let { Context.toBoolean(it) } ?: default

        fun fn(key: String): Callable? = raw(key)?.let { it as? Callable ?: throw WrappedIllegalArgumentException("Option \"$key\" must be a function for $name, got ${it.jsBrief()}") }

        /** `timeout` / `interval` as a [Toolkit.Locate]; [defaultTimeout] applies without `timeout` (`Infinity` means no limit). zh-CN: `timeout` / `interval` 作为 [Toolkit.Locate]; 缺省 `timeout` 时取 [defaultTimeout] (`Infinity` 为不限). */
        fun locate(defaultTimeout: Long): Toolkit.Locate {
            val timeout = number("timeout")?.let { unboundedOr(it, "timeout", name) } ?: defaultTimeout
            val interval = millis("interval", PollPolicy.DEFAULT_INTERVAL)
            return Toolkit.Locate(if (timeout == PollPolicy.UNBOUNDED) Long.MAX_VALUE else timeout, interval)
        }

        fun root(): UiObject? = raw("root")?.let {
            RhinoUtils.unwrap(it) as? UiObject ?: throw WrappedIllegalArgumentException("Option \"root\" must be a UiObject for $name, got ${it.jsBrief()}")
        }

        fun humanize(): Humanize = raw("humanize")?.let { humanize(it, name, defaults.humanize) } ?: defaults.humanize

        fun direction(): ScrollDirection = raw("direction")?.let {
            ScrollDirection.of((it as? CharSequence)?.toString()) ?: throw WrappedIllegalArgumentException("Option \"direction\" must be \"up\", \"down\", \"left\" or \"right\" for $name, got ${it.jsBrief()}")
        } ?: ScrollDirection.DOWN

        fun method(): ScrollMethod = raw("method")?.let {
            ScrollMethod.of((it as? CharSequence)?.toString()) ?: throw WrappedIllegalArgumentException("Option \"method\" must be \"action\" or \"gesture\" for $name, got ${it.jsBrief()}")
        } ?: ScrollMethod.ACTION

        /** A condition value for `verify`: a selector form, a node or a function, kept as given. zh-CN: `verify` 的条件值: 选择器形式, 节点或函数, 原样保留. */
        fun condition(key: String): Any? = raw(key)?.also {
            val v = RhinoUtils.unwrap(it)
            require(v is UiObject || v is Callable || FlowCondition.isSelectorForm(v)) { "Option \"$key\" must be a selector, a UiObject or a function for $name, got ${it.jsBrief()}" }
        }

        /** `resultType` for the pickup of collected items: a string or an array. zh-CN: 采集项拾取的 `resultType`: 字符串或数组. */
        fun resultType(): Any? = raw("resultType")?.let {
            when (it) {
                is CharSequence -> it.toString()
                is List<*> -> it
                else -> throw WrappedIllegalArgumentException("Option \"resultType\" must be a string or an array for $name, got ${it.jsBrief()}")
            }
        } ?: ListCollector.Options.DEFAULT_RESULT_TYPE

        /** The `offset` of a smart click: one number for both axes or `[dx, dy]`. zh-CN: 智能点击的 `offset`: 一个数字 (两轴同值) 或 `[dx, dy]`. */
        fun offset(): Pair<Int, Int> = raw("offset")?.let { pair(it, "offset", name) { v -> signedInteger(v, "offset", name) } } ?: (0 to 0)

    }

    fun reader(call: Call, keys: Set<String>, shorthandKey: String? = null): Reader = Reader(call, keys, shorthandKey)

    // Tool options. zh-CN: 各工具的选项.

    /** The flat smart click keys of [r] (also used by `clickIfExists` / `clickAny`). zh-CN: [r] 中扁平的智能点击键 (`clickIfExists` / `clickAny` 亦用). */
    fun smartClick(r: Reader, humanize: Humanize = r.humanize()): SmartClick.Options {
        val (offsetX, offsetY) = r.offset()
        return SmartClick.Options(
            climb = r.bool("climb", true),
            maxClimb = r.count("maxClimb", SmartClick.Options.DEFAULT_MAX_CLIMB),
            gestureFallback = r.bool("gestureFallback", true),
            verify = r.condition("verify"),
            verifyTimeout = r.millis("verifyTimeout", SmartClick.Options.DEFAULT_VERIFY_TIMEOUT),
            verifyInterval = r.millis("verifyInterval", SmartClick.Options.DEFAULT_VERIFY_INTERVAL),
            offsetX = offsetX,
            offsetY = offsetY,
            humanize = humanize,
        )
    }

    /** The nested `click` options object of `dismissPopups` / `toggle`; its own `humanize` overrides the outer one. zh-CN: `dismissPopups` / `toggle` 嵌套的 `click` 选项对象; 其自身的 `humanize` 覆盖外层的. */
    fun nestedClick(r: Reader): SmartClick.Options {
        val nested = r.raw("click") ?: return SmartClick.Options(humanize = r.humanize())
        require(nested is ScriptableObject && !nested.isJsArray() && nested !is Callable) { "Option \"click\" must be an object for ${r.name}, got ${nested.jsBrief()}" }
        val inner = Reader(Call("${r.name}.click", emptyList(), nested, null, null, null), CLICK_KEYS)
        return smartClick(inner, inner.raw("humanize")?.let { humanize(it, inner.name, defaults.humanize) } ?: r.humanize())
    }

    fun scrollSearch(r: Reader, container: Any?, locate: Toolkit.Locate): ScrollSearch.Options = ScrollSearch.Options(
        container = container,
        direction = r.direction(),
        maxSteps = r.count("maxSteps", ScrollSearch.Options.DEFAULT_MAX_STEPS),
        method = r.method(),
        stopAtEnd = r.bool("stopAtEnd", true),
        settleMillis = r.millis("settle", ScrollSearch.Options.DEFAULT_SETTLE_MILLIS),
        gestureDuration = r.millis("gestureDuration", ScrollSearch.Options.DEFAULT_GESTURE_DURATION),
        locate = locate,
        humanize = r.humanize(),
    )

    fun textEntry(r: Reader, locate: Toolkit.Locate): TextEntry.Options = TextEntry.Options(
        clear = r.bool("clear", true),
        submit = r.bool("submit", false),
        verify = r.bool("verify", true),
        verifyTimeout = r.millis("verifyTimeout", TextEntry.Options.DEFAULT_VERIFY_TIMEOUT),
        verifyInterval = r.millis("verifyInterval", TextEntry.Options.DEFAULT_VERIFY_INTERVAL),
        locate = locate,
        humanize = r.humanize(),
    )

    fun popups(r: Reader, locate: Toolkit.Locate): PopupDismisser.Options = PopupDismisser.Options(
        locate = locate,
        settleMillis = r.millis("settle", PopupDismisser.Options.DEFAULT_SETTLE_MILLIS),
        maxRounds = r.count("maxRounds", PopupDismisser.Options.DEFAULT_MAX_ROUNDS).also { require(it > 0) { "Option \"maxRounds\" must be positive for ${r.name}" } },
        click = nestedClick(r),
        debounceMillis = r.millis("debounce", PopupDismisser.Options.DEFAULT_DEBOUNCE_MILLIS),
    )

    fun listCollector(r: Reader, locate: Toolkit.Locate): ListCollector.Options {
        val until = r.fn("until")
        return ListCollector.Options(
            direction = r.direction(),
            maxSteps = r.count("maxSteps", ListCollector.Options.DEFAULT_MAX_STEPS),
            method = r.method(),
            resultType = r.resultType(),
            dedupe = r.bool("dedupe", true),
            until = until?.let { fn -> { value -> Context.toBoolean(invoke(fn, listOf(value))) } },
            maxItems = r.count("maxItems", Int.MAX_VALUE).also { require(it > 0) { "Option \"maxItems\" must be positive for ${r.name}" } },
            settleMillis = r.millis("settle", ListCollector.Options.DEFAULT_SETTLE_MILLIS),
            gestureDuration = r.millis("gestureDuration", ListCollector.Options.DEFAULT_GESTURE_DURATION),
            locate = locate,
            humanize = r.humanize(),
        )
    }

    fun launch(r: Reader): AppLauncher.LaunchOptions = AppLauncher.LaunchOptions(
        timeout = r.millis("timeout", AppLauncher.LaunchOptions.DEFAULT_TIMEOUT),
        interval = r.millis("interval", AppLauncher.LaunchOptions.DEFAULT_INTERVAL),
        bringToFront = r.bool("bringToFront", true),
    )

    fun back(r: Reader): AppLauncher.BackOptions = AppLauncher.BackOptions(
        maxTimes = r.count("maxTimes", AppLauncher.BackOptions.DEFAULT_MAX_TIMES),
        delay = r.millis("delay", AppLauncher.BackOptions.DEFAULT_DELAY),
        interval = r.millis("interval", AppLauncher.BackOptions.DEFAULT_INTERVAL).also { require(it > 0) { "Option \"interval\" must be positive for ${r.name}" } },
        humanize = r.humanize(),
    )

    fun toggle(r: Reader, locate: Toolkit.Locate): Toggle.Options = Toggle.Options(
        locate = locate,
        verifyTimeout = r.millis("verifyTimeout", Toggle.Options.DEFAULT_VERIFY_TIMEOUT),
        verifyInterval = r.millis("verifyInterval", Toggle.Options.DEFAULT_VERIFY_INTERVAL),
        click = nestedClick(r),
    )

    fun retry(r: Reader): Toolkit.RetryOptions {
        val backoff = r.number("backoff") ?: 1.0
        require(backoff.isFinite() && backoff >= 1.0) { "Option \"backoff\" ($backoff) must be at least 1 for ${r.name}" }
        return Toolkit.RetryOptions(
            times = r.count("times", 3),
            delay = r.millis("delay", 500),
            backoff = backoff,
        )
    }

    // Values. zh-CN: 值.

    /** The candidates of `findAny` / `clickAny` / `dismissPopups`: an array, or one target. zh-CN: `findAny` / `clickAny` / `dismissPopups` 的候选项: 数组, 或单个目标. */
    fun targets(value: Any, name: String): List<Any?> {
        val list = if (value.isJsArray()) (value as List<*>).toList() else listOf(value)
        require(list.isNotEmpty()) { "At least one target is required for $name" }
        list.forEachIndexed { i, t -> require(!t.isJsNullish()) { "Target at index $i cannot be null or undefined for $name" } }
        return list
    }

    /** The scope root of a step: the option, else the chain scope's. zh-CN: 步骤的作用域根: 选项, 否则链作用域的. */
    fun root(r: Reader, scope: FlowScope): UiObject? = r.root() ?: scope.root as? UiObject

    companion object {

        private val COMMON_KEYS = setOf("timeout", "interval", "root", "humanize")

        /** The flat smart click keys. zh-CN: 扁平的智能点击键. */
        val CLICK_KEYS = COMMON_KEYS + setOf("climb", "maxClimb", "gestureFallback", "verify", "verifyTimeout", "verifyInterval", "offset")

        /** Direct coordinate clicks have no node/ancestor fallback policy. zh-CN: 直接坐标点击不接受节点或祖先回退策略. */
        val CLICK_BOUNDS_KEYS = CLICK_KEYS - setOf("climb", "maxClimb", "gestureFallback")

        val FIND_KEYS = COMMON_KEYS

        val SCROLL_KEYS = COMMON_KEYS + setOf("container", "direction", "maxSteps", "method", "stopAtEnd", "settle", "gestureDuration")

        val TEXT_KEYS = COMMON_KEYS + setOf("clear", "submit", "verify", "verifyTimeout", "verifyInterval")

        val POPUP_KEYS = COMMON_KEYS + setOf("once", "watch", "settle", "maxRounds", "debounce", "click", "onDismissed", "onError")

        val COLLECT_KEYS = COMMON_KEYS + setOf("direction", "maxSteps", "method", "resultType", "dedupe", "until", "maxItems", "settle", "gestureDuration")

        val LAUNCH_KEYS = setOf("timeout", "interval", "bringToFront")

        val BACK_KEYS = setOf("root", "humanize", "maxTimes", "delay", "interval")

        val TOGGLE_KEYS = COMMON_KEYS + setOf("verifyTimeout", "verifyInterval", "click")

        val RETRY_KEYS = setOf("times", "delay", "backoff")

        /**
         * A `humanize` value: `false` / `null` for none, `true` for the flow defaults' one, or
         * `{ offset: n | [dx, dy], delay: n | [min, max] }` (pixels and milliseconds, non-negative).
         * zh-CN: `humanize` 值: `false` / `null` 为无, `true` 为 flow 默认值中的那个, 或 `{ offset: n | [dx, dy], delay: n | [min, max] }`
         * (像素与毫秒, 非负).
         */
        fun humanize(value: Any, name: String, fallback: Humanize = Humanize.NONE): Humanize = when {
            value == false -> Humanize.NONE
            value == true -> fallback
            value is ScriptableObject && !value.isJsArray() && value !is Callable -> {
                value.ids.forEach { key -> require(key.toString() in HUMANIZE_KEYS) { "Unknown key \"$key\" in \"humanize\" for $name; expected offset, delay" } }
                val offset = value.prop("offset").takeUnless { it.isJsNullish() }?.let { pair(it, "humanize.offset", name) { v -> integer(v, "humanize.offset", name) } } ?: (0 to 0)
                val delay = value.prop("delay").takeUnless { it.isJsNullish() }?.let { pair(it, "humanize.delay", name) { v -> nonNegativeMillis(number(v, "humanize.delay", name), "humanize.delay", name) } } ?: (0L to 0L)
                require(delay.second >= delay.first) { "The maximum of \"humanize.delay\" (${delay.second}) must be at least the minimum (${delay.first}) for $name" }
                Humanize(offsetX = offset.first, offsetY = offset.second, minDelay = delay.first, maxDelay = delay.second)
            }
            else -> throw WrappedIllegalArgumentException("Option \"humanize\" must be an object, true or false for $name, got ${value.jsBrief()}")
        }

        private val HUMANIZE_KEYS = setOf("offset", "delay")

        /** One value for both members, or an array of two. zh-CN: 一个值 (两个成员同值), 或两个值的数组. */
        private fun <T> pair(value: Any, what: String, name: String, convert: (Any?) -> T): Pair<T, T> = when {
            value.isJsArray() -> {
                val list = value as List<*>
                require(list.size == 2) { "Option \"$what\" must be a number or an array of two numbers for $name, got ${value.jsBrief()}" }
                convert(list[0]) to convert(list[1])
            }
            else -> convert(value).let { it to it }
        }

        private fun integer(value: Any?, what: String, name: String): Int {
            val n = number(value, what, name)
            require(n.isFinite() && n >= 0 && n == Math.floor(n)) { "Value ${value.jsBrief()} of \"$what\" must be a non-negative integer for $name" }
            require(n <= Int.MAX_VALUE) { "Value ${value.jsBrief()} of \"$what\" is too large for $name" }
            return n.toInt()
        }

        private fun signedInteger(value: Any?, what: String, name: String): Int {
            val n = number(value, what, name)
            require(n.isFinite() && n >= Int.MIN_VALUE && n <= Int.MAX_VALUE && n == Math.floor(n)) {
                "Value ${value.jsBrief()} of \"$what\" must be a signed 32-bit integer for $name"
            }
            return n.toInt()
        }

        private fun number(value: Any?, what: String, name: String): Double {
            val n = Context.toNumber(value)
            require(!n.isNaN()) { "Value ${value.jsBrief()} of \"$what\" is not a number for $name" }
            return n
        }

        private fun nonNegativeMillis(value: Double, what: String, name: String): Long {
            require(value.isFinite()) { "Value \"$what\" cannot be infinite for $name" }
            require(value >= 0) { "Value \"$what\" ($value) cannot be negative for $name" }
            return value.roundToLong()
        }

        private fun unboundedOr(value: Double, what: String, name: String): Long = when {
            value.isInfinite() && value > 0 -> PollPolicy.UNBOUNDED
            else -> nonNegativeMillis(value, what, name)
        }

    }

}
