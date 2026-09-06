package org.autojs.autojs.core.automator.flow

/**
 * One step of a running chain and the handle to it (the `FlowHandle` of the roadmap): it holds
 * the step, its parent, the settlement (pending, fulfilled with a value, or rejected with an
 * error; cancellation is a rejection with [FlowError.Cancelled]) and the [FlowContext] its
 * children inherit. Nodes form a tree: a chain method appends a child, a fork appends a second
 * child to the same parent, and every node settles once. Created through [FlowScheduler.start]
 * or [next]; the scheduler owns the state, guarded by its lock.
 * zh-CN: 运行中链的一个步骤及其句柄 (Roadmap 中的 `FlowHandle`): 持有步骤, 父节点, 结果 (待定, 以值兑现, 或以错误拒绝; 取消是以
 * [FlowError.Cancelled] 拒绝) 与子节点继承的 [FlowContext]. 节点构成一棵树: 链式方法追加一个子节点, 分叉则在同一父节点下追加第二
 * 个子节点, 每个节点只结算一次. 经 [FlowScheduler.start] 或 [next] 创建; 状态由调度器持有并以其锁保护.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class FlowNode internal constructor(
    val step: FlowStep,
    val parent: FlowNode?,
    private val scheduler: FlowScheduler,
) {

    sealed class State {

        object Pending : State() {
            override fun toString() = "Pending"
        }

        data class Fulfilled(val value: Any?) : State()

        data class Rejected(val error: Throwable) : State()

    }

    // The fields below are guarded by the scheduler's lock. zh-CN: 以下字段由调度器的锁保护.
    internal var stateInternal: State = State.Pending
    internal var contextInternal: FlowContext? = null
    internal val children = ArrayList<FlowNode>()
    internal val callbacks = ArrayList<(State) -> Unit>()
    internal val owned = ArrayList<FlowNode>()
    internal var cancelRequested = false

    /** The worker lane of the chain (that of the root), set by the scheduler at creation. zh-CN: 链 (即根) 的工作 lane, 由调度器在创建时设置. */
    internal lateinit var worker: FlowLane

    /** Whether a blocking task of this node is running on [worker] right now (guarded by the scheduler's lock). zh-CN: 本节点的阻塞任务此刻是否正在 [worker] 上运行 (受调度器的锁保护). */
    internal var running = false
    /** The clock reading when the step began, negative until then. zh-CN: 步骤开始时的时钟读数, 开始前为负. */
    internal var startedAt = NOT_STARTED

    val state: State get() = scheduler.stateOf(this)

    val isSettled: Boolean get() = state !is State.Pending

    val isFulfilled: Boolean get() = state is State.Fulfilled

    val isRejected: Boolean get() = state is State.Rejected

    val isCancelled: Boolean get() = (state as? State.Rejected)?.error is FlowError.Cancelled

    /** The value once fulfilled, `null` otherwise. zh-CN: 兑现后的值, 其它情况为 `null`. */
    val value: Any? get() = (state as? State.Fulfilled)?.value

    /** The error once rejected, `null` otherwise. zh-CN: 拒绝后的错误, 其它情况为 `null`. */
    val error: Throwable? get() = (state as? State.Rejected)?.error

    /** The context the children of this node run in; the parent's (or the initial one) until settled. zh-CN: 本节点的子节点运行于其中的上下文; 结算前为父节点的 (或初始的). */
    val context: FlowContext get() = scheduler.contextOf(this)

    /** Whether a step has been appended after this node (a leaf rejection is an unhandled one). zh-CN: 本节点之后是否追加了步骤 (叶节点的拒绝即未处理的拒绝). */
    val hasContinuation: Boolean get() = scheduler.hasChildren(this)

    /** Appends [step] after this node and starts it (once this node settles). zh-CN: 在本节点之后追加 [step] 并启动 (待本节点结算). */
    fun next(step: FlowStep): FlowNode = scheduler.start(step, this)

    /**
     * Rejects this node with `CANCELLED` unless it has settled, together with everything it runs
     * on its behalf and, upwards, every pending ancestor that has no other live continuation (so
     * cancelling the end of a linear chain stops the wait at its start). Steps not yet run are
     * skipped; a blocking step running right now is interrupted. Returns whether anything was
     * cancelled.
     * zh-CN: 除非已结算, 以 `CANCELLED` 拒绝本节点, 连同代其运行的一切, 并向上取消没有其它存活续接的待定祖先 (因此取消线性链的末端
     * 会停止链首的等待). 尚未运行的步骤被跳过; 正在运行的阻塞步骤被中断. 返回是否取消了什么.
     */
    fun cancel(): Boolean = scheduler.cancel(this)

    /** Runs [callback] with the settlement, at once when already settled. zh-CN: 以结算结果运行 [callback], 已结算时立即运行. */
    fun onSettled(callback: (State) -> Unit) = scheduler.onSettled(this, callback)

    /**
     * Blocks until settled and returns the value, or throws the error; gives up with
     * [FlowError.Timeout] (reason `sync`) after [timeoutMillis] when non-negative, leaving the
     * chain running. The runtime forbids this on the UI thread (`.sync()`).
     * zh-CN: 阻塞直到结算并返回值, 或抛出错误; [timeoutMillis] 非负时超过该时长即以 [FlowError.Timeout] (reason `sync`) 放弃, 链
     * 继续运行. 运行时禁止在 UI 线程调用 (`.sync()`).
     */
    fun await(timeoutMillis: Long = -1): Any? = scheduler.await(this, timeoutMillis)

    override fun toString(): String = "FlowNode(${step.name}, ${state})"

    internal companion object {
        const val NOT_STARTED = -1L
    }

}

/**
 * What a node hands down to its children: the pickup scope of the following waits and the
 * deadline of the rest of the chain ([NO_DEADLINE] when unlimited).
 * zh-CN: 节点传给子节点的内容: 后续等待的拾取作用域与链其余部分的截止时刻 (无限制时为 [NO_DEADLINE]).
 */
data class FlowContext(val scope: FlowScope = FlowScope.NONE, val deadline: Long = NO_DEADLINE) {

    fun remainingUntilDeadline(now: Long): Long = when (deadline) {
        NO_DEADLINE -> Long.MAX_VALUE
        else -> deadline - now
    }

    companion object {

        const val NO_DEADLINE = Long.MAX_VALUE

        val INITIAL = FlowContext()

    }

}
