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
import java.util.WeakHashMap
import java.util.concurrent.CountDownLatch

/**
 * Created by Stardust on Dec 27, 2017.
 * Modified by SuperMonster003 as of Jan 31, 2026.
 */
open class TimerThread(
    scriptRuntime: ScriptRuntime,
    private val target: Runnable,
) : ThreadCompat(target), ILooperThread {

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

        scriptRuntime.loopers.prepare()

        scriptRuntime.timers.newTimer(scriptRuntime).also {
            mTimer = it
            sTimerMap[currentThread()] = WeakReference<Timer>(it)
        }

        val engine = scriptRuntime.engines.myEngine() as? RhinoJavaScriptEngine
        engine?.enterContext()

        notifyRunning()

        val currentLooper = Looper.myLooper()
        setLooper(currentLooper)
        currentLooper?.let { Handler(it).post(target) }

        val exceptionHandler: (t: Throwable) -> Unit = { t ->
            runCatching {
                if (!ScriptInterruptedException.causedByInterrupt(t)) {
                    val console: Console = mWeakRuntime.get()?.console ?: AutoJs.instance.globalConsole
                    console.error("${currentThread()}: $t")
                }
            }
        }

        runCatching { Looper.loop() }.onFailure(exceptionHandler)
        runCatching { onExit() }.onFailure(exceptionHandler)
        mTimer = null
        runCatching { Context.exit() }.onFailure(exceptionHandler)
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
        mRunningLatch.await()
    }

    fun setTimeout(callback: BaseFunction): Double =
        setTimeout(callback, 1)

    fun setTimeout(callback: BaseFunction, delay: Long, vararg args: Any): Double = withTimer {
        setTimeout(callback, delay, args.copyOf())
    }

    fun clearTimeout(id: Double): Boolean = withTimer {
        clearTimeout(id)
    }

    fun setInterval(callback: BaseFunction): Double =
        setInterval(callback, 1L)

    fun setInterval(callback: BaseFunction, interval: Long, vararg args: Any): Double = withTimer {
        setInterval(callback, interval, args.copyOf())
    }

    fun clearInterval(id: Double): Boolean = withTimer {
        clearInterval(id)
    }

    fun setImmediate(callback: BaseFunction, vararg args: Any): Double = withTimer {
        setImmediate(callback, args.copyOf())
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

    companion object {

        private val sTimerMap = WeakHashMap<Thread, WeakReference<Timer>>()

        @JvmStatic
        fun getTimerForThread(thread: Thread) = sTimerMap[thread]?.get()
    }
}