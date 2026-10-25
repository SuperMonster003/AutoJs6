package org.autojs.autojs.runtime.api.modules.flow

import org.autojs.autojs.core.automator.flow.CallbackKind
import org.autojs.autojs.core.automator.flow.CombinatorKind
import org.autojs.autojs.core.automator.flow.FlowError
import org.autojs.autojs.core.automator.flow.FlowNode
import org.autojs.autojs.core.automator.flow.FlowScope
import org.autojs.autojs.core.automator.flow.FlowStep
import org.autojs.autojs.core.automator.flow.StableMode
import org.autojs.autojs.core.automator.query.PollPolicy
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsArray
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.augment.automator.EventWaitApi
import org.autojs.autojs.runtime.api.modules.global.Global
import org.autojs.autojs.runtime.api.modules.promise.PromiseSuspension
import org.autojs.autojs.runtime.api.modules.toJsPromise
import org.autojs.autojs.runtime.api.modules.toolkit.RuntimeToolkit
import org.autojs.autojs.runtime.api.modules.toolkit.ToolkitApi
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.runtime.exception.WrappedRuntimeException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContinuationPending
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.ScriptableObject.DONTENUM
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The JavaScript `Flow` (roadmap Appendix A): the handle of one [FlowNode]. Every chain method
 * lives on the shared [FlowPrototype] and appends a step after this node, returning the new
 * handle, so a chain is immutable and can fork; `then` / `catch` / `finally` are callback steps,
 * which also makes a Flow a thenable that `Promise.resolve` and `Promise.all` adopt. The
 * properties `state` / `step` / `value` / `error` read the node.
 * zh-CN: JavaScript 的 `Flow` (Roadmap 附录 A): 一个 [FlowNode] 的句柄. 全部链式方法在共享的 [FlowPrototype] 上, 在本节点之后追加
 * 一个步骤并返回新句柄, 因此链不可变且可分叉; `then` / `catch` / `finally` 是回调步骤, 这也使 Flow 成为 `Promise.resolve` 与
 * `Promise.all` 能采纳的 thenable. 属性 `state` / `step` / `value` / `error` 读取节点.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
internal class FlowNativeObject(val executor: FlowExecutor, val node: FlowNode) : NativeObject() {

    /** Whether `sync` / `await` / `toPromise` took the outcome, so a rejection is not unhandled. zh-CN: `sync` / `await` / `toPromise` 是否已取走结果, 使拒绝不算未处理. */
    @Volatile
    var observed = false

    init {
        prototype = executor.prototype.proto
        parentScope = executor.scriptRuntime.topLevelScope
        defineProperty("state", { stateName() }, null, READONLY or PERMANENT or DONTENUM)
        defineProperty("step", { node.step.name }, null, READONLY or PERMANENT or DONTENUM)
        defineProperty("value", { executor.toJs(node.value) }, null, READONLY or PERMANENT or DONTENUM)
        defineProperty("error", { node.error?.let { executor.toJs(it) } }, null, READONLY or PERMANENT or DONTENUM)
    }

    override fun getClassName(): String = "Flow"

    fun stateName(): String = when {
        node.isCancelled -> "cancelled"
        node.isRejected -> "rejected"
        node.isFulfilled -> "fulfilled"
        else -> "pending"
    }

    override fun toString(): String = "Flow(${node.step.name}, ${stateName()})"

}

/** The wait forms of the API, each a step kind. zh-CN: API 的各种等待形式, 各对应一种步骤. */
internal enum class WaitKind {
    WAIT, UNTIL, WHILE, WITHIN, STABLE, VISIBLE, HIDDEN, ACTIVITY, PACKAGE;

    val isStability: Boolean get() = this == STABLE || this == VISIBLE || this == HIDDEN
}

/**
 * The operations of the Flow API over an executor: builds the steps from JavaScript arguments
 * (through [FlowArguments]), appends them after a parent handle or starts a root, and implements
 * the terminal calls. Shared by the prototype methods, the `flow` namespace and the global
 * functions.
 * zh-CN: 基于执行器的 Flow API 操作: 由 JavaScript 参数构造步骤 (经 [FlowArguments]), 追加到父句柄之后或作为根启动, 并实现终结调用.
 * 由原型方法, `flow` 命名空间与全局函数共用.
 */
internal class FlowApi(val executor: FlowExecutor) {

