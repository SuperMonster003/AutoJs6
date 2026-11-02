package org.autojs.autojs.runtime.api.modules.promise

import org.autojs.autojs.core.looper.SettlementLatch
import org.autojs.autojs.rhino.AutoJsContext
import org.autojs.autojs.rhino.EventLoopContextFactory
import org.autojs.autojs.rhino.ScriptEventLoop
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.rhino.continuation.Continuation
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.runtime.api.modules.bootstrap.ContinuationPrototype
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContinuationPending
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicLong
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * One script engine as AutoJs6 runs it, for the global Promise: the built-in Promise of Rhino with [PromiseSuspension], in the
 * [AutoJsContext] and the [EventLoopContextFactory] of the host (roadmap P9.5 of Rhino-For-AutoJs6). The Promises before it
 * were `assets/modules/promise.js` and the Kotlin PromiseConstructor (docs/augment/ROADMAP.md P4.2, appendix C Q95);
 * [PromiseDifferentialTest] replays the results of the module.
 *
 * A single script thread runs a timer in virtual time (`setImmediate` and `setTimeout` ordered by due time, then by
 * posting, as `core/looper/Timer`) and each callback as a top call that lets a coroutine suspending again through (Q23).
 * This object is the [ScriptEventLoop] of the script, the timer standing for those of the runtime, and it logs a report of
 * an unhandled rejection as `unhandled ...`. `continuation` is the real [Continuation] as
 * `runtime/api/modules/continuation/Creator` uses it (another thread posts the resume, as Q98 has it), with the host's
 * [ContinuationPrototype]. `runtime.loopers.newSettlementLatch()` is a real [SettlementLatch] over the timer and the microtasks.
 * `otherThread(fn)` calls `fn` on another Java thread with its own Context and no timer (a binder thread, a Java thread):
 * `setImmediate` there posts to the script timer, as `Timers.setImmediate` falls back to the main timer. `kotlinAwait(p)` is
 * the `await()` of Flow (Q97): Kotlin suspending through [PromiseSuspension]. `outsideTopCall(fn)` calls `fn` in the next turn
 * with no top call running, as Kotlin code that calls a native function of Rhino directly does.
 *
 * zh-CN: 按 AutoJs6 的方式运行的一个脚本引擎, 用于全局 Promise: Rhino 内建 Promise 加 [PromiseSuspension], 运行在宿主的 [AutoJsContext] 与 [EventLoopContextFactory] 中
 * (Rhino-For-AutoJs6 ROADMAP P9.5). 此前的 Promise 是 promise.js 与 Kotlin PromiseConstructor (docs/augment/ROADMAP.md P4.2, 附录 C Q95), [PromiseDifferentialTest] 回放原模块的结果.
 * 单个脚本线程以虚拟时间运行 timer, 本对象是脚本的 [ScriptEventLoop], 把未处理拒绝的报告记为 `unhandled ...`; 续体为真实的 [Continuation], wait() 用真实的
 * [SettlementLatch]; otherThread(fn) 在没有 timer 的其他 Java 线程上调用 fn; kotlinAwait(p) 即 Flow 的 await() (Q97); outsideTopCall(fn) 在下一回合于顶层调用之外调用 fn.
 */
class PromiseHarness : AutoCloseable, ScriptEventLoop {

    val log = ArrayList<String>()
    private val tasks = PriorityQueue<Task>(compareBy<Task>({ it.due }, { it.seq }))
    private val seq = AtomicLong()
    private var now = 0L
    val mainThread: Thread = Thread.currentThread()

    private class Task(val due: Long, val seq: Long, val body: () -> Unit)

    private val factory = object : EventLoopContextFactory() {
        override fun makeContext(): Context = AutoJsContext(this).apply {
            languageVersion = Context.VERSION_ES6
            isInterpretedMode = true
        }
    }

    val cx: AutoJsContext = factory.enterContext() as AutoJsContext
    val scope = TopLevelScope().apply {
        initStandardObjects(cx, false)
        eventLoop = this@PromiseHarness
    }
    val global: ScriptableObject = scope.globalThis as ScriptableObject

