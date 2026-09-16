package org.autojs.autojs.core.automator.flow

import org.autojs.autojs.core.automator.query.PollPolicy
import org.autojs.autojs.core.automator.toolkit.Humanize

/**
 * One step of a Flow chain (roadmap Appendix A.3), as data: what to do, not how. Conditions
 * ([Wait.cond] and friends) and callbacks ([Callback.onOk] / [Callback.onErr]) are opaque here
 * because the core is free of Android and Rhino; the [FlowBindings] of the runtime give them
 * meaning. The [FlowScheduler] executes steps and applies the value rules of Appendix A.4.
 * zh-CN: Flow 链的一个步骤 (Roadmap 附录 A.3), 只是数据: 做什么, 而非怎么做. 条件 ([Wait.cond] 等) 与回调 ([Callback.onOk] /
 * [Callback.onErr]) 在此是不透明的, 因为核心不依赖 Android 与 Rhino; 由运行时的 [FlowBindings] 赋予含义. [FlowScheduler]
 * 执行步骤并施加附录 A.4 的取值规则.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
sealed class FlowStep {

    /** The short name a trace and a [FlowError.step] show. zh-CN: 追踪输出与 [FlowError.step] 显示的短名. */
    abstract val name: String

    /** Starts a chain with [value] (`flow.of`). zh-CN: 以 [value] 起链 (`flow.of`). */
    data class Value(val value: Any?) : FlowStep() {
        override val name get() = "of"
    }

    /**
     * A root settled from outside through [FlowScheduler.fulfill] / [FlowScheduler.reject]: how the
     * runtime adopts a promise (or any thenable) it hooked callbacks onto; [source] is only kept for
     * diagnostics. Never rerun, never cancelled from below (it has nothing running).
     * zh-CN: 由外部经 [FlowScheduler.fulfill] / [FlowScheduler.reject] 结算的根节点: 运行时借此采纳挂上了回调的 Promise (或任何
     * thenable); [source] 只作诊断用. 不会重跑, 也没有可中断的运行中任务.
     */
    data class Adopted(val source: Any? = null) : FlowStep() {
        override val name get() = "promise"
    }

    /**
     * Polls [cond] under [policy] until the probe yields a value (`wait`, `waitUntil`), or, with
     * [negate], until it yields none (`waitWhile`, resolving `true`). With [within] the found value
     * also becomes the root of the scope of the following waits (`within(cond)`).
     * zh-CN: 按 [policy] 轮询 [cond] 直到探测得到值 (`wait`, `waitUntil`); [negate] 时直到得不到值 (`waitWhile`, resolve 为
     * `true`). [within] 时找到的值同时成为后续等待的作用域根节点 (`within(cond)`).
     */
    data class Wait(
        val cond: Any?,
        val policy: PollPolicy,
        val negate: Boolean = false,
        val within: Boolean = false,
        /** Keep waiting through an accessibility service outage instead of failing at once (roadmap P5.5). zh-CN: 无障碍服务中断期间继续等待而非立即失败 (Roadmap P5.5). */
        val recoverService: Boolean = false,
    ) : FlowStep() {
        override val name get() = when {
            within -> "within"
            negate -> "waitWhile"
            else -> "wait"
        }
    }

    /**
     * Polls [cond] at [policy]'s interval until the target has been unchanged for
     * [stability]'s `stableFor` milliseconds ([StableMode.STABLE]), unchanged and visible
     * ([StableMode.VISIBLE]), or absent / invisible for that long ([StableMode.HIDDEN]); the policy's
     * timeout bounds the whole wait (Appendix A.5).
     * zh-CN: 按 [policy] 的间隔轮询 [cond], 直到目标在 [stability] 的 `stableFor` 毫秒内无变化 ([StableMode.STABLE]), 无变化且
     * 可见 ([StableMode.VISIBLE]), 或在该时长内不存在 / 不可见 ([StableMode.HIDDEN]); 计划的超时约束整个等待 (附录 A.5).
     */
    data class WaitStable(
        val cond: Any?,
        val policy: PollPolicy,
        val stability: Stability = Stability(),
        val mode: StableMode = StableMode.STABLE,
        /** As [Wait.recoverService]. zh-CN: 同 [Wait.recoverService]. */
        val recoverService: Boolean = false,
    ) : FlowStep() {
        override val name get() = when (mode) {
            StableMode.STABLE -> "waitForStable"
            StableMode.VISIBLE -> "waitForVisible"
            StableMode.HIDDEN -> "waitForHidden"
        }
    }

    /**
     * Performs the node action [action] on the current value (`click`, `setText`, ...). The value must
     * be an actionable target, else `INVALID_TARGET`; `false` rejects `ACTION_FAILED` when [strict]
     * (D12) and resolves `false` otherwise; success resolves the target itself. The random pause of
     * [humanize] (P4.2, `flow.defaults({ humanize })`) precedes the action.
     * zh-CN: 对当前值执行节点动作 [action] (`click`, `setText`, ...). 值须是可作用的目标, 否则 `INVALID_TARGET`; 返回 `false` 时
     * [strict] (D12) 则以 `ACTION_FAILED` 拒绝, 否则 resolve 为 `false`; 成功 resolve 为目标自身. 动作前先做 [humanize] 的随机暂停
     * (P4.2, `flow.defaults({ humanize })`).
     */
    data class Action(val action: String, val args: List<Any?> = emptyList(), val strict: Boolean = true, val humanize: Humanize = Humanize.NONE) : FlowStep() {
        override val name get() = action
    }

    /**
     * Performs a global action (`back`, `home`, `swipe`, ...) and passes the current value through;
     * `false` rejects `ACTION_FAILED` when [strict]. The random pause of [humanize] precedes the action.
     * zh-CN: 执行全局动作 (`back`, `home`, `swipe`, ...) 并透传当前值; 返回 `false` 时 [strict] 则以 `ACTION_FAILED` 拒绝. 动作前先做
     * [humanize] 的随机暂停.
     */
    data class GlobalAction(val action: String, val args: List<Any?> = emptyList(), val strict: Boolean = true, val humanize: Humanize = Humanize.NONE) : FlowStep() {
        override val name get() = action
    }

    /**
     * A toolkit call (roadmap P4.2: `smartClick`, `scrollUntil`, `typeInto`, ...): [block] runs on the
     * worker lane with the current value and the scope of the chain, and what it returns becomes the
     * value (passing the input through is the block's choice); an exception rejects (the tools throw
     * `FlowError`s). [detail] is what a trace shows after the [name].
     * zh-CN: 工具调用 (Roadmap P4.2: `smartClick`, `scrollUntil`, `typeInto`, ...): [block] 在工作 lane 上以当前值与链的作用域运行,
     * 其返回值成为新值 (是否透传输入由 block 决定); 异常即拒绝 (工具抛出的是 `FlowError`). [detail] 为追踪输出在 [name] 之后显示的内容.
     */
    class Tool(override val name: String, val detail: String? = null, val block: (value: Any?, scope: FlowScope) -> Any?) : FlowStep() {
        override fun toString(): String = "Tool($name${detail?.let { " $it" } ?: ""})"
    }

    /** Sleeps a random duration in [min]..[max] milliseconds and passes the value through. zh-CN: 休眠 [min]..[max] 毫秒中的随机时长并透传当前值. */
    data class Sleep(val min: Long, val max: Long = min) : FlowStep() {
        init {
            require(min >= 0) { "min ($min) cannot be negative" }
            require(max >= min) { "max ($max) must be at least min ($min)" }
        }

        override val name get() = "sleep"
    }

    /**
     * A user callback. `THEN` runs [onOk] with the value or [onErr] with the error (either may be
     * absent); `CATCH` is `THEN` without [onOk]; `MAP` is `THEN` with [onOk] only; `FILTER` rejects
     * `INVALID_TARGET` when [onOk] returns a falsy value and passes the value through otherwise;
     * `PEEK` runs [onOk] and passes the value through; `FINALLY` runs [onOk] without arguments and
     * restores the previous outcome; `RUN` is `THEN` executed on the worker lane (`flow.run`). A
     * callback that returns a Flow (or anything [FlowBindings.adopt] recognises) is adopted.
     * zh-CN: 用户回调. `THEN` 以值调用 [onOk] 或以错误调用 [onErr] (两者皆可缺省); `CATCH` 是没有 [onOk] 的 `THEN`; `MAP` 是只有
     * [onOk] 的 `THEN`; `FILTER` 在 [onOk] 返回假值时以 `INVALID_TARGET` 拒绝, 否则透传; `PEEK` 调用 [onOk] 后透传; `FINALLY`
     * 无参调用 [onOk] 后恢复原结果; `RUN` 是在工作线程上执行的 `THEN` (`flow.run`). 回调返回 Flow (或 [FlowBindings.adopt]
     * 认得的对象) 时予以采纳.
     */
    data class Callback(val kind: CallbackKind, val onOk: Any? = null, val onErr: Any? = null) : FlowStep() {
        override val name get() = kind.stepName
    }

    /**
     * `all` / `race` / `any` over [sources], each a [FlowNode] or a condition (which becomes a
     * [Wait] with [policy]); Promise semantics.
     * zh-CN: 对 [sources] 的 `all` / `race` / `any`, 每项为 [FlowNode] 或条件 (转为带 [policy] 的 [Wait]); 语义同 Promise.
     */
    data class Combinator(val kind: CombinatorKind, val sources: List<Any?>, val policy: PollPolicy) : FlowStep() {
        override val name get() = kind.stepName
    }

    /** Sets the scope of the following waits and passes the value through. zh-CN: 设置后续等待的作用域并透传当前值. */
    data class Scope(val scope: FlowScope) : FlowStep() {
        override val name get() = "scope"
    }

    /**
     * Sets a deadline [millis] from now for the rest of the chain: later waits are clipped to it and
     * a step starting after it rejects `TIMEOUT` (reason `chain`). Passes the value through.
     * zh-CN: 为链的其余部分设置自此刻起 [millis] 毫秒的截止: 之后的等待被截短到截止, 截止后开始的步骤以 `TIMEOUT` (reason `chain`)
     * 拒绝. 透传当前值.
     */
    data class Timeout(val millis: Long) : FlowStep() {
        init {
            require(millis >= 0) { "millis ($millis) cannot be negative" }
        }

        override val name get() = "timeout"
    }

    /**
     * Reruns the previous step up to [times] more times when it rejected (not when cancelled),
     * sleeping [delay] milliseconds before each rerun; passes a fulfilled value through.
     * zh-CN: 上一步骤拒绝 (非取消) 时最多重跑 [times] 次, 每次重跑前休眠 [delay] 毫秒; 上一步骤成功则透传.
     */
    data class Retry(val times: Int, val delay: Long = 0) : FlowStep() {
        init {
            require(times >= 0) { "times ($times) cannot be negative" }
            require(delay >= 0) { "delay ($delay) cannot be negative" }
        }

        override val name get() = "retry"
    }

    /** Detect an optional target, await its handler, and preserve the input value. Only absence is skipped.
     * zh-CN: 检测可选目标, 等待处理函数完成并透传输入值; 仅跳过目标缺席. */
    data class WhenPresent(val wait: Wait, val handler: Any?) : FlowStep() {
        override val name get() = "whenPresent"
    }

    /** Check before the first action and after each completed action. Callbacks run on a worker,
     * like `run`, and may return a fresh Flow or Promise. Errors propagate unless retryOn accepts them.
     * zh-CN: 首次动作前及每次动作完成后检查条件. 回调同 run 在工作线程运行, 可返回新的 Flow 或 Promise.
     * 异常默认传播, 仅在 retryOn 接受时重试. */
    data class RepeatUntil(
        val action: Any?,
        val cond: Any?,
        val timeout: Long,
        /** Zero leaves the action count unlimited; timeout still applies. zh-CN: 0 不限制动作次数, 仍受总超时约束. */
        val maxAttempts: Int = 0,
        val interval: Long = 200,
        val retryOn: Any? = null,
    ) : FlowStep() {
        init {
            require(timeout >= 0 && timeout < Long.MAX_VALUE) { "timeout must be finite and non-negative" }
            require(maxAttempts >= 0) { "maxAttempts must be non-negative" }
            require(interval >= 0) { "interval must be non-negative" }
        }

        override val name get() = "repeatUntil"
    }

}