    private val scriptRuntime get() = executor.scriptRuntime

    private val arguments get() = executor.arguments

    /** [step] after [parent], or as a root. zh-CN: [parent] 之后的 [step], 或作为根. */
    fun start(step: FlowStep, parent: FlowNativeObject?): FlowNativeObject {
        val effective = assisted(step)
        return executor.wrap(parent?.node?.next(effective) ?: executor.scheduler().start(effective))
    }

    /**
     * A wait step woken early by accessibility events under the flag `eventAssistedPolling` (roadmap P5.2; on by
     * default since 6.8.0, docs/selector/ROADMAP.md D6 (a)), at least 50 ms after its attempt before (D9); other steps unchanged.
     * zh-CN: 标志 `eventAssistedPolling` 下由无障碍事件提前唤醒的等待步骤 (Roadmap P5.2; 自 6.8.0 起默认开启, D6 (a)), 距上一次尝试至少 50 ms (D9);
     * 其它步骤不变.
     */
    private fun assisted(step: FlowStep): FlowStep = when {
        step is FlowStep.Wait && !step.policy.eventAssisted && scriptRuntime.accessibilityBridge.isEventAssistedPolling -> step.copy(policy = step.policy.copy(eventAssisted = true))
        else -> step
    }

    private fun withCallbacks(flow: FlowNativeObject, onOk: Callable?, onErr: Callable?): FlowNativeObject = when {
        onOk == null && onErr == null -> flow
        else -> start(FlowStep.Callback(CallbackKind.THEN, onOk, onErr), flow)
    }

    // Event-driven waits (roadmap P5.2). zh-CN: 事件驱动等待 (Roadmap P5.2).

    /** `waitForIdle` / `waitForEvent` / `waitForToast` / `waitForNotification` as a root or a chain step. zh-CN: 作为起点或链式步骤的 `waitForIdle` / `waitForEvent` / `waitForToast` / `waitForNotification`. */
    fun eventWait(parent: FlowNativeObject?, name: String, args: Array<out Any?>): FlowNativeObject =
        start(EventWaitApi(scriptRuntime).step(name, args, chained = parent != null), parent)

    // Waits. zh-CN: 等待.

    fun wait(parent: FlowNativeObject?, kind: WaitKind, name: String, args: Array<out Any?>): FlowNativeObject {
        val spec = specOf(kind, name, args, withDelay = false)
        return withCallbacks(start(stepOf(kind, spec), parent), spec.onOk, spec.onErr)
    }

    fun whenPresent(parent: FlowNativeObject?, args: Array<out Any?>): FlowNativeObject = start(arguments.whenPresent("whenPresent", args), parent)

    fun repeatUntil(parent: FlowNativeObject?, args: Array<out Any?>): FlowNativeObject = start(arguments.repeatUntil("repeatUntil", args), parent)

    /** A wait followed by [action] (`waitThenClick`, `clickWhenStable`, ...), with a sleep between when [after]. zh-CN: 等待后接 [action] (`waitThenClick`, `clickWhenStable`, ...), [after] 时中间加休眠. */
    fun waitThen(parent: FlowNativeObject?, kind: WaitKind, name: String, args: Array<out Any?>, action: String, after: Boolean): FlowNativeObject {
        val spec = specOf(kind, name, args, withDelay = after)
        var flow = start(stepOf(kind, spec), parent)
        if (after) flow = start(spec.delay ?: throw WrappedIllegalArgumentException("Argument \"delay\" is required for $name"), flow)
        flow = start(FlowStep.Action(action, emptyList(), spec.strict, executor.defaults.humanize), flow)
        return withCallbacks(flow, spec.onOk, spec.onErr)
    }

    private fun specOf(kind: WaitKind, name: String, args: Array<out Any?>, withDelay: Boolean): WaitSpec = when (kind) {
        WaitKind.ACTIVITY -> arguments.waitNative(name, args) { Global.currentActivity(scriptRuntime, emptyArray()) == it }
        WaitKind.PACKAGE -> arguments.waitNative(name, args) { Global.currentPackage(scriptRuntime, emptyArray()) == it }
        else -> arguments.wait(name, args, withDelay)
    }