    init {
        define("log", 1) { _, args -> synchronized(log) { log += args.joinToString(" ") { render(it) } }; Undefined.instance }
        define("where", 0) { _, _ -> if (Thread.currentThread() === mainThread) "main" else "other" }
        define("setImmediate", 1) { _, args -> post(0) { callTop(args[0] as Callable, args.drop(1).toTypedArray()) }; Undefined.instance }
        define("setTimeout", 2) { _, args ->
            val delay = if (args.size < 2) 1L else Context.toNumber(args[1]).toLong()
            post(delay) { callTop(args[0] as Callable, args.drop(2).toTypedArray()) }
            Undefined.instance
        }
        define("otherThread", 1) { _, args ->
            val fn = args[0] as Callable
            val t = Thread {
                val c = factory.enterContext()
                try {
                    fn.call(c, scope, global, emptyArray())
                } catch (e: Throwable) {
                    synchronized(log) { log += "other thread error: $e" }
                } finally {
                    Context.exit()
                }
            }
            t.start()
            t.join()
            Undefined.instance
        }
        define("kotlinAwait", 1) { c, args -> PromiseSuspension.await(c, scope, args[0]) }
        define("outsideTopCall", 1) { _, args ->
            val f = args[0] as Callable
            post(0) { f.call(cx, scope, Undefined.SCRIPTABLE_UNDEFINED, emptyArray()) }
            Undefined.instance
        }
        val console = cx.newObject(scope) as NativeObject
        console.defineProperty("warn", fn("warn", 2) { _, args -> log += "warn " + args.joinToString(" ") { render(it) }; Undefined.instance }, ScriptableObject.EMPTY)
        console.defineProperty("log", fn("log", 2) { _, args -> log += args.joinToString(" ") { render(it) }; Undefined.instance }, ScriptableObject.EMPTY)
        global.defineProperty("console", console, ScriptableObject.DONTENUM)

        val continuation = cx.newObject(scope) as NativeObject
        continuation.prototype = ContinuationPrototype.create(scope)
        continuation.defineProperty("create", fn("create", 0) { _, _ -> Context.javaToJS(Cont(), scope) }, ScriptableObject.EMPTY)
        global.defineProperty("continuation", continuation, ScriptableObject.DONTENUM)

        val loopers = cx.newObject(scope) as NativeObject
        loopers.defineProperty("newSettlementLatch", fn("newSettlementLatch", 0) { _, _ -> Context.javaToJS(SettlementLatch(queue, true) { cx.runMicrotasks() }, scope) }, ScriptableObject.EMPTY)
        val runtime = cx.newObject(scope) as NativeObject
        runtime.defineProperty("loopers", loopers, ScriptableObject.EMPTY)
        global.defineProperty("runtime", runtime, ScriptableObject.DONTENUM)

        global.defineProperty("Promise", global.get("Promise", global), ScriptableObject.READONLY or ScriptableObject.PERMANENT)
        PromiseSuspension.install(scope)
    }

    override fun hasTimer(thread: Thread) = thread === mainThread

    override fun post(thread: Thread?, task: Runnable) = post(0) { cx.runTask(this, task) }

    override fun reportUnhandledRejection(description: String) {
        synchronized(log) { log += "unhandled " + description.lineSequence().first() }
        unhandledReports += description
    }

    /** The reports of unhandled rejections in full. */
    val unhandledReports = ArrayList<String>()

    private val queue = object : SettlementLatch.CallbackQueue {
        override fun takeDueCallback(): Runnable? = synchronized(tasks) { tasks.poll() }?.let { t -> Runnable { now = maxOf(now, t.due); t.body() } }
        override fun millisUntilNextCallback(): Long = synchronized(tasks) { if (tasks.isEmpty()) -1L else 1L }
        override fun beginWait() = Unit
        override fun endWait() = Unit
    }

    fun post(delay: Long, body: () -> Unit) {
        synchronized(tasks) { tasks += Task(now + delay, seq.getAndIncrement(), body) }
    }

