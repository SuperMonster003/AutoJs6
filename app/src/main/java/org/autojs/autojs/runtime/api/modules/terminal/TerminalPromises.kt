package org.autojs.autojs.runtime.api.modules.terminal

import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.modules.ScriptAsyncDispatcher
import org.autojs.autojs.runtime.api.modules.toJsPromise
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.terminal.api.TerminalErrorCodes
import org.mozilla.javascript.Scriptable
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** The `xAsync` results and the session events of the terminal API reach the script thread through the shared dispatcher. zh-CN: 终端 API 的 `xAsync` 结果与会话事件经共享分发器回到脚本线程. */
internal typealias TerminalAsyncDispatcher = ScriptAsyncDispatcher

/** The work of one script method: runs in a coroutine; the dispatcher is present for the `Async` form only. zh-CN: 脚本方法的工作: 在协程里运行; 只有 `Async` 形态带分发器. */
internal typealias TerminalWork = suspend (TerminalAsyncDispatcher?) -> Any?

/**
 * Promise and event plumbing of the terminal API (plugin roadmap P4.1 and P4.2): the work of an
 * `Async` method runs as a coroutine of the script runtime and its outcome comes back on the
 * script thread through a [TerminalAsyncDispatcher] created there, which also keeps the script
 * alive until the Promise settled; the synchronous form ([sync]) blocks the script thread until
 * the work completes while every terminal dispatcher of that thread keeps delivering, so
 * `waitFor` and `exec({ wait: true })` do not starve `output` listeners or pending Promises.
 * Every dispatcher is a [TerminalDispatch]: tracked per thread for that pumping and registered
 * with the runtime so script exit cancels it silently. Results are plain JVM values, script
 * objects or an [OnScriptThread] whose value is produced on the script thread; failures become
 * `TerminalError` instances ([TerminalJsErrors]).
 * zh-CN: 终端 API 的 Promise 与事件管道 (插件路线图 P4.1 与 P4.2): `Async` 方法的工作作为脚本运行时的协程运行, 结果经在脚本线程上创建的
 * [TerminalAsyncDispatcher] 回到脚本线程, 分发器同时让脚本活到 Promise 落定; 同步形态 ([sync]) 阻塞脚本线程直到工作完成, 期间该线程的每个终端分发器持续
 * 投递, 因此 `waitFor` 与 `exec({ wait: true })` 不会饿死 `output` 监听器或待落定的 Promise. 每个分发器都是 [TerminalDispatch]: 按线程跟踪以供泵送, 并
 * 登记到运行时以便脚本退出时静默取消. 结果是普通 JVM 值, 脚本对象或在脚本线程上产出值的 [OnScriptThread]; 失败成为 `TerminalError` 实例
 * ([TerminalJsErrors]).
 */
internal object TerminalPromises {

    /** A result whose script value must be built on the script thread. zh-CN: 其脚本值必须在脚本线程上构建的结果. */
    fun interface OnScriptThread {
        fun produce(): Any?
    }

    private val dispatches = object : ThreadLocal<MutableSet<TerminalDispatch>>() {
        override fun initialValue(): MutableSet<TerminalDispatch> = ConcurrentHashMap.newKeySet()
    }

    /** A tracked dispatcher of the calling script thread; must be created on that thread. zh-CN: 调用脚本线程的受跟踪分发器; 必须在该线程上创建. */
    fun dispatch(scriptRuntime: ScriptRuntime, onClose: () -> Unit = {}): TerminalDispatch =
        TerminalDispatch(scriptRuntime, requireNotNull(dispatches.get()), onClose)

    /** Must be called on the script thread. zh-CN: 必须在脚本线程上调用. */
    fun launch(scriptRuntime: ScriptRuntime, block: suspend () -> Any?): Scriptable = launchWith(scriptRuntime) { block() }

