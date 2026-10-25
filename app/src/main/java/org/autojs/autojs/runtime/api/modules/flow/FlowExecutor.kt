package org.autojs.autojs.runtime.api.modules.flow

import android.os.Looper
import android.os.SystemClock
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.flow.FlowError
import org.autojs.autojs.core.automator.flow.FlowLane
import org.autojs.autojs.core.automator.flow.FlowListener
import org.autojs.autojs.core.automator.flow.FlowListeners
import org.autojs.autojs.core.automator.flow.FlowNode
import org.autojs.autojs.core.automator.flow.FlowScheduler
import org.autojs.autojs.core.automator.flow.FlowScope
import org.autojs.autojs.core.automator.flow.FlowTaskStack
import org.autojs.autojs.core.looper.Loopers
import org.autojs.autojs.core.looper.Timer
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import java.io.Closeable
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Flow machinery of one [ScriptRuntime] (roadmap P3.2): a lazily started worker pool
 * (threads `flow-<engineId>-<n>`; every chain root gets its own serial lane on it, so independent
 * chains block concurrently), one [FlowScheduler] per script thread that creates flows (its
 * script lane posts callbacks back to that thread through its [Timer]), the bindings over
 * `pickup` / Rhino / `UiObject`, the per-script defaults and the trace listener.
 * Every flow handed to a script holds a `waitWhenIdle` token of its creating thread until it
 * settles, so a script with pending flows does not exit; on script exit ([close], through the
 * runtime's closeable manager) every pending flow is cancelled and the worker thread stopped.
 * zh-CN: 一个 [ScriptRuntime] 的 Flow 机制 (Roadmap P3.2): 惰性启动的工作线程池 (线程名 `flow-<engineId>-<n>`; 每条链的根有自己的串行
 * lane, 因此独立的链并发阻塞), 每个创建 Flow 的脚本线程一个 [FlowScheduler] (其脚本 lane 经该线程的 [Timer] 把回调投回该线程), 基于
 * `pickup` / Rhino / `UiObject` 的
 * bindings, 每脚本的默认值与追踪监听器. 交给脚本的每条 Flow 在结算前持有其创建线程的 `waitWhenIdle` 令牌, 因此有待定 Flow 的脚本不会
 * 退出; 脚本退出时 ([close], 经运行时的 closeable 管理器) 取消全部待定 Flow 并停止工作线程.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
internal class FlowExecutor(val scriptRuntime: ScriptRuntime) : Closeable {

    val defaults = FlowDefaults()

    private val bindings = RuntimeFlowBindings(scriptRuntime, this)

    val arguments = FlowArguments(defaults) { value, name, scope -> conditionOf(value, name, scope) }

    private val poolLazy = lazy { FlowWorkerPool(scriptRuntime, "flow-${scriptRuntime.engines.myEngine().id}") }

    private val pool: FlowWorkerPool by poolLazy

    private class Entry(val scheduler: FlowScheduler, val lane: FlowScriptLane)

    private val entries: MutableMap<Timer, Entry> = Collections.synchronizedMap(WeakHashMap())

    private val closed = AtomicBoolean(false)

    /** The shared prototype and constructor of the JavaScript `Flow` objects, built on first use on the script thread. zh-CN: JavaScript `Flow` 对象共享的原型与构造器, 首次使用时在脚本线程构建. */
    val prototype: FlowPrototype by lazy { FlowPrototype(this) }

    val api: FlowApi by lazy { FlowApi(this) }

    /** The trace listener (`flow.trace`), applied to every scheduler. zh-CN: 追踪监听器 (`flow.trace`), 应用于每个调度器. */
    var listener: FlowListener? = null
        set(value) {
            field = value
            val effective = effectiveListener()
            synchronized(entries) { entries.values.forEach { it.scheduler.listener = effective } }
        }

    /** Feeds every settled step to `auto.stats` (see `A11yStats.recordStep`). zh-CN: 把每个已结算的步骤送入 `auto.stats` (见 `A11yStats.recordStep`). */
    private val statsListener = object : FlowListener {

        override fun onStepEnd(node: FlowNode, state: FlowNode.State, elapsed: Long) {
            scriptRuntime.accessibilityBridge.stats.recordStep(node.step.name, elapsed, state is FlowNode.State.Rejected)
        }

    }

    /** The stats listener plus the trace listener when tracing. zh-CN: 统计监听器, 追踪开启时再加上追踪监听器. */
    private fun effectiveListener(): FlowListener? = FlowListeners.of(statsListener, listener)

    /** `flow.trace(on)`: whether every step is logged to the console through [FlowTrace]. zh-CN: `flow.trace(on)`: 是否经 [FlowTrace] 把每个步骤记录到控制台. */
    var tracing: Boolean = false
        set(value) {
            field = value
            listener = if (value) FlowTrace(scriptRuntime.console) else null
        }

    /** Live worker threads of the pool, for diagnostics (`flow.workers`). zh-CN: 线程池的存活工作线程数, 供诊断 (`flow.workers`). */
    val workerCount: Int get() = if (poolLazy.isInitialized()) pool.threadCount else 0

    /** Whether the calling thread is a flow worker thread. zh-CN: 调用线程是否为 Flow 工作线程. */
    fun isWorkerThread(): Boolean = Thread.currentThread() is FlowWorkerPool.WorkerThread

    /** Flows not settled yet, over every script thread. zh-CN: 全部脚本线程上尚未结算的 Flow 数. */
    val pendingCount: Int get() = synchronized(entries) { entries.values.sumOf { it.scheduler.pendingCount } }

    /** The scheduler of the calling script thread (created on first use). zh-CN: 调用方脚本线程的调度器 (首次使用时创建). */
    fun scheduler(): FlowScheduler = entry().scheduler

    /** The script lane of the calling script thread. zh-CN: 调用方脚本线程的脚本 lane. */
    fun scriptLane(): FlowLane = entry().lane

    private fun entry(): Entry {
        check(!closed.get()) { "The script is exiting; no more flows can be started" }
        val timer = scriptRuntime.timers.timerForCurrentThread ?: scriptRuntime.timers.mainTimer
        return synchronized(entries) {
            entries.getOrPut(timer) {
                val lane = FlowScriptLane(scriptRuntime, timer)
                val scheduler = FlowScheduler(pool.newLane(), lane, bindings, clock = { SystemClock.uptimeMillis() }, sleep = { Thread.sleep(it) }, workerFactory = { pool.newLane() }, eventSleep = { scriptRuntime.accessibilityBridge.pulse.awaitEvent(it) })
                scheduler.listener = effectiveListener()
                Entry(scheduler, lane)
            }
        }
    }

    /**
     * The JavaScript object of [node]: holds a wait token of the calling thread until the node
     * settles, and reports a leaf rejection nobody observes on the console (like the Promise
     * polyfill's unhandled rejection warning).
     * zh-CN: [node] 的 JavaScript 对象: 在节点结算前持有调用线程的等待令牌, 并把无人观察的叶节点拒绝报告到控制台 (同 Promise
     * polyfill 的未处理拒绝警告).
     */
    fun wrap(node: FlowNode): FlowNativeObject {
        val flow = FlowNativeObject(this, node)
        val token = waitToken()
        val lane = runCatching { scriptLane() }.getOrNull()
        node.onSettled { state ->
            token?.release()
            val error = (state as? FlowNode.State.Rejected)?.error
            if (error != null && error !is FlowError.Cancelled && lane != null) {
                lane.post { if (!flow.observed && !node.hasContinuation) warnUnhandled(node, error) }
            }
        }
        return flow
    }

    private fun waitToken(): Loopers.WaitToken? = when {
        Looper.myLooper() == null -> null
        else -> runCatching { scriptRuntime.loopers.waitWhenIdleToken() }.getOrNull()
    }

    private fun warnUnhandled(node: FlowNode, error: Throwable) {
        val message = when (error) {
            is JavaScriptException -> Context.toString(error.value)
            else -> error.message ?: error.toString()
        }
        val taskStack = FlowTaskStack.format(error)?.let { "\n$it" }.orEmpty()
        scriptRuntime.console.warn("Possible unhandled Flow rejection in step \"${node.step.name}\": $message$taskStack")
    }

    /** The JavaScript value of [value] on the calling script thread (errors per [FlowJsErrors]). zh-CN: [value] 在调用方脚本线程上的 JavaScript 值 (错误按 [FlowJsErrors]). */
    fun toJs(value: Any?): Any? = RhinoUtils.withRhinoContext(scriptRuntime) { cx -> bindings.toJs(cx, scriptRuntime.topLevelScope, value) }

    /** Cancels every pending flow of every script thread; returns how many. zh-CN: 取消全部脚本线程的全部待定 Flow; 返回个数. */
    fun cancelAll(): Int = synchronized(entries) { entries.values.toList() }.sumOf { it.scheduler.cancelAll() }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { cancelAll() }
        synchronized(entries) { entries.values.toList() }.forEach { it.lane.close() }
        if (poolLazy.isInitialized()) pool.shutdown()
    }

    // Conditions and thenables. zh-CN: 条件与 thenable.

    /** [FlowCondition] of a JavaScript condition value (roadmap Appendix A.2). zh-CN: JavaScript 条件值的 [FlowCondition] (Roadmap 附录 A.2). */
    fun conditionOf(value: Any?, name: String, scope: FlowScope): FlowCondition {
        val v = RhinoUtils.unwrap(value)
        return when {
            v == null || v is Undefined -> throw IllegalArgumentException("Argument \"cond\" is required for $name")
            v is FlowNativeObject -> FlowCondition.Settling("Flow(${v.node.step.name})") { FlowArguments.settledOrNull(v.node) }
            v is UiObject -> FlowCondition.Node(v)
            v is Callable -> FlowCondition.Predicate(v)
            v is Scriptable && isThenable(v) -> {
                var settled: FlowNode.State? = null
                hookThenable(v, { settled = FlowNode.State.Fulfilled(it) }, { settled = FlowNode.State.Rejected(it) })
                FlowCondition.Settling("Promise") { settled }
            }
            FlowCondition.isSelectorForm(v) -> FlowCondition.Selector(v, scope, FlowCondition.describeSelector(v))
            else -> throw IllegalArgumentException("Argument \"cond\" of $name cannot be ${value.jsBrief()}")
        }
    }

    /** Whether [value] has a callable `then`, so a Promise of any make. zh-CN: [value] 是否有可调用的 `then`, 即任何实现的 Promise. */
    fun isThenable(value: Any?): Boolean = value is Scriptable && value !is FlowNativeObject && ScriptableObject.getProperty(value, "then") is Callable

    /**
     * Subscribes to [thenable] with two script functions; [onFulfilled] gets the unwrapped value,
     * [onRejected] the error as a [JavaScriptException] carrying the rejection value. Must run on
     * a thread with a Rhino context.
     * zh-CN: 以两个脚本函数订阅 [thenable]; [onFulfilled] 得到解包后的值, [onRejected] 得到以 [JavaScriptException] 携带拒绝值的错误.
     * 须在有 Rhino 上下文的线程上运行.
     */
    fun hookThenable(thenable: Scriptable, onFulfilled: (Any?) -> Unit, onRejected: (Throwable) -> Unit) {
        val scope = scriptRuntime.topLevelScope
        val ok = newBaseFunction(scope, "onFulfilled", { args -> onFulfilled(RhinoUtils.unwrap(args.getOrNull(0))); UNDEFINED }, NOT_CONSTRUCTABLE)
        val err = newBaseFunction(scope, "onRejected", { args -> onRejected(rejectionOf(args.getOrNull(0))); UNDEFINED }, NOT_CONSTRUCTABLE)
        RhinoUtils.withRhinoContext(scriptRuntime) { cx ->
            ScriptableObject.callMethod(cx, thenable, "then", arrayOf(ok, err))
        }
    }

    /** A node settled by [thenable] (`FlowScheduler.defer`). zh-CN: 由 [thenable] 结算的节点 (`FlowScheduler.defer`). */
    fun adoptThenable(thenable: Scriptable): FlowNode {
        val scheduler = scheduler()
        val node = scheduler.defer(thenable)
        hookThenable(thenable, { scheduler.fulfill(node, it) }, { scheduler.reject(node, it) })
        return node
    }

    /** A rejection value of script code as the throwable a node stores; the value is recovered by [FlowJsErrors.toJs]. zh-CN: 脚本代码的拒绝值作为节点存储的 throwable; 值由 [FlowJsErrors.toJs] 取回. */
    fun rejectionOf(value: Any?): Throwable {
        val unwrapped = RhinoUtils.unwrap(value)
        if (unwrapped is Throwable) return unwrapped
        val linep = intArrayOf(0)
        val sourceName = Context.getSourcePositionFromStack(linep)
        return JavaScriptException(value, sourceName, linep[0])
    }

    companion object {

        private const val PROPERTY_KEY = "flow.executor"

        /** The executor of [scriptRuntime], created on first use and closed with the runtime. zh-CN: [scriptRuntime] 的执行器, 首次使用时创建, 随运行时关闭. */
        fun of(scriptRuntime: ScriptRuntime): FlowExecutor = synchronized(scriptRuntime) {
            scriptRuntime.getProperty(PROPERTY_KEY) as? FlowExecutor ?: FlowExecutor(scriptRuntime).also {
                scriptRuntime.putProperty(PROPERTY_KEY, it)
                scriptRuntime.closeableManager.add(it)
            }
        }

    }

}
