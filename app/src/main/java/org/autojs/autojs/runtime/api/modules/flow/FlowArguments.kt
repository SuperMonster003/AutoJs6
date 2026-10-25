package org.autojs.autojs.runtime.api.modules.flow

import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.flow.FlowNode
import org.autojs.autojs.core.automator.flow.FlowScope
import org.autojs.autojs.core.automator.flow.FlowStep
import org.autojs.autojs.core.automator.flow.MissingPolicy
import org.autojs.autojs.core.automator.flow.Stability
import org.autojs.autojs.core.automator.flow.StabilityCompare
import org.autojs.autojs.core.automator.query.PollPolicy
import org.autojs.autojs.core.automator.toolkit.Humanize
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsArray
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.api.modules.global.Global
import org.autojs.autojs.runtime.api.modules.toolkit.ToolkitArguments
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import kotlin.math.roundToLong

/**
 * The per-script defaults of the Flow API (`flow.defaults`, roadmap P3.3), read whenever a step is
 * created. zh-CN: Flow API 每脚本一份的默认值 (`flow.defaults`, Roadmap P3.3), 在创建步骤时读取.
 */
internal class FlowDefaults {

    @Volatile
    var timeout: Long = DEFAULT_TIMEOUT

    @Volatile
    var interval: Long = DEFAULT_INTERVAL

    @Volatile
    var stableFor: Long = Stability.DEFAULT_STABLE_FOR

    /** D12: an action reporting `false` rejects `ACTION_FAILED`. zh-CN: D12: 动作返回 `false` 时以 `ACTION_FAILED` 拒绝. */
    @Volatile
    var strictActions: Boolean = true

    /** P4.2: the pause before every action step and the default `humanize` of the toolkit. zh-CN: P4.2: 每个动作步骤前的暂停, 以及工具集 `humanize` 的默认值. */
    @Volatile
    var humanize: Humanize = Humanize.NONE

    /** P5.5: waits keep waiting through an accessibility service outage. zh-CN: P5.5: 等待在无障碍服务中断期间继续等待. */
    @Volatile
    var recoverService: Boolean = false

    companion object {
        const val DEFAULT_TIMEOUT = 10_000L
        const val DEFAULT_INTERVAL = 200L
    }

}

/** Everything a wait step is built from. zh-CN: 构造一个等待步骤所需的一切. */
internal class WaitSpec(
    val cond: FlowCondition,
    val policy: PollPolicy,
    val stability: Stability,
    val strict: Boolean,
    /** Roadmap P5.5: ride out an accessibility service outage. zh-CN: Roadmap P5.5: 度过无障碍服务的中断. */
    val recoverService: Boolean,
    /** The `delay` of the `*After` forms. zh-CN: `*After` 形式的 `delay`. */
    val delay: FlowStep.Sleep?,
    val onOk: Callable?,
    val onErr: Callable?,
)

