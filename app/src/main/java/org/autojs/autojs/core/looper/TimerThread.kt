package org.autojs.autojs.core.looper

import android.os.Handler
import android.os.Looper
import androidx.annotation.CallSuper
import org.autojs.autojs.AutoJs
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.lang.ThreadCompat
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.Console
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import java.lang.ref.WeakReference
import java.util.*

/**
 * Created by Stardust on Dec 27, 2017.
 */
open class TimerThread(
    scriptRuntime: ScriptRuntime,
    private val target: Runnable,
) : ThreadCompat(target), ILooperThread {

    private var mRunning = false
    private val mRunningLock = Object()
    private var mWeakRuntime = WeakReference(scriptRuntime)

    @Volatile
    private var mTimer: Timer? = null

    @Volatile
    private var mLooper: Looper? = null

    override val looper: Looper? = mLooper

    var loopers: Loopers? = null

    val timer: Timer
        get() {
            checkNotNull(mTimer) { "thread is not alive" }
            return mTimer as Timer
        }

    override fun run() {
        val scriptRuntime = mWeakRuntime.get() ?: return
        scriptRuntime.loopers.prepare()
        var timer: Timer
        mTimer = scriptRuntime.timers.newTimer(scriptRuntime).also { timer = it }
        sTimerMap[currentThread()] = WeakReference<Timer>(timer)
        (scriptRuntime.engines.myEngine() as? RhinoJavaScriptEngine)?.enterContext()
        notifyRunning()
        Looper.myLooper().also { setLooper(it) }
            ?.let { Handler(it).post(target) }
        try {
            Looper.loop()
            onExit()
            mTimer = null
        } catch (throwable: Throwable) {
            try {
                if (ScriptInterruptedException.causedByInterrupt(throwable)) {
                    return
                }
                var console: Console? = null
                val runtime = mWeakRuntime.get()
                if (runtime != null) {
                    console = runtime.console
                }
                if (console == null) {
                    console = AutoJs.instance.globalConsole
                }
                console.error("${Thread.currentThread()}: $throwable")
            } finally {
                onExit()
                mTimer = null
                Context.exit()
                sTimerMap.remove(currentThread())
            }
        }
        runCatching { Context.exit() }
            sTimerMap.remove(currentThread())
        }

    override fun interrupt() {
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
        synchronized(mRunningLock) {
            if (!mRunning) {
                mRunningLock.wait()
            }
        }
    }

    fun setTimeout(callback: BaseFunction): Double = setTimeout(callback, 1)

    fun setTimeout(callback: BaseFunction, delay: Long, vararg args: Any): Double = timer.setTimeout(callback, delay, args.copyOf())

    fun clearTimeout(id: Double) = timer.clearTimeout(id)

    fun setInterval(callback: BaseFunction) = setInterval(callback, 1L)

    fun setInterval(callback: BaseFunction, interval: Long, vararg args: Any) = timer.setInterval(callback, interval, args.copyOf())

    fun clearInterval(id: Double) = timer.clearInterval(id)

    fun setImmediate(callback: BaseFunction, vararg args: Any) = timer.setImmediate(callback, args.copyOf())

    fun clearImmediate(id: Double) = timer.clearImmediate(id)

    private fun setLooper(looper: Looper?) {
        mLooper = looper
    }

    private fun notifyRunning() {
        synchronized(mRunningLock) {
            mRunning = true
            mRunningLock.notifyAll()
        }
    }

    companion object {
        private val sTimerMap = WeakHashMap<Thread, WeakReference<Timer>>()

        @JvmStatic
        fun getTimerForThread(thread: Thread) = sTimerMap[thread]?.get()

    }

}