    private fun stepOf(kind: WaitKind, spec: WaitSpec): FlowStep = when (kind) {
        WaitKind.WAIT, WaitKind.UNTIL, WaitKind.ACTIVITY, WaitKind.PACKAGE -> FlowStep.Wait(spec.cond, spec.policy, recoverService = spec.recoverService)
        WaitKind.WHILE -> FlowStep.Wait(spec.cond, spec.policy, negate = true, recoverService = spec.recoverService)
        WaitKind.WITHIN -> FlowStep.Wait(spec.cond, spec.policy, within = true, recoverService = spec.recoverService)
        WaitKind.STABLE -> FlowStep.WaitStable(spec.cond, spec.policy, spec.stability, StableMode.STABLE, spec.recoverService)
        WaitKind.VISIBLE -> FlowStep.WaitStable(spec.cond, spec.policy, spec.stability, StableMode.VISIBLE, spec.recoverService)
        WaitKind.HIDDEN -> FlowStep.WaitStable(spec.cond, spec.policy, spec.stability, StableMode.HIDDEN, spec.recoverService)
    }

    // Actions. zh-CN: 动作.

    fun action(parent: FlowNativeObject, name: String, args: Array<out Any?>): FlowNativeObject {
        val (actionArgs, callbacks) = arguments.actionArguments(name, args)
        checkArity(name, actionArgs.size, FlowActions.NODE_ACTIONS.getValue(name))
        return withCallbacks(start(FlowStep.Action(name, actionArgs, executor.defaults.strictActions, executor.defaults.humanize), parent), callbacks.first, callbacks.second)
    }

    /** `clickAfter(min, max?)` and the like: a sleep, then [action]. zh-CN: `clickAfter(min, max?)` 一类: 先休眠再 [action]. */
    fun actionAfter(parent: FlowNativeObject, name: String, action: String, args: Array<out Any?>): FlowNativeObject {
        val (sleepArgs, callbacks) = arguments.actionArguments(name, args)
        val sleep = arguments.sleep(name, sleepArgs.toTypedArray())
        val flow = start(FlowStep.Action(action, emptyList(), executor.defaults.strictActions, executor.defaults.humanize), start(sleep, parent))
        return withCallbacks(flow, callbacks.first, callbacks.second)
    }

    fun globalAction(parent: FlowNativeObject?, name: String, args: Array<out Any?>): FlowNativeObject {
        val (actionArgs, callbacks) = arguments.actionArguments(name, args)
        checkArity(name, actionArgs.size, FlowActions.GLOBAL_ACTIONS.getValue(name))
        return withCallbacks(start(FlowStep.GlobalAction(name, actionArgs, executor.defaults.strictActions, executor.defaults.humanize), parent), callbacks.first, callbacks.second)
    }

    // Toolkit steps (roadmap P4.2). zh-CN: 工具步骤 (Roadmap P4.2).

    /**
     * A tool as a step: after [parent] the current value is its target (`.smartClick(options?)`), as a
     * root (`flow.smartClick(target, options?)`) the target is the first argument and is waited for by
     * the defaults; trailing functions are `onOk` / `onErr`.
     * zh-CN: 作为步骤的工具: 在 [parent] 之后以当前值为目标 (`.smartClick(options?)`), 作为根 (`flow.smartClick(target, options?)`) 以首个
     * 参数为目标并按默认值等待; 尾随的函数为 `onOk` / `onErr`.
     */
    fun tool(parent: FlowNativeObject?, name: String, args: Array<out Any?>): FlowNativeObject {
        val (step, callbacks) = RuntimeToolkit.of(scriptRuntime).api.step(name, args, chained = parent != null)
        return withCallbacks(start(step, parent), callbacks.first, callbacks.second)
    }

    private fun checkArity(name: String, count: Int, range: IntRange) {
        require(count in range) {
            val expected = if (range.first == range.last) "${range.first}" else if (range.last == Int.MAX_VALUE) "at least ${range.first}" else "${range.first} to ${range.last}"
            "Invalid number of arguments ($count) for $name, expected $expected"
        }
    }

    // Timing, scope and callbacks. zh-CN: 计时, 作用域与回调.

    fun sleep(parent: FlowNativeObject?, name: String, args: Array<out Any?>): FlowNativeObject = start(arguments.sleep(name, args), parent)

    fun timeout(parent: FlowNativeObject, args: Array<out Any?>): FlowNativeObject = start(arguments.timeout("timeout", args), parent)

    fun retry(parent: FlowNativeObject, args: Array<out Any?>): FlowNativeObject = start(arguments.retry("retry", args), parent)

