package org.autojs.autojs.core.automator.flow

import org.autojs.autojs.core.automator.flow.FlowNode.State
import org.autojs.autojs.core.automator.query.PollPolicy
import org.autojs.autojs.core.automator.query.Poller
import org.autojs.autojs.core.automator.toolkit.Humanize
import kotlin.math.ceil

/**
 * Runs Flow chains (roadmap Appendix A): starts a [FlowNode] per step, settles it once its parent
 * has settled, and hands the settlement down. Blocking steps (waits, stability waits, sleeps,
 * actions, tool calls) run on the [worker] lane, user callbacks on the [script] lane, bookkeeping steps
 * (`of`, `scope`, `timeout`, combinators, `retry`) inline on whichever thread settles the parent.
 * The value rules are those of Appendix A.4: a wait resolves to what it found, an action to its
 * target, a global action / sleep / scope / timeout passes the value through, a rejection skips
 * every step until a `catch` / `then(_, onErr)` / `finally`, cancellation is a `CANCELLED`
 * rejection that interrupts a blocking step in progress. Everything the core cannot know
 * (probing, invoking, performing) comes from the [FlowBindings]; time comes from [clock] and
 * [sleep] so the tests drive it exactly. The runtime (P3.2) wraps nodes as the JavaScript `Flow`.
 * zh-CN: 运行 Flow 链 (Roadmap 附录 A): 每个步骤一个 [FlowNode], 父节点结算后结算自身并把结果传下去. 阻塞步骤 (等待, 稳定等待,
 * 休眠, 动作) 在 [worker] 上运行, 用户回调在 [script] 上运行, 簿记步骤 (`of`, `scope`, `timeout`, 组合器, `retry`) 在结算父节点
 * 的线程上就地运行. 取值规则即附录 A.4: 等待 resolve 为找到的值, 动作为其目标, 全局动作 / 休眠 / 作用域 / 超时透传值, 拒绝跳过
 * 之后的步骤直到 `catch` / `then(_, onErr)` / `finally`, 取消是会中断正在运行的阻塞步骤的 `CANCELLED` 拒绝. 核心无法知晓的一切
 * (探测, 调用, 执行) 来自 [FlowBindings]; 时间来自 [clock] 与 [sleep], 使测试能精确驱动. 运行时 (P3.2) 把节点包装为 JavaScript
 * 的 `Flow`.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class FlowScheduler(
    private val worker: FlowLane,
    private val script: FlowLane,
    private val bindings: FlowBindings,
    private val clock: () -> Long,
    private val sleep: (Long) -> Unit,
    private val random: () -> Double = { Math.random() },
    /**
     * When given, every chain root gets its own worker lane from here (children share their
     * root's lane); without it every chain uses [worker]. The runtime backs the lanes by a thread
     * pool, so independent chains (and the sources of a combinator) block concurrently.
     * zh-CN: 给定时每条链的根从此处得到自己的工作 lane (子节点共用其根的 lane); 否则全部链使用 [worker]. 运行时以线程池支撑这些 lane,
     * 使独立的链 (以及组合器的各来源) 并发阻塞.
     */
    private val workerFactory: (() -> FlowLane)? = null,
    /**
     * Sleeps at most the given time but returns early when an accessibility event arrives; the
     * sleeps of event-assisted wait steps go through it (roadmap P5.2).
     * zh-CN: 至多休眠给定时长, 无障碍事件到达时提前返回; 事件辅助的等待步骤的休眠经由它 (Roadmap P5.2).
     */
    private val eventSleep: ((Long) -> Unit)? = null,
) {

    /** Observes step execution (`flow.trace`). zh-CN: 观察步骤执行 (`flow.trace`). */
    @Volatile
    var listener: FlowListener? = null

    // A java.lang.Object monitor: `await` waits on it and `settle` notifies it. zh-CN: java.lang.Object 监视器: `await` 在其上等待, `settle` 通知它.
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val lock = java.lang.Object()

    private val pending = LinkedHashSet<FlowNode>()

    private val poller = Poller(clock, sleep, eventSleep)

    private class Outcome(val state: State, val context: FlowContext)

    /** Nodes not settled yet. zh-CN: 尚未结算的节点数. */
    val pendingCount: Int get() = synchronized(lock) { pending.size }

    /** Starts [step] after [parent] (or as the root of a new chain) and returns its node. zh-CN: 在 [parent] 之后 (或作为新链的根) 启动 [step] 并返回其节点. */
    fun start(step: FlowStep, parent: FlowNode? = null): FlowNode {
        val node = FlowNode(step, parent, this)
        val launchNow = synchronized(lock) {
            node.worker = parent?.worker ?: workerFactory?.invoke() ?: worker
            pending += node
            when (parent) {
                null -> true
                else -> {
                    parent.children += node
                    parent.stateInternal !is State.Pending
                }
            }
        }
        if (launchNow) launch(node)
        return node
    }

    /** A chain starting with [value] (`flow.of`). zh-CN: 以 [value] 起始的链 (`flow.of`). */
    fun of(value: Any?): FlowNode = start(FlowStep.Value(value))

    /**
     * A root that stays pending until [fulfill] or [reject] (or a cancellation): the runtime's way
     * to adopt a promise. [source] is what is being adopted, kept for diagnostics.
     * zh-CN: 保持待定直到 [fulfill] / [reject] (或取消) 的根节点: 运行时借此采纳 Promise. [source] 为被采纳的对象, 仅作诊断用.
     */
    fun defer(source: Any? = null): FlowNode = start(FlowStep.Adopted(source))

    /** Settles a [defer]red node with [value]; ignored once settled. zh-CN: 以 [value] 结算 [defer] 创建的节点; 已结算则忽略. */
    fun fulfill(node: FlowNode, value: Any?) = settleAdopted(node, State.Fulfilled(value))

    /** Settles a [defer]red node with [error]; ignored once settled. zh-CN: 以 [error] 结算 [defer] 创建的节点; 已结算则忽略. */
    fun reject(node: FlowNode, error: Throwable) = settleAdopted(node, State.Rejected(error))

    private fun settleAdopted(node: FlowNode, state: State) {
        require(node.step is FlowStep.Adopted) { "Only a node created by defer() can be settled from outside, not ${node.step.name}" }
        settle(node, Outcome(state, contextOf(node)))
    }

    /**
     * Cancels every pending node (the script is exiting) and interrupts the lanes of the running ones; returns how
     * many were cancelled.
     * zh-CN: 取消全部待定节点 (脚本退出) 并中断正在运行的节点所在的 lane; 返回取消的个数.
     */
    fun cancelAll(): Int {
        val nodes = synchronized(lock) {
            pending.filter { !it.cancelRequested }.onEach { it.cancelRequested = true }.also { cancelled ->
                cancelled.filter { it.running }.map { it.worker }.distinct().forEach { it.interruptCurrent() }
            }
        }
        nodes.forEach { settle(it, cancelledOutcome(it)) }
        return nodes.size
    }

    // Node accessors (the node's state lives here, under the lock). zh-CN: 节点访问器 (节点状态在此, 受锁保护).

    internal fun stateOf(node: FlowNode): State = synchronized(lock) { node.stateInternal }

    internal fun contextOf(node: FlowNode): FlowContext = synchronized(lock) { node.contextInternal ?: inheritedContext(node) }

    internal fun hasChildren(node: FlowNode): Boolean = synchronized(lock) { node.children.isNotEmpty() }

    private fun inheritedContext(node: FlowNode): FlowContext = node.parent?.let { it.contextInternal ?: inheritedContext(it) } ?: FlowContext.INITIAL

    internal fun onSettled(node: FlowNode, callback: (State) -> Unit) {
        val settled = synchronized(lock) {
            when (val state = node.stateInternal) {
                is State.Pending -> null.also { node.callbacks += callback }
                else -> state
            }
        }
        settled?.let(callback)
    }

    internal fun await(node: FlowNode, timeoutMillis: Long): Any? {
        val state = synchronized(lock) {
            val start = System.currentTimeMillis()
            while (node.stateInternal is State.Pending) {
                if (timeoutMillis < 0) {
                    lock.wait()
                } else {
                    val left = timeoutMillis - (System.currentTimeMillis() - start)
                    if (left <= 0) throw FlowError.Timeout("sync", null, timeoutMillis, 0, "sync")
                    lock.wait(left)
                }
            }
            node.stateInternal
        }
        return when (state) {
            is State.Fulfilled -> state.value
            is State.Rejected -> throw state.error
            is State.Pending -> throw IllegalStateException("unreachable")
        }
    }

    internal fun cancel(node: FlowNode): Boolean {
        val toSettle = ArrayList<FlowNode>()
        val toCancelOwned = ArrayList<FlowNode>()
        synchronized(lock) {
            if (node.stateInternal !is State.Pending || node.cancelRequested) return false
            val lanesToInterrupt = LinkedHashSet<FlowLane>()
            var current: FlowNode? = node
            while (current != null) {
                current.cancelRequested = true
                toSettle += current
                toCancelOwned += current.owned
                if (current.running) lanesToInterrupt += current.worker
                // Upwards: a pending parent whose every continuation is cancelled has no reason to go on.
                // zh-CN: 向上: 全部续接都已取消的待定父节点没有继续的理由.
                val parent = current.parent
                current = parent?.takeIf { it.stateInternal is State.Pending && !it.cancelRequested && it.children.all { child -> child.cancelRequested } }
            }
            lanesToInterrupt.forEach { it.interruptCurrent() }
        }
        toSettle.forEach { settle(it, cancelledOutcome(it)) }
        // An owned node may belong to another scheduler (an adopted flow of another script thread), so it
        // is cancelled through its own. zh-CN: 代为运行的节点可能属于另一个调度器 (另一脚本线程的被采纳 Flow), 因此经其自身取消.
        toCancelOwned.forEach { it.cancel() }
        return true
    }

    // Execution. zh-CN: 执行.

    private fun inputOf(node: FlowNode): Pair<State, FlowContext> = synchronized(lock) {
        when (val parent = node.parent) {
            null -> State.Fulfilled(null) to FlowContext.INITIAL
            else -> parent.stateInternal to (parent.contextInternal ?: inheritedContext(parent))
        }
    }

    private fun launch(node: FlowNode) {
        val (input, ctx) = inputOf(node)
        if (synchronized(lock) { node.cancelRequested }) {
            settle(node, cancelledOutcome(node))
            return
        }
        when (val lane = laneFor(node, input)) {
            null -> runNode(node, input, ctx, blocking = false)
            else -> lane.post { runNode(node, input, ctx, blocking = lane === node.worker) }
        }
    }

    private fun runNode(node: FlowNode, input: State, ctx: FlowContext, blocking: Boolean) {
        val proceed = synchronized(lock) {
            (node.stateInternal is State.Pending && !node.cancelRequested).also { if (it) node.startedAt = clock() }
        }
        if (!proceed) {
            settle(node, cancelledOutcome(node))
            return
        }
        listener?.onStepStart(node)
        execute(node, node.step, input, ctx, blocking) { settle(node, it) }
    }

    /**
     * Runs [step] for [node] with [input], reporting through [done] (possibly later, from another
     * thread, for adoptions, combinators and retries); exceptions become rejections, an interrupt
     * a cancellation. While a blocking step runs the node is marked running on its lane, so that
     * [cancel] can interrupt it.
     * zh-CN: 以 [input] 为 [node] 运行 [step], 经 [done] 报告 (采纳, 组合器与重试可能稍后从其它线程报告); 异常成为拒绝, 中断成为
     * 取消. 阻塞步骤运行期间该节点在其 lane 上标记为运行中, 使 [cancel] 得以中断它.
     */
    private fun execute(node: FlowNode, step: FlowStep, input: State, ctx: FlowContext, blocking: Boolean, done: (Outcome) -> Unit) {
        val body = {
            try {
                runStep(node, step, input, ctx, done)
            } catch (_: InterruptedException) {
                done(Outcome(State.Rejected(cancelledError(node, step)), ctx))
            } catch (e: Throwable) {
                // An exception out of a cancelled step is the cancellation, whatever form the
                // interruption took. zh-CN: 已取消步骤抛出的异常即取消本身, 不论中断以何种形式出现.
                val error = if (isCancelRequested(node)) cancelledError(node, step) else e
                done(Outcome(State.Rejected(error), ctx))
            }
        }
        if (blocking) asWorkerTask(node, body) else body()
    }

    /**
     * Runs [block] as the running task of [node] on its lane, so that [cancel] can interrupt it; the
     * interrupt of a cancellation is cleared afterwards so it does not leak into the next task.
     * zh-CN: 把 [block] 作为 [node] 在其 lane 上运行中的任务运行, 使 [cancel] 得以中断它; 事后清除取消所用的中断, 以免泄漏到下一个任务.
     */
    private inline fun asWorkerTask(node: FlowNode, block: () -> Unit) {
        synchronized(lock) { node.running = true }
        try {
            block()
        } finally {
            synchronized(lock) { node.running = false }
            if (isCancelRequested(node)) Thread.interrupted()
        }
    }

    private fun isCancelRequested(node: FlowNode): Boolean = synchronized(lock) { node.cancelRequested }

    private fun runStep(node: FlowNode, step: FlowStep, input: State, ctx: FlowContext, done: (Outcome) -> Unit) {
        val passThrough = Outcome(input, ctx)
        val isFinally = step is FlowStep.Callback && step.kind == CallbackKind.FINALLY
        if (input is State.Fulfilled && !isFinally && ctx.remainingUntilDeadline(clock()) <= 0) {
            done(Outcome(State.Rejected(FlowError.Timeout(step.name, null, 0, 0, "chain")), ctx))
            return
        }
        when (step) {
            is FlowStep.Value -> done(Outcome(State.Fulfilled(step.value), ctx))
            // Settled from outside by fulfill / reject. zh-CN: 由外部经 fulfill / reject 结算.
            is FlowStep.Adopted -> Unit
            is FlowStep.Scope -> done(if (input is State.Fulfilled) Outcome(input, ctx.copy(scope = ctx.scope.overriddenBy(step.scope))) else passThrough)
            is FlowStep.Timeout -> done(if (input is State.Fulfilled) Outcome(input, ctx.copy(deadline = minOf(ctx.deadline, clock() + step.millis))) else passThrough)
            is FlowStep.Wait -> done(if (input is State.Fulfilled) runWait(node, step, ctx) else passThrough)
            is FlowStep.WaitStable -> done(if (input is State.Fulfilled) runWaitStable(node, step, ctx) else passThrough)
            is FlowStep.Action -> done(if (input is State.Fulfilled) runAction(step, input.value, ctx) else passThrough)
            is FlowStep.GlobalAction -> done(if (input is State.Fulfilled) runGlobalAction(step, input, ctx) else passThrough)
            is FlowStep.Tool -> done(if (input is State.Fulfilled) runTool(step, input.value, ctx) else passThrough)
            is FlowStep.Sleep -> done(if (input is State.Fulfilled) runSleep(node, step, input, ctx) else passThrough)
            is FlowStep.Callback -> runCallback(node, step, input, ctx, done)
            is FlowStep.Combinator -> if (input is State.Fulfilled) runCombinator(node, step, ctx, done) else done(passThrough)
            is FlowStep.Retry -> runRetry(node, step, input, ctx, done)
        }
    }

    private fun runWait(node: FlowNode, step: FlowStep.Wait, ctx: FlowContext): Outcome {
        val start = clock()
        val selector = bindings.describe(step.cond)
        val (policy, clipped) = clipToDeadline(step.policy, ctx, step.name, selector)
        var progress: Poller.Progress? = null
        val found = poller.poll(policy) { p ->
            progress = p
            ensureNotCancelled(node, step.name, selector, p.elapsed, p.round)
            val probed = bindings.probe(step.cond, ctx.scope)
            when {
                step.negate -> if (probed == null) SATISFIED else null
                else -> probed
            }
        }
        val attempts = progress?.round ?: 0
        val elapsed = clock() - start
        if (found == null) {
            val reason = if (clipped && policy.timeout != PollPolicy.UNBOUNDED && elapsed > policy.timeout) "chain" else null
            return Outcome(State.Rejected(FlowError.Timeout(step.name, selector, elapsed, attempts, reason)), ctx)
        }
        val value = if (step.negate) true else found
        val context = if (step.within) ctx.copy(scope = ctx.scope.copy(root = value)) else ctx
        return Outcome(State.Fulfilled(value), context)
    }

    private fun runWaitStable(node: FlowNode, step: FlowStep.WaitStable, ctx: FlowContext): Outcome {
        val start = clock()
        val selector = bindings.describe(step.cond)
        val (policy, clipped) = clipToDeadline(step.policy, ctx, step.name, selector)
        if (policy.isNoop) return Outcome(State.Rejected(FlowError.Timeout(step.name, selector, 0, 0, "missing")), ctx)
        val stabilizer = stabilizerFor(step.stability)
        var attempts = 0L
        while (true) {
            attempts += 1
            ensureNotCancelled(node, step.name, selector, clock() - start, attempts)
            val probed = bindings.probe(step.cond, ctx.scope)
            val sample: Any? = when (step.mode) {
                StableMode.STABLE -> probed
                StableMode.VISIBLE -> probed?.takeIf { Stabilizer.isVisible(it) }
                StableMode.HIDDEN -> if (probed != null && Stabilizer.isVisible(probed)) null else HIDDEN
            }
            when (val verdict = stabilizer.observe(sample, clock())) {
                is Stabilizer.Verdict.Stable -> return Outcome(State.Fulfilled(if (step.mode == StableMode.HIDDEN) true else verdict.sample), ctx)
                Stabilizer.Verdict.Missing -> {
                    val reason = if (step.mode == StableMode.HIDDEN) "reappeared" else "missing"
                    return Outcome(State.Rejected(FlowError.InvalidTarget(step.name, selector, reason, clock() - start, attempts)), ctx)
                }
                Stabilizer.Verdict.Pending -> Unit
            }
            val elapsed = clock() - start
            val exhausted = policy.times != PollPolicy.UNBOUNDED && attempts >= policy.times
            val timedOut = policy.timeout != PollPolicy.UNBOUNDED && elapsed > policy.timeout
            if (exhausted || timedOut) {
                val reason = when {
                    clipped && timedOut -> "chain"
                    step.mode == StableMode.HIDDEN -> if (sample == null) "visible" else "unstable"
                    else -> stabilizer.reason
                }
                return Outcome(State.Rejected(FlowError.Timeout(step.name, selector, elapsed, attempts, reason)), ctx)
            }
            sleep(policy.intervalAfter(attempts - 1))
        }
    }

    private fun runAction(step: FlowStep.Action, target: Any?, ctx: FlowContext): Outcome {
        pause(step.humanize, ctx)
        val result = bindings.perform(step.action, target, step.args)
            ?: return Outcome(State.Rejected(FlowError.InvalidTarget(step.name, null, "not an actionable target")), ctx)
        return when {
            result -> Outcome(State.Fulfilled(target), ctx)
            step.strict -> Outcome(State.Rejected(FlowError.ActionFailed(step.name)), ctx)
            else -> Outcome(State.Fulfilled(false), ctx)
        }
    }

    private fun runGlobalAction(step: FlowStep.GlobalAction, input: State, ctx: FlowContext): Outcome {
        pause(step.humanize, ctx)
        val result = bindings.performGlobal(step.action, step.args)
        return when {
            !result && step.strict -> Outcome(State.Rejected(FlowError.ActionFailed(step.name)), ctx)
            else -> Outcome(input, ctx)
        }
    }

    /** The random pause of [humanize] before an action, clipped to the chain deadline. zh-CN: 动作前 [humanize] 的随机暂停, 截短到链的截止. */
    private fun pause(humanize: Humanize, ctx: FlowContext) {
        val delay = humanize.delay(random())
        if (delay > 0) sleep(minOf(delay, ctx.remainingUntilDeadline(clock()).coerceAtLeast(0)))
    }

    /** A tool call: its result is the value; the tool's own `FlowError`s (or anything else it throws) reject through [execute]. zh-CN: 工具调用: 结果即新值; 工具自身的 `FlowError` (或任何其它异常) 经 [execute] 成为拒绝. */
    private fun runTool(step: FlowStep.Tool, value: Any?, ctx: FlowContext): Outcome = Outcome(State.Fulfilled(step.block(value, ctx.scope)), ctx)

    private fun runSleep(node: FlowNode, step: FlowStep.Sleep, input: State, ctx: FlowContext): Outcome {
        val millis = (step.min + ceil(random() * (step.max - step.min))).toLong().coerceIn(step.min, step.max)
        ensureNotCancelled(node, step.name, null, 0, 0)
        sleep(minOf(millis, ctx.remainingUntilDeadline(clock()).coerceAtLeast(0)))
        return Outcome(input, ctx)
    }

    private fun runCallback(node: FlowNode, step: FlowStep.Callback, input: State, ctx: FlowContext, done: (Outcome) -> Unit) {
        val passThrough = Outcome(input, ctx)
        fun complete(result: Any?) {
            when (val adopted = bindings.adopt(result)) {
                null -> done(Outcome(State.Fulfilled(result), ctx))
                else -> adopt(node, adopted) { done(Outcome(it, ctx)) }
            }
        }
        when (step.kind) {
            CallbackKind.FINALLY -> {
                if (step.onOk == null) return done(passThrough)
                when (val adopted = bindings.adopt(bindings.invoke(step.onOk, emptyList()))) {
                    null -> done(passThrough)
                    else -> adopt(node, adopted) { done(if (it is State.Rejected) Outcome(it, ctx) else passThrough) }
                }
            }
            CallbackKind.CATCH -> when {
                input is State.Rejected && step.onErr != null -> complete(bindings.invoke(step.onErr, listOf(input.error)))
                else -> done(passThrough)
            }
            CallbackKind.FILTER -> when {
                input is State.Fulfilled && step.onOk != null -> {
                    val kept = bindings.isTruthy(bindings.invoke(step.onOk, argumentsFor(node, input.value)))
                    done(if (kept) passThrough else Outcome(State.Rejected(FlowError.InvalidTarget(step.name, null, "filtered out")), ctx))
                }
                else -> done(passThrough)
            }
            CallbackKind.PEEK -> {
                if (input is State.Fulfilled && step.onOk != null) bindings.invoke(step.onOk, argumentsFor(node, input.value))
                done(passThrough)
            }
            CallbackKind.THEN, CallbackKind.MAP, CallbackKind.RUN -> when {
                input is State.Fulfilled && step.onOk != null -> complete(bindings.invoke(step.onOk, argumentsFor(node, input.value)))
                input is State.Rejected && step.onErr != null -> complete(bindings.invoke(step.onErr, listOf(input.error)))
                else -> done(passThrough)
            }
        }
    }

    /** A root callback gets no argument (`flow.run(fn)`); a chained one gets the value. zh-CN: 根回调不带参数 (`flow.run(fn)`); 链上的回调得到值. */
    private fun argumentsFor(node: FlowNode, value: Any?): List<Any?> = if (node.parent == null) emptyList() else listOf(value)

    private fun adopt(node: FlowNode, adopted: FlowNode, onSettled: (State) -> Unit) {
        synchronized(lock) { node.owned += adopted }
        adopted.onSettled(onSettled)
    }

    private fun runCombinator(node: FlowNode, step: FlowStep.Combinator, ctx: FlowContext, done: (Outcome) -> Unit) {
        val created = ArrayList<FlowNode>()
        val sources = step.sources.map { source ->
            (source as? FlowNode) ?: bindings.adopt(source) ?: start(FlowStep.Wait(source, step.policy)).also { created += it }
        }
        synchronized(lock) { node.owned += created }
        if (sources.isEmpty()) {
            return done(
                when (step.kind) {
                    CombinatorKind.ALL -> Outcome(State.Fulfilled(emptyList<Any?>()), ctx)
                    else -> Outcome(State.Rejected(FlowError.InvalidTarget(step.name, null, "no sources")), ctx)
                }
            )
        }
        val results = arrayOfNulls<Any?>(sources.size)
        var unsettled = sources.size
        var decided = false
        val local = Any()
        sources.forEachIndexed { index, source ->
            source.onSettled { state ->
                val outcome = synchronized(local) {
                    if (decided) return@onSettled
                    unsettled -= 1
                    when (state) {
                        is State.Fulfilled -> {
                            results[index] = state.value
                            when (step.kind) {
                                CombinatorKind.ALL -> if (unsettled == 0) State.Fulfilled(results.toList()) else null
                                CombinatorKind.RACE, CombinatorKind.ANY -> state
                            }
                        }
                        is State.Rejected -> when (step.kind) {
                            CombinatorKind.ALL, CombinatorKind.RACE -> state
                            CombinatorKind.ANY -> if (unsettled == 0) state else null
                        }
                        is State.Pending -> null
                    }?.also { decided = true }
                } ?: return@onSettled
                done(Outcome(outcome, ctx))
                created.forEach { if (it !== source) cancel(it) }
            }
        }
    }

    private fun runRetry(node: FlowNode, step: FlowStep.Retry, input: State, ctx: FlowContext, done: (Outcome) -> Unit) {
        val error = (input as? State.Rejected)?.error
        val target = node.parent
        if (error == null || error is FlowError.Cancelled || target == null || !isRerunnable(target.step)) {
            return done(Outcome(input, ctx))
        }
        val (targetInput, targetContext) = inputOf(target)
        var remaining = step.times
        var last: State = input
        lateinit var attempt: () -> Unit
        val rerun = {
            val lane = laneFor(target, targetInput)
            val run = Runnable {
                execute(node, target.step, targetInput, targetContext, blocking = lane === target.worker) { outcome ->
                    val state = outcome.state
                    when {
                        state is State.Fulfilled || state is State.Rejected && state.error is FlowError.Cancelled -> done(outcome)
                        else -> {
                            last = state
                            attempt()
                        }
                    }
                }
            }
            when (lane) {
                null -> run.run()
                else -> lane.post(run)
            }
        }
        attempt = {
            when {
                remaining <= 0 -> done(Outcome(last, ctx))
                else -> {
                    remaining -= 1
                    when {
                        step.delay <= 0 -> rerun()
                        else -> node.worker.post {
                            val delayed = runCatching {
                                asWorkerTask(node) {
                                    ensureNotCancelled(node, step.name, null, 0, (step.times - remaining).toLong())
                                    sleep(minOf(step.delay, ctx.remainingUntilDeadline(clock()).coerceAtLeast(0)))
                                }
                            }
                            delayed.exceptionOrNull()?.let { e ->
                                val error = if (e is InterruptedException || isCancelRequested(node)) cancelledError(node, step) else e
                                return@post done(Outcome(State.Rejected(error), ctx))
                            }
                            rerun()
                        }
                    }
                }
            }
        }
        attempt()
    }

    private fun isRerunnable(step: FlowStep): Boolean = when (step) {
        is FlowStep.Wait, is FlowStep.WaitStable, is FlowStep.Action, is FlowStep.GlobalAction, is FlowStep.Tool, is FlowStep.Sleep, is FlowStep.Callback, is FlowStep.Combinator -> true
        is FlowStep.Value, is FlowStep.Adopted, is FlowStep.Scope, is FlowStep.Timeout, is FlowStep.Retry -> false
    }

    // Settlement. zh-CN: 结算.

    private fun settle(node: FlowNode, outcome: Outcome) {
        val callbacks: List<(State) -> Unit>
        val children: List<FlowNode>
        synchronized(lock) {
            if (node.stateInternal !is State.Pending) return
            node.stateInternal = outcome.state
            node.contextInternal = outcome.context
            pending.remove(node)
            callbacks = ArrayList(node.callbacks)
            node.callbacks.clear()
            children = ArrayList(node.children)
            lock.notifyAll()
        }
        listener?.onStepEnd(node, outcome.state, elapsedOf(node))
        callbacks.forEach { it(outcome.state) }
        children.forEach { launch(it) }
    }

    private fun elapsedOf(node: FlowNode): Long = if (node.startedAt < 0) 0 else clock() - node.startedAt

    private fun cancelledOutcome(node: FlowNode): Outcome = Outcome(State.Rejected(cancelledError(node, node.step)), contextOf(node))

    private fun cancelledError(node: FlowNode, step: FlowStep): FlowError.Cancelled {
        val selector = when (step) {
            is FlowStep.Wait -> bindings.describe(step.cond)
            is FlowStep.WaitStable -> bindings.describe(step.cond)
            else -> null
        }
        return FlowError.Cancelled(step.name, selector, elapsedOf(node))
    }

    private fun ensureNotCancelled(node: FlowNode, step: String, selector: String?, elapsed: Long, attempts: Long) {
        if (synchronized(lock) { node.cancelRequested }) throw FlowError.Cancelled(step, selector, elapsed, attempts)
    }

    /**
     * [policy] shortened to the chain deadline of [ctx]; a deadline already passed fails at once
     * (`TIMEOUT`, reason `chain`). The flag tells whether the deadline is the tighter bound.
     * zh-CN: 截短到 [ctx] 链截止的 [policy]; 截止已过则立即失败 (`TIMEOUT`, reason `chain`). 标志表示截止是否是更紧的约束.
     */
    private fun clipToDeadline(policy: PollPolicy, ctx: FlowContext, step: String, selector: String?): Pair<PollPolicy, Boolean> {
        val remaining = ctx.remainingUntilDeadline(clock())
        if (ctx.deadline == FlowContext.NO_DEADLINE || remaining >= policy.timeout) return policy to false
        if (remaining <= 0) throw FlowError.Timeout(step, selector, 0, 0, "chain")
        return policy.copy(timeout = remaining) to true
    }

    private fun laneFor(node: FlowNode, input: State): FlowLane? {
        val step = node.step
        val fulfilled = input is State.Fulfilled
        return when (step) {
            is FlowStep.Value, is FlowStep.Adopted, is FlowStep.Scope, is FlowStep.Timeout, is FlowStep.Combinator, is FlowStep.Retry -> null
            is FlowStep.Wait, is FlowStep.WaitStable, is FlowStep.Sleep, is FlowStep.Action, is FlowStep.GlobalAction, is FlowStep.Tool -> if (fulfilled) node.worker else null
            is FlowStep.Callback -> {
                val runs = when (step.kind) {
                    CallbackKind.FINALLY -> step.onOk != null
                    CallbackKind.CATCH -> !fulfilled && step.onErr != null
                    CallbackKind.FILTER, CallbackKind.PEEK -> fulfilled && step.onOk != null
                    CallbackKind.THEN, CallbackKind.MAP, CallbackKind.RUN -> if (fulfilled) step.onOk != null else step.onErr != null
                }
                when {
                    !runs -> null
                    step.kind == CallbackKind.RUN -> node.worker
                    else -> script
                }
            }
        }
    }

    /**
     * A custom comparator receives the samples themselves through the bindings; the built-in
     * modes reduce each sample to a key when it is observed.
     * zh-CN: 自定义比较器经 bindings 直接收到样本; 内置模式在观察到样本时把它归约为键.
     */
    private fun stabilizerFor(stability: Stability): Stabilizer = when (val compare = stability.compare) {
        is StabilityCompare.Custom -> Stabilizer(stability, { it }) { previous, next -> bindings.isTruthy(bindings.invoke(compare.fn, listOf(previous, next))) }
        else -> Stabilizer(stability, { Stabilizer.keyOf(compare, it) })
    }

    private companion object {

        /** The value a negated wait yields when the probe finds nothing. zh-CN: 取反等待在探测无所得时产生的值. */
        val SATISFIED = Any()

        /** The sample of a hidden target in `waitForHidden`. zh-CN: `waitForHidden` 中目标隐藏时的样本. */
        val HIDDEN = Any()

    }

}