/**
 * Turns the JavaScript arguments of the Flow API into steps (roadmap Appendix A.2): positional
 * `(cond, timeout?, interval?)` or `(cond, options)`, with trailing functions as `onOk` / `onErr`
 * and, for the `*After` forms, further numbers as the delay; the options object carries `timeout`,
 * `interval`, `times`, `root`, `compass`, `resultType`, `stableFor`, `compare`, `missing`,
 * `strict`, `delay` and `recoverService` (P5.5). The `limit < 100` rule of the synchronous `wait` is not carried over: a
 * number of attempts is only ever the `times` option.
 * zh-CN: 把 Flow API 的 JavaScript 参数转换为步骤 (Roadmap 附录 A.2): 位置参数 `(cond, timeout?, interval?)` 或 `(cond, options)`,
 * 尾随的函数为 `onOk` / `onErr`, `*After` 形式再以后续数字为延迟; 选项对象携带 `timeout`, `interval`, `times`, `root`, `compass`,
 * `resultType`, `stableFor`, `compare`, `missing`, `strict`, `delay` 与 `recoverService` (P5.5). 同步 `wait` 的 `limit < 100` 规则不沿用: 尝试次数只来自
 * `times` 选项.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
internal class FlowArguments(private val defaults: FlowDefaults, private val conditions: FlowConditions) {

    /** How a condition value becomes a [FlowCondition] (the executor hooks thenables). zh-CN: 条件值如何成为 [FlowCondition] (由执行器挂接 thenable). */
    fun interface FlowConditions {
        fun of(value: Any?, name: String, scope: FlowScope): FlowCondition
    }

    private class Parsed(val options: ScriptableObject?, val numbers: List<Double>, val callbacks: List<Callable>)

    /** `(cond, timeout?, interval?, [delayMin?, delayMax?,] onOk?, onErr?)` or `(cond, options, onOk?, onErr?)`. zh-CN: 见类注释. */
    fun wait(name: String, args: Array<out Any?>, withDelay: Boolean = false): WaitSpec {
        require(args.isNotEmpty() && !args[0].isJsNullish()) { "Argument \"cond\" is required for $name" }
        val parsed = parse(name, args, 1, maxNumbers = if (withDelay) 4 else 2)
        return build(name, args[0], parsed, withDelay)
    }

    /** `waitForActivity(name, ...)` / `waitForPackage(name, ...)`: a [FlowCondition.Native] over [probe]. zh-CN: 基于 [probe] 的 [FlowCondition.Native]. */
    fun waitNative(name: String, args: Array<out Any?>, probe: (String) -> Any?): WaitSpec {
        require(args.isNotEmpty() && args[0] is CharSequence) { "Argument \"name\" must be a string for $name, got ${args.getOrNull(0).jsBrief()}" }
        val expected = args[0].toString()
        val parsed = parse(name, args, 1, maxNumbers = 2)
        val cond = FlowCondition.Native("$name(\"$expected\")") { probe(expected) }
        return build(name, cond, parsed, withDelay = false)
    }

    private fun build(name: String, cond: Any?, parsed: Parsed, withDelay: Boolean): WaitSpec {
        val options = parsed.options
        val numbers = parsed.numbers
        val timeout = numbers.getOrNull(0) ?: options?.number("timeout", name)
        val interval = numbers.getOrNull(1) ?: options?.number("interval", name)
        val times = options?.number("times", name)
        val policy = policy(name, timeout, interval, times)
        val snapshot = options?.prop("snapshot").takeUnless { it.isJsNullish() }
        require(snapshot == null || snapshot is Callable) { "Option \"snapshot\" must be a function for $name" }
        require(snapshot == null || options?.prop("compare").isJsNullish()) { "Options \"snapshot\" and \"compare\" are mutually exclusive for $name" }
        val stability = Stability(
            stableFor = options?.number("stableFor", name)?.let { nonNegativeMillis(it, "stableFor", name) } ?: defaults.stableFor,
            compare = options?.prop("compare").takeUnless { it.isJsNullish() }?.let { compare(it, name) } ?: StabilityCompare.Fingerprint,
            missing = options?.prop("missing").takeUnless { it.isJsNullish() }?.let { missing(it, name) } ?: MissingPolicy.RESET,
            snapshot = snapshot,
        )
        val strict = options?.prop("strict").takeUnless { it.isJsNullish() }?.let { Context.toBoolean(it) } ?: defaults.strictActions
        val recoverService = options?.prop("recoverService").takeUnless { it.isJsNullish() }?.let { Context.toBoolean(it) } ?: defaults.recoverService
        val delay = when {
            withDelay && numbers.size > 2 -> sleep(name, numbers.drop(2).toTypedArray<Any?>())
            else -> options?.prop("delay").takeUnless { it.isJsNullish() }?.let { delay(it, name) }
        }
        val scope = options?.let { scope(it, name) } ?: FlowScope.NONE
        val condition = cond as? FlowCondition ?: conditions.of(cond, name, scope)
        return WaitSpec(condition, policy, stability, strict, recoverService, delay, parsed.callbacks.getOrNull(0), parsed.callbacks.getOrNull(1))
    }

    /**
     * The polling plan: `timeout` (default 10000, `Infinity` for none, 0 for a single attempt),
     * `interval` (default 200) and `times` (default none, `Infinity` for none).
     * zh-CN: 轮询计划: `timeout` (默认 10000, `Infinity` 为不限, 0 为单次尝试), `interval` (默认 200) 与 `times` (默认不限,
     * `Infinity` 为不限).
     */
    fun policy(name: String, timeout: Double?, interval: Double?, times: Double?): PollPolicy {
        val timeoutMillis = timeout?.let { unboundedOr(it, "timeout", name) } ?: defaults.timeout
        val intervalMillis = interval?.let { nonNegativeMillis(it, "interval", name) } ?: defaults.interval
        var attempts = times?.let { unboundedOr(it, "times", name) } ?: PollPolicy.UNBOUNDED
        if (timeoutMillis == 0L) attempts = minOf(attempts, 1L)
        return PollPolicy(timeout = timeoutMillis, interval = intervalMillis, times = attempts)
    }

    /** `sleep(min, max?)`: a millisecond count, or a range by two numbers or a delta string, as the global `sleep`. zh-CN: `sleep(min, max?)`: 毫秒数, 或以两个数字或增量字符串表示的范围, 同全局 `sleep`. */
    fun sleep(name: String, args: Array<out Any?>): FlowStep.Sleep {
        require(args.isNotEmpty() && !args[0].isJsNullish()) { "Argument \"millis\" is required for $name" }
        require(args.size <= 2) { "Too many arguments (${args.size}) for $name" }
        val min = number(args[0], "millis", name)
        val range = Global.resolveSleepRange(nonNegativeMillis(min, "millis", name), args.getOrNull(1).takeUnless { it.isJsNullish() })
        return FlowStep.Sleep(range.min.toLong(), range.max.toLong())
    }

    /** `timeout(millis)`. */
    fun timeout(name: String, args: Array<out Any?>): FlowStep.Timeout {
        require(args.size == 1) { "Argument \"millis\" is required for $name" }
        return FlowStep.Timeout(nonNegativeMillis(number(args[0], "millis", name), "millis", name))
    }

    /** `retry(times = 1, delay = 0)`. */
    fun retry(name: String, args: Array<out Any?>): FlowStep.Retry {
        require(args.size <= 2) { "Too many arguments (${args.size}) for $name" }
        val times = args.getOrNull(0).takeUnless { it.isJsNullish() }?.let { nonNegativeMillis(number(it, "times", name), "times", name) } ?: 1L
        val delay = args.getOrNull(1).takeUnless { it.isJsNullish() }?.let { nonNegativeMillis(number(it, "delay", name), "delay", name) } ?: 0L
        require(times <= Int.MAX_VALUE) { "Argument \"times\" ($times) is too large for $name" }
        return FlowStep.Retry(times.toInt(), delay)
    }

    fun whenPresent(name: String, args: Array<out Any?>): FlowStep.WhenPresent {
        require(args.size in 2..3) { "$name expects (cond, handler, options?)" }
        require(args[1] is Callable) { "Argument \"handler\" must be a function for $name" }
        val options = controlOptions(name, args.getOrNull(2), setOf("timeout", "interval", "times", "root", "compass", "resultType", "recoverService"))
        val spec = wait(name, arrayOf(args[0], options))
        return FlowStep.WhenPresent(FlowStep.Wait(spec.cond, spec.policy, recoverService = spec.recoverService), args[1])
    }

    fun repeatUntil(name: String, args: Array<out Any?>): FlowStep.RepeatUntil {
        require(args.size in 2..3) { "$name expects (action, cond, options?)" }
        require(args[0] is Callable) { "Argument \"action\" must be a function for $name" }
        val options = controlOptions(name, args.getOrNull(2), setOf("timeout", "interval", "maxAttempts", "retryOn", "root", "compass", "resultType"))
        val timeout = options?.number("timeout", name)?.let { nonNegativeMillis(it, "timeout", name) } ?: defaults.timeout
        require(timeout < Long.MAX_VALUE) { "Option \"timeout\" must be finite for $name" }
        val interval = options?.number("interval", name)?.let { nonNegativeMillis(it, "interval", name) } ?: defaults.interval
        val attempts = options?.number("maxAttempts", name) ?: 0.0
        require(attempts.isFinite() && attempts >= 0 && attempts <= Int.MAX_VALUE && attempts == kotlin.math.floor(attempts)) { "Option \"maxAttempts\" must be a non-negative integer for $name" }
        val retryOn = options?.prop("retryOn").takeUnless { it.isJsNullish() }
        require(retryOn == null || retryOn is Callable) { "Option \"retryOn\" must be a function for $name" }
        val scope = options?.let { scope(it, name) } ?: FlowScope.NONE
        return FlowStep.RepeatUntil(args[0], conditions.of(args[1], name, scope), timeout, attempts.toInt(), interval, retryOn)
    }

    private fun controlOptions(name: String, value: Any?, keys: Set<String>): ScriptableObject? {
        if (value.isJsNullish()) return null
        require(value is ScriptableObject && !value.isJsArray() && value !is Callable) { "Argument \"options\" must be an object for $name" }
        value.ids.forEach { key -> require(key.toString() in keys) { "Unknown option \"$key\" for $name; expected one of ${keys.joinToString(", ")}" } }
        return value
    }

    /**
     * `flow.defaults(options)` (roadmap P3.3): writes the given keys into the defaults (`timeout`
     * with `Infinity` for none, `interval`, `stableFor`, `strictActions`, `humanize` (P4.2:
     * `{ offset, delay }` or `false`) and `recoverService` (P5.5)); returns the `trace` option when
     * present. Unknown keys are an error, and nothing is written when any value is invalid.
     * zh-CN: `flow.defaults(options)` (Roadmap P3.3): 把给定的键写入默认值 (`timeout` 以 `Infinity` 为不限, `interval`, `stableFor`,
     * `strictActions`, `humanize` (P4.2: `{ offset, delay }` 或 `false`) 与 `recoverService` (P5.5)); 存在 `trace` 时返回它. 未知的键报错,
     * 任一值无效时什么都不写入.
     */
    fun applyDefaults(options: ScriptableObject, name: String): Boolean? {
        options.ids.forEach { key -> require(key.toString() in DEFAULT_KEYS) { "Unknown option \"$key\" for $name; expected one of ${DEFAULT_KEYS.joinToString(", ")}" } }
        val timeout = options.number("timeout", name)?.let { unboundedOr(it, "timeout", name) }
        val interval = options.number("interval", name)?.let { nonNegativeMillis(it, "interval", name) }
        val stableFor = options.number("stableFor", name)?.let { nonNegativeMillis(it, "stableFor", name) }
        val strict = options.prop("strictActions").takeUnless { it.isJsNullish() }?.let { Context.toBoolean(it) }
        val humanize = options.prop("humanize").takeUnless { it.isJsNullish() }?.let { ToolkitArguments.humanize(it, name, defaults.humanize) }
        val recoverService = options.prop("recoverService").takeUnless { it.isJsNullish() }?.let { Context.toBoolean(it) }
        val trace = options.prop("trace").takeUnless { it.isJsNullish() }?.let { Context.toBoolean(it) }
        timeout?.let { defaults.timeout = it }
        interval?.let { defaults.interval = it }
        stableFor?.let { defaults.stableFor = it }
        strict?.let { defaults.strictActions = it }
        humanize?.let { defaults.humanize = it }
        recoverService?.let { defaults.recoverService = it }
        return trace
    }

    /** `scope({ root, compass, resultType })`. */
    fun scope(options: Any?, name: String): FlowScope {
        require(options is ScriptableObject && !options.isJsArray() && options !is Callable) { "Argument \"options\" must be an object for $name, got ${options.jsBrief()}" }
        val root = options.prop("root").takeUnless { it.isJsNullish() }?.let {
            RhinoUtils.unwrap(it) as? UiObject ?: throw WrappedIllegalArgumentException("Option \"root\" must be a UiObject for $name, got ${it.jsBrief()}")
        }
        val compass = options.prop("compass").takeUnless { it.isJsNullish() }?.let {
            it as? CharSequence ?: throw WrappedIllegalArgumentException("Option \"compass\" must be a string for $name, got ${it.jsBrief()}")
        }
        val resultType = options.prop("resultType").takeUnless { it.isJsNullish() }?.let {
            when (it) {
                is CharSequence -> it.toString()
                is List<*> -> it
                else -> throw WrappedIllegalArgumentException("Option \"resultType\" must be a string or an array for $name, got ${it.jsBrief()}")
            }
        }
        return FlowScope(root, compass, resultType)
    }

    /**
     * Splits the arguments of an action: the leading non-functions are its arguments, the trailing
     * functions its `onOk` / `onErr`.
     * zh-CN: 拆分动作的参数: 开头的非函数为动作参数, 尾随的函数为其 `onOk` / `onErr`.
     */
    fun actionArguments(name: String, args: Array<out Any?>): Pair<List<Any?>, Pair<Callable?, Callable?>> {
        val firstCallable = args.indexOfFirst { it is Callable }.takeIf { it >= 0 } ?: args.size
        val actionArgs = args.take(firstCallable).map { RhinoUtils.unwrap(it) }
        return actionArgs to callbacks(name, args, firstCallable)
    }

    /** The trailing `onOk` / `onErr` of `then`-like calls; nullish entries are skipped. zh-CN: `then` 类调用尾随的 `onOk` / `onErr`; 空值项跳过. */
    fun callbacks(name: String, args: Array<out Any?>, from: Int = 0): Pair<Callable?, Callable?> {
        val callbacks = ArrayList<Callable>(2)
        for (i in from until args.size) {
            val a = args[i]
            when {
                a.isJsNullish() -> continue
                a is Callable -> callbacks += a
                else -> throw WrappedIllegalArgumentException("Argument ${a.jsBrief()} at index $i must be a function for $name")
            }
        }
        require(callbacks.size <= 2) { "Too many callbacks (${callbacks.size}) for $name" }
        return callbacks.getOrNull(0) to callbacks.getOrNull(1)
    }

    /** The sources of `all` / `race` / `any`: one array argument, or the arguments themselves. zh-CN: `all` / `race` / `any` 的来源: 一个数组参数, 或参数本身. */
    fun sources(name: String, args: Array<out Any?>): List<Any?> = when {
        args.size == 1 && args[0] is List<*> -> (args[0] as List<*>).toList()
        else -> args.toList()
    }.also { list -> require(list.none { it.isJsNullish() }) { "Sources of $name cannot contain null or undefined" } }

    private fun parse(name: String, args: Array<out Any?>, from: Int, maxNumbers: Int): Parsed {
        var options: ScriptableObject? = null
        val numbers = ArrayList<Double>()
        val callbacks = ArrayList<Callable>()
        for (i in from until args.size) {
            val a = args[i]
            when {
                a.isJsNullish() -> continue
                a is Callable -> callbacks += a
                a is Number -> numbers += number(a, "argument $i", name)
                a is ScriptableObject && !a.isJsArray() -> {
                    require(options == null) { "Only one options object is accepted by $name" }
                    options = a
                }
                else -> throw WrappedIllegalArgumentException("Argument ${a.jsBrief()} at index $i is not accepted by $name")
            }
        }
        require(numbers.size <= maxNumbers) { "Too many numeric arguments (${numbers.size}) for $name" }
        require(callbacks.size <= 2) { "Too many callbacks (${callbacks.size}) for $name" }
        return Parsed(options, numbers, callbacks)
    }

    private fun ScriptableObject.number(key: String, name: String): Double? = prop(key).takeUnless { it.isJsNullish() }?.let { number(it, key, name) }

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

    private fun compare(value: Any, name: String): StabilityCompare = when {
        value is Callable -> StabilityCompare.Custom(value)
        value is CharSequence -> when (value.toString()) {
            "fingerprint" -> StabilityCompare.Fingerprint
            "bounds" -> StabilityCompare.Bounds
            "content" -> StabilityCompare.Content
            else -> throw WrappedIllegalArgumentException("Option \"compare\" must be \"fingerprint\", \"bounds\", \"content\" or a function for $name, got \"$value\"")
        }
        else -> throw WrappedIllegalArgumentException("Option \"compare\" must be a string or a function for $name, got ${value.jsBrief()}")
    }

    private fun missing(value: Any, name: String): MissingPolicy = when ((value as? CharSequence)?.toString()) {
        "reset" -> MissingPolicy.RESET
        "fail" -> MissingPolicy.FAIL
        else -> throw WrappedIllegalArgumentException("Option \"missing\" must be \"reset\" or \"fail\" for $name, got ${value.jsBrief()}")
    }

    private fun delay(value: Any, name: String): FlowStep.Sleep = when (value) {
        is List<*> -> sleep(name, value.toTypedArray<Any?>())
        else -> sleep(name, arrayOf(value))
    }

    companion object {

        val DEFAULT_KEYS = listOf("timeout", "interval", "stableFor", "strictActions", "humanize", "recoverService", "trace")

        /** A settled state, or `null` while pending: the form [FlowCondition.Settling] reads. zh-CN: 已结算的状态, 待定时为 `null`: [FlowCondition.Settling] 读取的形式. */
        fun settledOrNull(node: FlowNode): FlowNode.State? = node.state.takeUnless { it is FlowNode.State.Pending }

    }

}
