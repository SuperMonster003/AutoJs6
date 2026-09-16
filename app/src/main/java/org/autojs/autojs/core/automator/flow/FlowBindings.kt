package org.autojs.autojs.core.automator.flow

/**
 * What the Flow core needs from the runtime and knows nothing about: how a condition is probed,
 * how a callback is invoked, how an action is performed, which values are other flows. The
 * runtime implements this over `Selector.pickup`, Rhino callables and `UiObject` (P3.2); the
 * tests implement it over `FakeUiNode` and Kotlin lambdas. Every method runs on the lane the
 * step runs on: [probe] / [perform] / [performGlobal] on the worker, [invoke] on the script lane
 * (or the worker for `run` callbacks and stability comparators).
 * zh-CN: Flow 核心需要而自身一无所知的运行时能力: 如何探测条件, 如何调用回调, 如何执行动作, 哪些值是另一条 Flow. 运行时基于
 * `Selector.pickup`, Rhino 可调用对象与 `UiObject` 实现 (P3.2); 测试基于 `FakeUiNode` 与 Kotlin lambda 实现. 各方法在步骤所在的
 * 线程上运行: [probe] / [perform] / [performGlobal] 在工作线程, [invoke] 在脚本线程 (`run` 回调与稳定比较器则在工作线程).
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
interface FlowBindings {

    /** Capture script frames at step creation, before it moves to a worker. zh-CN: 在步骤移交工作线程前捕获创建处的脚本栈. */
    fun captureSourceStack(): List<StackTraceElement> = emptyList()

    /**
     * One attempt at [cond] under [scope]: the picked-up value, or `null` when the condition is
     * not satisfied yet (not found; a function condition returned a value the `wait` truthiness
     * rule rejects). May throw a [FlowError] (`A11Y_UNAVAILABLE`) to fail the wait at once.
     * zh-CN: 对 [cond] 在 [scope] 下的一次尝试: 拾取到的值, 或条件尚未满足时为 `null` (未找到; 函数条件返回了 `wait` 真值规则
     * 判为不满足的值). 可抛出 [FlowError] (`A11Y_UNAVAILABLE`) 使等待立即失败.
     */
    fun probe(cond: Any?, scope: FlowScope): Any?

    /** A short rendering of [cond] for error messages, e.g. the selector's string form. zh-CN: [cond] 用于错误信息的简短呈现, 如选择器的字符串形式. */
    fun describe(cond: Any?): String? = cond?.toString()

    /** Calls the user callback [fn] with [args] and returns its result; exceptions propagate. zh-CN: 以 [args] 调用用户回调 [fn] 并返回结果; 异常照常抛出. */
    fun invoke(fn: Any?, args: List<Any?>): Any?

    /** The truthiness of a callback result by the language's rules. zh-CN: 回调结果按语言规则的真值. */
    fun isTruthy(value: Any?): Boolean = value != null && value != false

    /**
     * Performs the node action [action] with [args] on [target]: the action's result, or `null`
     * when [target] is not something the action applies to (`INVALID_TARGET`).
     * zh-CN: 对 [target] 以 [args] 执行节点动作 [action]: 动作的结果, 或 [target] 不是该动作可作用的对象时为 `null`
     * (`INVALID_TARGET`).
     */
    fun perform(action: String, target: Any?, args: List<Any?>): Boolean?

    /** Performs the global action [action] with [args]. zh-CN: 以 [args] 执行全局动作 [action]. */
    fun performGlobal(action: String, args: List<Any?>): Boolean

    /**
     * The [FlowNode] behind [value] when a callback returned a flow (or a promise the runtime can
     * wrap), else `null`; the node is adopted as the callback's outcome.
     * zh-CN: 回调返回 Flow (或运行时能包装的 Promise) 时 [value] 背后的 [FlowNode], 否则为 `null`; 该节点被采纳为回调的结果.
     */
    fun adopt(value: Any?): FlowNode? = value as? FlowNode

    /** Freeze a stability projection before another sample can mutate it.
     * zh-CN: 在下一次采样可能修改投影之前保存其值. */
    fun snapshot(value: Any?): Any? = Stabilizer.snapshot(value)

    /** Key of a single node or ordered collection. zh-CN: 单节点或有序集合的比较键. */
    fun stabilityKey(compare: StabilityCompare, value: Any): Any? = Stabilizer.keyOf(compare, value)

    /**
     * Waits at most [timeoutMillis] for the accessibility service to be available again after a
     * probe failed with `A11Y_UNAVAILABLE` (roadmap P5.5, `recoverService`); returns whether it
     * is. The default assumes it is and lets the next probe tell. Interruptible: a cancelled chain
     * interrupts the worker. zh-CN: 探测以 `A11Y_UNAVAILABLE` 失败后, 至多等待 [timeoutMillis] 毫秒让无障碍服务恢复可用
     * (Roadmap P5.5, `recoverService`); 返回它是否可用. 缺省实现假定可用, 交给下一次探测判断. 可中断: 取消链会中断工作线程.
     */
    fun awaitService(timeoutMillis: Long): Boolean = true

}

/**
 * A place to run tasks: the worker thread of the script's flows (blocking steps) or the script
 * thread (user callbacks). Tasks of one lane run one at a time, in order.
 * zh-CN: 执行任务的地方: 脚本的 Flow 工作线程 (阻塞步骤) 或脚本线程 (用户回调). 同一 lane 的任务按顺序逐个执行.
 */
interface FlowLane {

    fun post(task: Runnable)

    /**
     * Interrupts the task running right now, if any, so that a blocking step stops early on
     * cancellation; the default does nothing (a script lane never runs blocking steps).
     * zh-CN: 中断此刻正在运行的任务 (若有), 使阻塞步骤在取消时提前结束; 默认什么也不做 (脚本线程不运行阻塞步骤).
     */
    fun interruptCurrent() {}

}

/** Observes step execution, for `flow.trace(true)` (P3.3). zh-CN: 观察步骤执行, 供 `flow.trace(true)` 使用 (P3.3). */
interface FlowListener {

    fun onStepStart(node: FlowNode) {}

    fun onStepEnd(node: FlowNode, state: FlowNode.State, elapsed: Long) {}

}

/**
 * Fans every step notification out to several listeners in order, so that the stats recorder of
 * `auto.stats` and the trace of `flow.trace(true)` can both watch one scheduler.
 * zh-CN: 把每个步骤通知按顺序分发给多个监听器, 使 `auto.stats` 的统计记录器与 `flow.trace(true)` 的追踪可以同时观察一个调度器.
 */
class FlowListeners(private val listeners: List<FlowListener>) : FlowListener {

    override fun onStepStart(node: FlowNode) = listeners.forEach { it.onStepStart(node) }

    override fun onStepEnd(node: FlowNode, state: FlowNode.State, elapsed: Long) = listeners.forEach { it.onStepEnd(node, state, elapsed) }

    companion object {

        /** `null` without a listener, the listener itself when it is alone, else the fan-out. zh-CN: 没有监听器时为 `null`, 只有一个时为其本身, 否则为分发器. */
        fun of(vararg listeners: FlowListener?): FlowListener? = listeners.filterNotNull().let {
            when (it.size) {
                0 -> null
                1 -> it[0]
                else -> FlowListeners(it)
            }
        }

    }

}