enum class CallbackKind(val stepName: String) {
    THEN("then"),
    CATCH("catch"),
    FINALLY("finally"),
    MAP("map"),
    FILTER("filter"),
    PEEK("peek"),
    RUN("run"),
}

enum class CombinatorKind(val stepName: String) {
    ALL("all"),
    RACE("race"),
    ANY("any"),
}

enum class StableMode { STABLE, VISIBLE, HIDDEN }

/** What a stability wait compares between two sightings of the target. zh-CN: 稳定检测在两次看到目标之间比较什么. */
sealed class StabilityCompare {

    /** `UiObjectSummary.fingerprint` for nodes, `equals` otherwise (default). zh-CN: 节点用 `UiObjectSummary.fingerprint`, 其余用 `equals` (默认). */
    object Fingerprint : StabilityCompare()

    /** The four bounds. zh-CN: 四条边界. */
    object Bounds : StabilityCompare()

    /** Text and description. zh-CN: 文本与描述. */
    object Content : StabilityCompare()

    /** A user comparator `fn(prev, next) -> boolean`, invoked through the bindings. zh-CN: 用户比较器 `fn(prev, next) -> boolean`, 经 bindings 调用. */
    data class Custom(val fn: Any?) : StabilityCompare()

}

/** What to do when a target seen before is missing again. zh-CN: 曾看到的目标再次消失时的处理. */
enum class MissingPolicy { RESET, FAIL }