    fun scope(parent: FlowNativeObject, args: Array<out Any?>): FlowNativeObject = start(FlowStep.Scope(arguments.scope(args.getOrNull(0), "scope")), parent)

    fun callback(parent: FlowNativeObject?, kind: CallbackKind, name: String, args: Array<out Any?>): FlowNativeObject {
        val (first, second) = arguments.callbacks(name, args)
        val step = when (kind) {
            CallbackKind.THEN -> FlowStep.Callback(kind, first, second)
            CallbackKind.CATCH -> {
                require(second == null) { "Only one callback is accepted by $name" }
                FlowStep.Callback(kind, null, first)
            }
            else -> {
                require(first != null) { "Argument \"fn\" is required for $name" }
                require(second == null) { "Only one callback is accepted by $name" }
                FlowStep.Callback(kind, first, null)
            }
        }
        return start(step, parent)
    }

    // Roots. zh-CN: 根.

    fun of(value: Any?): FlowNativeObject = executor.wrap(executor.scheduler().of(RhinoUtils.unwrap(value)))

    fun combinator(kind: CombinatorKind, name: String, args: Array<out Any?>): FlowNativeObject {
        val sources = arguments.sources(name, args).map { source ->
            when {
                source is FlowNativeObject -> source.node
                executor.isThenable(source) -> source
                else -> executor.conditionOf(source, name, FlowScope.NONE)
            }
        }
        val policy = arguments.policy(name, null, null, null)
        return executor.wrap(executor.scheduler().start(FlowStep.Combinator(kind, sources, policy)))
    }

    /** `flow.Flow(x)` / `new flow.Flow(x)`: a flow as is, a thenable adopted, anything else `of`. zh-CN: `flow.Flow(x)` / `new flow.Flow(x)`: Flow 原样, thenable 采纳, 其余为 `of`. */
    fun from(args: Array<out Any?>): FlowNativeObject {
        val value = args.getOrNull(0)
        return when {
            value is FlowNativeObject -> value
            executor.isThenable(value) -> executor.wrap(executor.adoptThenable(value as Scriptable))
            else -> of(value)
        }
    }

    // Defaults and tracing (roadmap P3.3). zh-CN: 默认值与追踪 (Roadmap P3.3).

    /** `flow.defaults()` reads, `flow.defaults(options)` writes; both return a snapshot object. zh-CN: `flow.defaults()` 读取, `flow.defaults(options)` 写入; 都返回快照对象. */
    fun defaults(args: Array<out Any?>): Scriptable {
        require(args.size <= 1) { "Too many arguments (${args.size}) for flow.defaults" }
        val options = args.getOrNull(0)
        if (!options.isJsNullish()) {
            require(options is ScriptableObject && !options.isJsArray() && options !is Callable) { "Argument \"options\" must be an object for flow.defaults, got ${options.jsBrief()}" }
            arguments.applyDefaults(options, "flow.defaults")?.let { executor.tracing = it }
        }
        val d = executor.defaults
        return RhinoUtils.newNativeObject(scriptRuntime.topLevelScope).apply {
            put("timeout", this, if (d.timeout == PollPolicy.UNBOUNDED) Double.POSITIVE_INFINITY else d.timeout.toDouble())
            put("interval", this, d.interval.toDouble())
            put("stableFor", this, d.stableFor.toDouble())
            put("strictActions", this, d.strictActions)
            put("humanize", this, ToolkitApi.humanizeSnapshot(scriptRuntime.topLevelScope, d.humanize))
            put("recoverService", this, d.recoverService)
            put("trace", this, executor.tracing)
        }
    }

    /** `flow.trace(on?)`: switches the console trace; returns whether it is on. zh-CN: `flow.trace(on?)`: 开关控制台追踪; 返回是否开启. */
    fun trace(args: Array<out Any?>): Boolean {
        require(args.size <= 1) { "Too many arguments (${args.size}) for flow.trace" }
        args.getOrNull(0).takeUnless { it.isJsNullish() }?.let { executor.tracing = Context.toBoolean(it) }
        return executor.tracing
    }

    // Terminals. zh-CN: 终结.