    /** Like [launch]; [block] receives the dispatcher for non-terminal callbacks. zh-CN: 同 [launch]; [block] 得到分发器以做非终结回调. */
    fun launchWith(scriptRuntime: ScriptRuntime, block: suspend (TerminalAsyncDispatcher) -> Any?): Scriptable {
        val adapter = ScriptPromiseAdapter()
        val promise = adapter.toJsPromise(scriptRuntime) as Scriptable
        var job: Job? = null
        val delivery = dispatch(scriptRuntime) { job?.cancel() }
        val launched = scriptRuntime.coroutineScope.launch {
            val outcome = runCatching { block(delivery.dispatcher) }
            deliver(scriptRuntime, delivery, adapter, outcome)
        }
        job = launched
        launched.invokeOnCompletion { cause ->
            // A coroutine cancelled before it started never executes its body. zh-CN: 启动前即被取消的协程不会执行其主体.
            if (cause != null && !delivery.dispatcher.isTerminal) {
                if (scriptRuntime.isExiting || scriptRuntime.isStopped) delivery.close()
                else deliver(scriptRuntime, delivery, adapter, Result.failure(cause))
            }
        }
        if (!delivery.active) launched.cancel()
        return promise
    }

    /** A Promise already rejected with [error]. zh-CN: 已用 [error] 拒绝的 Promise. */
    fun rejected(scriptRuntime: ScriptRuntime, error: Throwable): Scriptable {
        val adapter = ScriptPromiseAdapter()
        val promise = adapter.toJsPromise(scriptRuntime) as Scriptable
        deliver(scriptRuntime, dispatch(scriptRuntime), adapter, Result.failure(error))
        return promise
    }

    /**
     * Synchronous form: runs [block] as a coroutine and blocks the calling script thread until it
     * completes, pumping this thread's terminal dispatchers meanwhile; stopping the script cancels
     * the wait. Refused on the UI thread.
     * zh-CN: 同步形态: 把 [block] 作为协程运行并阻塞调用脚本线程直到完成, 期间泵送本线程的终端分发器; 停止脚本即取消等待. 在 UI 线程上拒绝.
     */
    fun <T> sync(scriptRuntime: ScriptRuntime, block: suspend () -> T): T {
        val looper = Looper.myLooper()
        if (looper != null && looper == Looper.getMainLooper()) {
            throw TerminalJsErrors.failure(TerminalErrorCodes.INVALID_ARGUMENT, "synchronous terminal methods cannot block the UI thread; use the Async form")
        }
        val wait = TerminalBlockingWait<T>()
        val job = scriptRuntime.coroutineScope.launch { wait.complete(runCatching { block() }) }
        job.invokeOnCompletion { cause ->
            if (cause != null) wait.complete(Result.failure(cause))
        }
        try {
            return wait.await(
                stopped = { scriptRuntime.isExiting || scriptRuntime.isStopped },
                pump = { drainCurrentThread(scriptRuntime) },
            )
        } catch (interrupted: InterruptedException) {
            // Condition.await clears the flag; keep the force-stop visible. zh-CN: Condition.await 清掉了标志; 保持强制停止可见.
            Thread.currentThread().interrupt()
            throw interrupted
        } finally {
            job.cancel()
        }
    }

    private fun drainCurrentThread(scriptRuntime: ScriptRuntime) {
        requireNotNull(dispatches.get()).toList()
            .filter { it.runtime === scriptRuntime && it.active }
            .forEach { it.dispatcher.drainOnOriginatingThread() }
    }