    /** A timer callback as `Timer.callFunction` runs it (`RhinoUtils.callFunction`: a top call unless one is running). */
    private fun callTop(f: Callable, args: Array<out Any?>) {
        try {
            @Suppress("UNCHECKED_CAST")
            val a = args as Array<Any?>
            if (RhinoScriptRuntime.hasTopCall(cx)) f.call(cx, scope, Undefined.SCRIPTABLE_UNDEFINED, a)
            else RhinoScriptRuntime.doTopCall(f, cx, scope, Undefined.SCRIPTABLE_UNDEFINED, a, false)
        } catch (_: ContinuationPending) {
            /* Q23: suspended again. */
        }
    }

    /** A continuation as `continuation.create()` returns it (`runtime/api/modules/continuation/Creator`). */
    inner class Cont {
        private val continuation = Continuation(cx, scope) { r -> post(0) { try { r.run() } catch (_: ContinuationPending) {} } }

        fun await(): Any? = Continuation.Result.getOrThrow(continuation.suspend())

        fun resume() = resume(Undefined.instance)

        fun resume(result: Any?) = continuation.resumeWith(Continuation.Result.success(result))

        fun resumeError(error: Any?) {
            require(!error.isJsNullish()) { "resumeError must be non-nullish" }
            continuation.resumeWith(Continuation.Result.failure(error))
        }
    }

    /** Runs [source] as the main script with continuations, then the timer until nothing is left. */
    fun run(source: String): List<String> {
        val watchdog = Thread {
            try {
                Thread.sleep(15_000)
                synchronized(log) { log += "(watchdog: interrupted after 15 s)" }
                mainThread.interrupt()
            } catch (_: InterruptedException) {
            }
        }.apply { isDaemon = true; start() }
        try {
            val script = cx.compileString(source, "main.js", 1, null)
            try {
                cx.executeScriptWithContinuations(script, scope)
            } catch (_: ContinuationPending) {
                /* Waiting for the timer, or already resumed by the checkpoint of the top call. */
            } catch (e: Throwable) {
                log += "main error: " + describe(e)
            }
            drain()
            return log
        } finally {
            watchdog.interrupt()
            Thread.interrupted()
        }
    }

    fun drain(maxTasks: Int = 100_000) {
        var n = 0
        while (n++ < maxTasks) {
            val t = synchronized(tasks) { tasks.poll() } ?: return
            now = maxOf(now, t.due)
            try {
                t.body()
            } catch (e: Throwable) {
                log += "task error: " + describe(e)
            }
        }
        log += "(task limit)"
    }

    fun eval(source: String): Any? = cx.evaluateString(scope, source, "eval", 1, null)

    fun render(v: Any?): String = when (v) {
        null -> "null"
        is Undefined -> "undefined"
        is CharSequence -> v.toString()
        is Scriptable -> try {
            val f = cx.evaluateString(scope, "(function (x) { try { if (x instanceof Error) return x.name + ': ' + x.message; return JSON.stringify(x) || String(x); } catch (e) { return String(x); } })", "render", 1, null)
            Context.toString((f as Callable).call(cx, scope, global, arrayOf(v)))
        } catch (e: Throwable) {
            v.toString()
        }
        else -> Context.toString(v)
    }

    private fun describe(e: Throwable): String = when (e) {
        is org.mozilla.javascript.JavaScriptException -> "JavaScriptException " + render(e.value)
        is org.mozilla.javascript.RhinoException -> e.javaClass.simpleName + " " + e.details()
        else -> e.javaClass.simpleName + " " + e.message
    }


    private fun fn(name: String, length: Int, body: (Context, Array<out Any?>) -> Any?): BaseFunction = object : BaseFunction(scope, ScriptableObject.getFunctionPrototype(scope)) {
        override fun call(cx: Context, nt: Any?, s: VarScope, thisObj: Any?, args: Array<out Any?>): Any? = body(cx, args)
        override fun getFunctionName() = name
        override fun getLength() = length
    }

    private fun define(name: String, length: Int, body: (Context, Array<out Any?>) -> Any?): BaseFunction =
        fn(name, length, body).also { global.defineProperty(name, it, ScriptableObject.DONTENUM) }

    override fun close() {
        Context.exit()
    }

}