    /**
     * Blocks until settled: the value, or the rejection thrown. While blocked, the calling
     * thread drains its own script lane, so the callbacks of the awaited chain (which belong to
     * this very thread) still run. Forbidden on the UI thread and on the flow worker thread.
     * zh-CN: 阻塞直到结算: 返回值, 或抛出拒绝. 阻塞期间调用线程排空自己的脚本 lane, 因此所等待链的回调 (它们属于这条线程) 仍能运行.
     * UI 线程与 Flow 工作线程禁止.
     */
    fun sync(self: FlowNativeObject, args: Array<out Any?>): Any? {
        require(!RhinoUtils.isUiThread()) { str(R.string.error_function_called_in_ui_thread, "sync") }
        require(!executor.isWorkerThread()) { "sync() cannot be called on the flow worker thread (inside a run() callback); chain then() instead" }
        require(args.size <= 1) { "Too many arguments (${args.size}) for sync" }
        val timeout = args.getOrNull(0).takeUnless { it.isJsNullish() }?.let {
            val n = Context.toNumber(it)
            require(!n.isNaN() && n >= 0) { "Argument \"timeout\" must be a non-negative number for sync, got ${it.jsBrief()}" }
            if (n.isInfinite()) -1L else n.toLong()
        } ?: -1L
        self.observed = true
        return RhinoUtils.withRhinoContext(scriptRuntime) { cx ->
            try {
                executor.toJs(awaitPumping(self.node, timeout))
            } catch (e: Throwable) {
                if (ScriptInterruptedException.causedByInterrupt(e)) throw ScriptInterruptedException(e)
                FlowJsErrors.throwInto(cx, scriptRuntime.topLevelScope, e)
            }
        }
    }

    /** [FlowNode.await] that keeps draining the calling thread's script lane. zh-CN: 持续排空调用线程脚本 lane 的 [FlowNode.await]. */
    private fun awaitPumping(node: FlowNode, timeoutMillis: Long): Any? {
        val lane = executor.scriptLane() as? FlowScriptLane ?: return node.await(timeoutMillis)
        node.onSettled { lane.wakeUp() }
        val start = System.currentTimeMillis()
        while (true) {
            lane.runPending()
            when (val state = FlowArguments.settledOrNull(node)) {
                is FlowNode.State.Fulfilled -> return state.value
                is FlowNode.State.Rejected -> throw state.error
                else -> Unit
            }
            val seen = lane.signal
            lane.runPending()
            if (FlowArguments.settledOrNull(node) != null) continue
            val left = if (timeoutMillis < 0) Long.MAX_VALUE else timeoutMillis - (System.currentTimeMillis() - start)
            if (left <= 0) throw FlowError.Timeout("sync", null, timeoutMillis, 0, "sync")
            lane.awaitSignal(seen, minOf(left, 1000L))
        }
    }

    /**
     * The continuation-mode wait of `promise.await()`, suspending from Kotlin: calling the script `await` of the Promise
     * opened a new top frame that cannot be captured (docs/augment/ROADMAP.md Q97).
     * zh-CN: `promise.await()` 的协程模式等待, 由 Kotlin 直接挂起: 调用 Promise 的脚本 await 会开一个无法捕获的新顶层帧 (docs/augment/ROADMAP.md Q97).
     */
    fun await(self: FlowNativeObject): Any? = RhinoUtils.withRhinoContext(scriptRuntime) { cx ->
        PromiseSuspension.await(cx, scriptRuntime.topLevelScope, toPromise(self))
    }

    /** A polyfill Promise settled with this node, on the calling script thread. zh-CN: 以本节点结算的 polyfill Promise, 在调用方脚本线程上结算. */
    fun toPromise(self: FlowNativeObject): Scriptable {
        self.observed = true
        val adapter = ScriptPromiseAdapter()
        val promise = adapter.toJsPromise(scriptRuntime) as Scriptable
        val lane = executor.scriptLane()
        self.node.onSettled { state ->
            lane.post {
                when (state) {
                    is FlowNode.State.Fulfilled -> adapter.resolve(executor.toJs(state.value))
                    is FlowNode.State.Rejected -> adapter.reject(executor.toJs(state.error))
                    is FlowNode.State.Pending -> Unit
                }
            }
        }
        return promise
    }

    fun cancel(self: FlowNativeObject): Boolean = self.node.cancel()

}