/** The parameters of a stability wait (Appendix A.5). zh-CN: 稳定检测等待的参数 (附录 A.5). */
data class Stability(
    val stableFor: Long = DEFAULT_STABLE_FOR,
    val compare: StabilityCompare = StabilityCompare.Fingerprint,
    val missing: MissingPolicy = MissingPolicy.RESET,
    /** A projection captured by value at each sample, mutually exclusive with a custom compare.
     * zh-CN: 每次采样时按值保存的投影, 与自定义 compare 互斥. */
    val snapshot: Any? = null,
) {

    init {
        require(stableFor >= 0) { "stableFor ($stableFor) cannot be negative" }
    }

    companion object {
        const val DEFAULT_STABLE_FOR = 100L
    }

}

/**
 * Where and how the following waits pick up their targets: an optional root node, a compass to
 * walk from the match and the result type of the pickup; all opaque to the core.
 * zh-CN: 后续等待在何处以及如何拾取目标: 可选的根节点, 从匹配处行走的罗盘, 拾取的结果类型; 对核心均不透明.
 */
data class FlowScope(val root: Any? = null, val compass: Any? = null, val resultType: Any? = null) {

    /** [other]'s non-null parts over this scope. zh-CN: 以 [other] 的非空部分覆盖本作用域. */
    fun overriddenBy(other: FlowScope): FlowScope = FlowScope(
        root = other.root ?: root,
        compass = other.compass ?: compass,
        resultType = other.resultType ?: resultType,
    )

    companion object {
        val NONE = FlowScope()
    }

}
