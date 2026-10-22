package org.autojs.autojs.core.looper

import android.os.Handler
import android.os.Looper
import androidx.annotation.CallSuper
import org.autojs.autojs.AutoJs
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.lang.ThreadCompat
import org.autojs.autojs.leakcanary.LeakCanarySetup
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.Console
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.CountDownLatch

/**
 * Created by Stardust on Dec 27, 2017.
 * Modified by SuperMonster003 as of Jan 31, 2026.
 */
open class TimerThread(
    scriptRuntime: ScriptRuntime,
    target: Runnable,
) : ThreadCompat(), ILooperThread {

    // Cleared when run() ends: the thread object may stay referenced after it has ended (an unrecycled Message keeps its
    // Looper and thus this thread), and must not keep the script's runnable with it (docs/memory/ROADMAP.md H21).
    // This class runs the target itself, so it is not handed to Thread.
    // zh-CN: run() 结束时清空: 线程结束后线程对象仍可能被引用 (未回收的 Message 持有其 Looper, 进而持有本线程), 不能连带脚本的 Runnable (docs/memory/ROADMAP.md H21).
    // 本类自行运行该 Runnable, 因此不交给 Thread.
    @Volatile
    private var target: Runnable? = target

    private val mRunningLatch = CountDownLatch(1)
    private var mWeakRuntime = WeakReference(scriptRuntime)

    @Volatile
    private var mTimer: Timer? = null

    @Volatile
    private var mLooper: Looper? = null

    override val looper: Looper?
        get() = mLooper

    override fun run() {
        val scriptRuntime = mWeakRuntime.get() ?: return
        try {
            runLoop(scriptRuntime)
        } finally {
            // Drop this thread from the process-wide looper table however the loop ends; a leftover entry kept the looper,
            // its MessageQueue and the idle handler of the script runtime reachable (docs/memory/ROADMAP.md H1).
            // zh-CN: 无论循环以何种方式结束, 都把本线程从进程级 Looper 表中移除; 残留的条目曾使该 Looper, 它的 MessageQueue 以及脚本运行时的 IdleHandler 一直可达 (docs/memory/ROADMAP.md H1).
            LooperHelper.quitForThread(this)
            target = null
            // A thread ending while its script exits should be collected with the script; debug builds let LeakCanary check that
            // (docs/memory/ROADMAP.md T5). Threads ending earlier may still be referenced by the running script, so they are not watched.
            // zh-CN: 脚本结束过程中结束的线程应随脚本一起被回收, debug 构建交给 LeakCanary 核对 (docs/memory/ROADMAP.md T5). 更早结束的线程可能仍被运行中的脚本引用, 因此不监视.
            if (scriptRuntime.isExiting) LeakCanarySetup.watch(this, "TimerThread ended on script exit")
        }
    }

    private fun runLoop(scriptRuntime: ScriptRuntime) {
        scriptRuntime.loopers.prepare()

        scriptRuntime.timers.newTimer(scriptRuntime).also {
            mTimer = it
            sTimerMap[currentThread()] = WeakReference<Timer>(it)
        }

        // Entered for the script's engine and exited when the loop ends; a thread without a Rhino engine enters none, so it
        // exits none either (docs/memory/ROADMAP.md H4 / P4.4).
        // zh-CN: 为脚本的引擎进入, 循环结束时退出; 没有 Rhino 引擎的线程不进入, 也就不退出 (docs/memory/ROADMAP.md H4 / P4.4).
        val engine = scriptRuntime.engines.myEngine() as? RhinoJavaScriptEngine
        val entered = engine?.enterContext() != null

        notifyRunning()

        val currentLooper = Looper.myLooper()
        setLooper(currentLooper)
        val target = target
        if (currentLooper != null && target != null) Handler(currentLooper).post(ReleasingRunnable(target))

        val exceptionHandler: (t: Throwable) -> Unit = { t ->
            runCatching {
                if (!ScriptInterruptedException.causedByInterrupt(t)) {
                    val console: Console = mWeakRuntime.get()?.console ?: AutoJs.instance.globalConsole
                    console.error("${currentThread()}: $t")
                }
            }
        }

        runCatching { Looper.loop() }.onFailure(exceptionHandler)
        runCatching { mTimer?.removeAllCallbacks() }.onFailure(exceptionHandler)
        runCatching { onExit() }.onFailure(exceptionHandler)
        mTimer = null
        if (entered) runCatching { Context.exit() }.onFailure(exceptionHandler)
        sTimerMap.remove(currentThread())
    }

    override fun interrupt() {
        LooperHelper.quitForThread(this)
        LooperHelper.quit(LooperThread.getLooperOrNull(this))
        super.interrupt()
    }

    override fun toString() = "Thread[$name,$priority]"

    @CallSuper
    protected open fun onExit() {
        val loopers = mWeakRuntime.get()?.loopers ?: return
        loopers.notifyThreadExit(this)
    }

    @Throws(InterruptedException::class)
    fun waitFor() {
        mRunningLatch.await()
    }

    fun setTimeout(callback: Callable): Double =
        setTimeout(callback, 1)

    fun setTimeout(callback: Callable, delay: Long, vararg args: Any): Double = withTimer {
        setTimeout(callback, delay, *args.copyOf())
    }

    fun clearTimeout(id: Double): Boolean = withTimer {
        clearTimeout(id)
    }

    fun setInterval(callback: Callable): Double =
        setInterval(callback, 1L)

    fun setInterval(callback: Callable, interval: Long, vararg args: Any): Double = withTimer {
        setInterval(callback, interval, *args.copyOf())
    }

    fun clearInterval(id: Double): Boolean = withTimer {
        clearInterval(id)
    }

    fun setImmediate(callback: Callable, vararg args: Any): Double = withTimer {
        setImmediate(callback, *args.copyOf())
    }

    fun clearImmediate(id: Double): Boolean = withTimer {
        clearImmediate(id)
    }

    private inline fun <R> withTimer(callback: Timer.() -> R): R {
        val timer = mTimer
        checkNotNull(timer) { "Thread is not alive" }
        return callback(timer)
    }

    private fun setLooper(looper: Looper?) {
        mLooper = looper
    }

    private fun notifyRunning() {
        mRunningLatch.countDown()
    }

    /**
     * A runnable for one post to a Looper which drops its delegate as soon as it starts running (docs/memory/ROADMAP.md H21).
     * When the delegate throws (for example on a force stop), Looper.loop leaves the dispatched Message unrecycled with its
     * callback set, and a stale reference to that Message object in the stack frame of another Looper thread (it came from the
     * shared message pool) then kept the script runtime reachable.
     * zh-CN: 只投递一次的 Runnable, 一开始运行就丢弃其委托 (docs/memory/ROADMAP.md H21).
     * 委托抛出异常 (例如强制停止) 时, Looper.loop 不会回收已分发的 Message, 其 callback 仍在; 而另一个 Looper 线程栈帧中对该 Message 对象 (来自共享消息池) 的陈旧引用曾因此让脚本运行时保持可达.
     */
    class ReleasingRunnable(delegate: Runnable) : Runnable {

        @Volatile
        private var mDelegate: Runnable? = delegate

        override fun run() {
            val delegate = mDelegate ?: return
            mDelegate = null
            delegate.run()
        }
    }

    companion object {

        private val sTimerMap = WeakHashMap<Thread, WeakReference<Timer>>()

        @JvmStatic
        fun getTimerForThread(thread: Thread) = sTimerMap[thread]?.get()
    }
}