/**
 * The prototype shared by the `Flow` objects of one script and the `flow.Flow` constructor
 * (`x instanceof flow.Flow`). Built once, on the script thread, with one [Method] per chain
 * method of roadmap Appendix A.3 and its aliases.
 * zh-CN: 一个脚本的 `Flow` 对象共享的原型与 `flow.Flow` 构造器 (`x instanceof flow.Flow`). 在脚本线程上构建一次, Roadmap 附录 A.3
 * 的每个链式方法及其别名各一个 [Method].
 */
internal class FlowPrototype(private val executor: FlowExecutor) {

    private val scope: VarScope = executor.scriptRuntime.topLevelScope

    val proto: NativeObject = RhinoUtils.newNativeObject(scope)

    val constructor: BaseFunction

    private val api: FlowApi get() = executor.api

    init {
        method("wait") { self, args -> api.wait(self, WaitKind.WAIT, "wait", args) }
        method("waitUntil") { self, args -> api.wait(self, WaitKind.UNTIL, "waitUntil", args) }
        method("waitWhile") { self, args -> api.wait(self, WaitKind.WHILE, "waitWhile", args) }
        method("within") { self, args -> api.wait(self, WaitKind.WITHIN, "within", args) }
        method("waitForStable") { self, args -> api.wait(self, WaitKind.STABLE, "waitForStable", args) }
        method("waitForVisible") { self, args -> api.wait(self, WaitKind.VISIBLE, "waitForVisible", args) }
        method("waitForHidden", "waitForGone") { self, args -> api.wait(self, WaitKind.HIDDEN, "waitForHidden", args) }
        method("waitForActivity") { self, args -> api.wait(self, WaitKind.ACTIVITY, "waitForActivity", args) }
        method("waitForPackage") { self, args -> api.wait(self, WaitKind.PACKAGE, "waitForPackage", args) }

        method("waitThenClick", "clickWait") { self, args -> api.waitThen(self, WaitKind.WAIT, "waitThenClick", args, "click", after = false) }
        method("waitThenClickBounds", "clickBoundsWait") { self, args -> api.waitThen(self, WaitKind.WAIT, "waitThenClickBounds", args, "clickBounds", after = false) }
        method("waitThenLongClick") { self, args -> api.waitThen(self, WaitKind.WAIT, "waitThenLongClick", args, "longClick", after = false) }
        method("waitForStableThenClick", "clickWhenStable") { self, args -> api.waitThen(self, WaitKind.STABLE, "clickWhenStable", args, "click", after = false) }
        method("waitForStableThenClickBounds", "clickBoundsWhenStable") { self, args -> api.waitThen(self, WaitKind.STABLE, "waitForStableThenClickBounds", args, "clickBounds", after = false) }
        method("clickWhenStableAfter") { self, args -> api.waitThen(self, WaitKind.STABLE, "clickWhenStableAfter", args, "click", after = true) }
        method("clickBoundsWhenStableAfter") { self, args -> api.waitThen(self, WaitKind.STABLE, "clickBoundsWhenStableAfter", args, "clickBounds", after = true) }

        FlowActions.NODE_ACTIONS.keys.forEach { name -> method(name) { self, args -> api.action(self, name, args) } }
        method("clickAfter") { self, args -> api.actionAfter(self, "clickAfter", "click", args) }
        method("clickBoundsAfter") { self, args -> api.actionAfter(self, "clickBoundsAfter", "clickBounds", args) }
        method("longClickAfter") { self, args -> api.actionAfter(self, "longClickAfter", "longClick", args) }
        FlowActions.GLOBAL_ACTIONS.keys.forEach { name -> method(name) { self, args -> api.globalAction(self, name, args) } }

        ToolkitApi.CHAIN_STEPS.forEach { name -> method(name) { self, args -> api.tool(self, name, args) } }
        EventWaitApi.STEPS.forEach { name -> method(name) { self, args -> api.eventWait(self, name, args) } }

        method("sleep", "delay") { self, args -> api.sleep(self, "sleep", args) }
        method("timeout") { self, args -> api.timeout(self, args) }
        method("retry") { self, args -> api.retry(self, args) }
        method("whenPresent") { self, args -> api.whenPresent(self, args) }
        method("repeatUntil") { self, args -> api.repeatUntil(self, args) }
        method("scope") { self, args -> api.scope(self, args) }

        method("then") { self, args -> api.callback(self, CallbackKind.THEN, "then", args) }
        method("catch", "else") { self, args -> api.callback(self, CallbackKind.CATCH, "catch", args) }
        method("finally") { self, args -> api.callback(self, CallbackKind.FINALLY, "finally", args) }
        method("map") { self, args -> api.callback(self, CallbackKind.MAP, "map", args) }
        method("filter") { self, args -> api.callback(self, CallbackKind.FILTER, "filter", args) }
        method("peek") { self, args -> api.callback(self, CallbackKind.PEEK, "peek", args) }
        method("run") { self, args -> api.callback(self, CallbackKind.RUN, "run", args) }

        method("sync") { self, args -> api.sync(self, args) }
        method("await") { self, _ -> api.await(self) }
        method("toPromise") { self, _ -> api.toPromise(self) }
        method("cancel") { self, _ -> api.cancel(self) }
        method("toString") { self, _ -> self.toString() }

        constructor = Constructor(scope, proto) { args -> api.from(args) }
        proto.defineProperty("constructor", constructor, DONTENUM)
    }

