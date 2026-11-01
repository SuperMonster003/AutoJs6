package org.autojs.autojs.runtime.api.modules.promise

import org.autojs.autojs.core.looper.SettlementLatch
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.runtime.api.modules.bootstrap.ContinuationPrototype
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
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
 * One script engine as AutoJs6 runs it, for the global Promise (roadmap P4.2, appendix C Q95): [PromiseConstructor]
 * ([Impl.KOTLIN] through the global `setImmediate`, [Impl.KOTLIN_POSTED] with a [ReactionPoster] as the runtime has).
 * Until P5.2 it also ran `assets/modules/promise.js`, the Promise of the switch off; [PromiseDifferentialTest] replays
 * its results.
 *
 * A single script thread runs a timer in virtual time (`setImmediate` and `setTimeout` ordered by due time, then by
 * posting, as `core/looper/Timer`) and each callback as a top call that lets a coroutine suspending again through (Q23).
 * `continuation` captures real Rhino continuations as `augment/continuation/Creator` and `rhino/continuation/Continuation`
 * do (`resumeError` throws the error at the suspended call, as `Continuation` does; another thread
 * posts the resume, as Q98 has it), with the host's [ContinuationPrototype]. `runtime.loopers.newSettlementLatch()` is a
 * real [SettlementLatch] over the timer. `otherThread(fn)` calls `fn` on another Java thread with its own Context and no
 * timer (a binder thread, a Java thread): `setImmediate` there posts to the script timer, as `Timers.setImmediate` falls
 * back to the main timer. `kotlinAwait(p)` is the `await()` of Flow (Q97): Kotlin suspending through [PromiseSuspension].
 *
 * zh-CN: 按 AutoJs6 的方式运行的一个脚本引擎, 用于全局 Promise (ROADMAP P4.2, 附录 C Q95): [PromiseConstructor] (经全局 setImmediate,
 * 或像运行时那样带 [ReactionPoster]). P5.2 之前还运行开关关闭时的 promise.js, 其结果由 [PromiseDifferentialTest] 回放. 单个脚本线程以虚拟时间运行 timer, 续体为真实的 Rhino 续体, wait() 用真实的
 * [SettlementLatch]; otherThread(fn) 在没有 timer 的其他 Java 线程上调用 fn; kotlinAwait(p) 即 Flow 的 await() (Q97).
 */
class PromiseHarness(val impl: Impl) : AutoCloseable {

    enum class Impl {
        /** [PromiseConstructor] with every job through the global `setImmediate`. */
        KOTLIN,

        /** [PromiseConstructor] posting its jobs itself while `setImmediate` is the harness's own, as the runtime does. */
        KOTLIN_POSTED,
    }

    val log = ArrayList<String>()
    private val tasks = PriorityQueue<Task>(compareBy<Task>({ it.due }, { it.seq }))
    private val seq = AtomicLong()
    private var now = 0L
    val mainThread: Thread = Thread.currentThread()

    private class Task(val due: Long, val seq: Long, val body: () -> Unit)

    private val factory = object : ContextFactory() {
        override fun makeContext(): Context = super.makeContext().apply {
            languageVersion = Context.VERSION_ES6
            isInterpretedMode = true
        }
    }

    val cx: Context = factory.enterContext()
    val scope = TopLevelScope().apply { initStandardObjects(cx, false) }
    val global: ScriptableObject = scope.globalThis as ScriptableObject

    /** The global Promise of this harness. */
    val promise: BaseFunction

    private val ownSetImmediate: BaseFunction

    init {
        define("log", 1) { _, args -> synchronized(log) { log += args.joinToString(" ") { render(it) } }; Undefined.instance }
        define("where", 0) { _, _ -> if (Thread.currentThread() === mainThread) "main" else "other" }
        ownSetImmediate = define("setImmediate", 1) { _, args -> post(0) { callTop(args[0] as Callable, args.drop(1).toTypedArray()) }; Undefined.instance }
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
        val console = cx.newObject(scope) as NativeObject
        console.defineProperty("warn", fn("warn", 2) { _, args -> log += "warn " + args.joinToString(" ") { render(it) }; Undefined.instance }, ScriptableObject.EMPTY)
        console.defineProperty("log", fn("log", 2) { _, args -> log += args.joinToString(" ") { render(it) }; Undefined.instance }, ScriptableObject.EMPTY)
        global.defineProperty("console", console, ScriptableObject.DONTENUM)

        val continuation = cx.newObject(scope) as NativeObject
        continuation.prototype = ContinuationPrototype.create(scope)
        continuation.defineProperty("create", fn("create", 0) { _, _ -> Context.javaToJS(Cont(), scope) }, ScriptableObject.EMPTY)
        global.defineProperty("continuation", continuation, ScriptableObject.DONTENUM)

        val loopers = cx.newObject(scope) as NativeObject
        loopers.defineProperty("newSettlementLatch", fn("newSettlementLatch", 0) { _, _ -> Context.javaToJS(SettlementLatch(queue, true), scope) }, ScriptableObject.EMPTY)
        val runtime = cx.newObject(scope) as NativeObject
        runtime.defineProperty("loopers", loopers, ScriptableObject.EMPTY)
        global.defineProperty("runtime", runtime, ScriptableObject.DONTENUM)

        promise = when (impl) {
            Impl.KOTLIN -> PromiseConstructor(scope)
            Impl.KOTLIN_POSTED -> PromiseConstructor(scope) { setImmediate, job ->
                (setImmediate === ownSetImmediate).also { own -> if (own) post(0) { callTop(job, emptyArray()) } }
            }
        }
        global.defineProperty("Promise", promise, ScriptableObject.READONLY or ScriptableObject.PERMANENT)
    }

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

    /** A continuation as `continuation.create()` returns it. */
    inner class Cont {
        private var pending: ContinuationPending? = null
        private val thread = Thread.currentThread()

        fun await(): Any? {
            check(pending == null) { "suspend called more than once" }
            throw Context.getCurrentContext().captureContinuation().also { pending = it }
        }

        fun resume() = resume(Undefined.instance)

        fun resume(result: Any?) {
            if (thread === Thread.currentThread()) resumeHere(Context.getCurrentContext(), result)
            else post(0) { try { resumeHere(cx, result) } catch (_: ContinuationPending) {} }
        }

        private fun resumeHere(c: Context, result: Any?) {
            val p = checkNotNull(pending) { "resume called without suspend" }.continuation
            c.resumeContinuation(p, scope, result)
        }

        fun resumeError(error: Any?) {
            require(!error.isJsNullish()) { "resumeError must be non-nullish" }
            if (thread === Thread.currentThread()) resumeErrorHere(Context.getCurrentContext(), error)
            else post(0) { try { resumeErrorHere(cx, error) } catch (_: ContinuationPending) {} }
        }

        private fun resumeErrorHere(c: Context, error: Any?) {
            val p = checkNotNull(pending) { "resume called without suspend" }.continuation
            c.resumeContinuationWithError(p, scope, error)
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
                log += "(suspended)"
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