    private fun deliver(scriptRuntime: ScriptRuntime, delivery: TerminalDispatch, adapter: ScriptPromiseAdapter, outcome: Result<Any?>) {
        if (!delivery.active) {
            delivery.close()
            return
        }
        try {
            outcome.fold(
                onSuccess = { value ->
                    val produced = value is OnScriptThread
                    delivery.dispatcher.terminalValues(if (produced) emptyArray() else arrayOf(value)) { mapped ->
                        try {
                            if (!delivery.active) return@terminalValues
                            val resolved = try {
                                if (produced) RhinoUtils.toJsValue(scriptRuntime.topLevelScope, (value as OnScriptThread).produce()) else mapped[0]
                            } catch (t: Throwable) {
                                adapter.reject(TerminalJsErrors.toJs(scriptRuntime, t))
                                return@terminalValues
                            }
                            adapter.resolve(resolved)
                        } finally {
                            delivery.finish()
                        }
                    }
                },
                onFailure = { error ->
                    delivery.dispatcher.terminalValues { _ ->
                        try {
                            if (delivery.active) adapter.reject(TerminalJsErrors.toJs(scriptRuntime, error))
                        } finally {
                            delivery.finish()
                        }
                    }
                },
            )
        } catch (_: Throwable) {
            // The script is gone (its looper no longer accepts work): nothing is left to settle.
            // zh-CN: 脚本已不在 (其 looper 不再接受工作): 没有可落定的对象了.
            delivery.close()
        }
    }
}

/**
 * One [ScriptAsyncDispatcher] of a script thread, tracked until its terminal delivery ran
 * ([finish]) or the script exits ([close], registered with the runtime's closeables): exit
 * suppresses even a terminal callback already queued by Binder.
 * zh-CN: 脚本线程的一个 [ScriptAsyncDispatcher], 跟踪到终结投递完成 ([finish]) 或脚本退出 ([close], 登记于运行时的 closeables): 退出会抑制 Binder 已排队的
 * 终结回调.
 */
internal class TerminalDispatch(
    val runtime: ScriptRuntime,
    private val owner: MutableSet<TerminalDispatch>,
    private val onClose: () -> Unit,
) : Closeable {

    val dispatcher = ScriptAsyncDispatcher(runtime)
    private val released = AtomicBoolean()

    val active: Boolean
        get() = !released.get() && !runtime.isExiting && !runtime.isStopped

    init {
        owner += this
        runtime.closeableManager.add(this)
        if (runtime.isExiting || runtime.isStopped) close()
    }

    /** The terminal delivery ran: stop tracking. zh-CN: 终结投递已运行: 停止跟踪. */
    fun finish() {
        if (!released.compareAndSet(false, true)) return
        owner -= this
        runtime.closeableManager.remove(this)
    }

    /** Script exit or loss of interest: drop queued deliveries without a script-visible event. zh-CN: 脚本退出或不再关心: 丢弃排队的投递, 不产生脚本可见事件. */
    override fun close() {
        if (!released.compareAndSet(false, true)) return
        try {
            dispatcher.cancelSilently()
            onClose()
        } finally {
            owner -= this
            runtime.closeableManager.remove(this)
        }
    }
}

/** Pure condition wait: callbacks never run on a worker or while the lock is held. zh-CN: 纯条件等待: 回调从不在工作线程上或持锁时运行. */
internal class TerminalBlockingWait<T> {

    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private var outcome: Result<T>? = null

    fun complete(result: Result<T>) = lock.withLock {
        if (outcome == null) {
            outcome = result
            changed.signalAll()
        }
    }

    fun await(stopped: () -> Boolean = { false }, pump: () -> Unit): T {
        while (true) {
            if (stopped()) throw CancellationException("terminal script stopped")
            pump()
            if (stopped()) throw CancellationException("terminal script stopped")
            val ready = lock.withLock { outcome }
            if (ready != null) {
                // The producer signals only after its last event was enqueued. zh-CN: 生产方在最后一个事件入队后才发信号.
                pump()
                if (stopped()) throw CancellationException("terminal script stopped")
                return ready.getOrThrow()
            }
            lock.withLock { if (outcome == null) changed.await(POLL_MILLIS, TimeUnit.MILLISECONDS) }
        }
    }

    private companion object {
        const val POLL_MILLIS = 10L
    }
}