    private fun method(name: String, vararg aliases: String, body: (FlowNativeObject, Array<out Any?>) -> Any?) {
        val f = Method(scope, name, body)
        proto.defineProperty(name, f, DONTENUM or READONLY or PERMANENT)
        aliases.forEach { proto.defineProperty(it, f, DONTENUM or READONLY or PERMANENT) }
    }

    /** A prototype method: [body] gets the receiver; Java exceptions become script errors. zh-CN: 原型方法: [body] 得到接收者; Java 异常成为脚本错误. */
    private class Method(scope: VarScope, private val name: String, private val body: (FlowNativeObject, Array<out Any?>) -> Any?) : BaseFunction() {

        init {
            RhinoUtils.initNewBaseFunction(scope, this)
        }

        override fun getFunctionName(): String = name

        override fun call(cx: Context, nt: Any?, scope: VarScope, thisObj: Any?, args: Array<out Any?>): Any? {
            // Not through the JavaScript toString of thisObj: on the prototype itself that is this very method.
            // zh-CN: 不经 thisObj 的 JavaScript toString: 在原型自身上那就是本方法.
            val self = thisObj as? FlowNativeObject ?: throw RhinoScriptRuntime.typeError("Flow.prototype.$name called on ${describe(thisObj)}, not a Flow")
            return guarded { body(self, args) }
        }

    }

    /** `flow.Flow`: callable and constructable, with [proto] as its `prototype`. zh-CN: `flow.Flow`: 可调用亦可构造, `prototype` 为 [proto]. */
    private class Constructor(scope: VarScope, proto: NativeObject, private val body: (Array<out Any?>) -> FlowNativeObject) : BaseFunction() {

        init {
            // Before initNewBaseFunction: it reads `prototype`, and installs a plain default object
            // in the slot map when that is still undefined, which would shadow the one set later.
            // zh-CN: 须在 initNewBaseFunction 之前: 它读取 `prototype`, 若仍为 undefined 则在槽表中装入一个普通默认对象, 遮蔽之后设置的.
            prototypeProperty = proto
            setPrototypePropertyAttributes(DONTENUM or READONLY or PERMANENT)
            RhinoUtils.initNewBaseFunction(scope, this)
        }

        override fun getFunctionName(): String = "Flow"

        override fun call(cx: Context, nt: Any?, scope: VarScope, thisObj: Any?, args: Array<out Any?>): Any = guarded { body(args) }

        override fun construct(cx: Context, nt: Any?, scope: VarScope, args: Array<out Any?>): Scriptable = guarded { body(args) }

    }

    private companion object {

        fun describe(thisObj: Any?): String = when (thisObj) {
            null -> "null"
            is Scriptable -> "[object ${thisObj.className}]"
            else -> thisObj.javaClass.simpleName
        }

        inline fun <R> guarded(block: () -> R): R = try {
            block()
        } catch (e: RhinoException) {
            throw e
        } catch (e: ContinuationPending) {
            // await() suspending its coroutine is not a failure (docs/augment/ROADMAP.md Q97).
            // zh-CN: await() 挂起所在协程不是失败 (docs/augment/ROADMAP.md Q97).
            throw e
        } catch (e: ScriptInterruptedException) {
            throw e
        } catch (e: Throwable) {
            if (ScriptInterruptedException.causedByInterrupt(e)) throw ScriptInterruptedException(e)
            throw WrappedRuntimeException(e)
        }

    }

}
