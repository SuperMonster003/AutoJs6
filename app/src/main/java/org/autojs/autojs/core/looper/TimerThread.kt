package org.autojs.autojs.core.looper

import android.os.Handler
import android.os.Looper
import androidx.annotation.CallSuper
import org.autojs.autojs.concurrent.VolatileBox
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.lang.ThreadCompat
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.mozilla.javascript.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by Stardust on 2017/12/27.
 * Modified by SuperMonster003 as of Jul 12, 2023.
 * Transformed by SuperMonster003 on Jul 12, 2023.
 */
// @Overruled by SuperMonster003 on Jul 12, 2023.
//  ! Author: aiselp
//  ! Related PR:
//  ! http://pr.autojs6.com/75
//  ! Reason:
//  ! Sorry but my current capabilities are not sufficient
//  ! to fully understand everything from above pull request(s),
//  ! so most of the code will remain as is. :)
@Suppress("unused")
open class TimerThread(
    private val scriptRuntime: ScriptRuntime,
    private val maxCallbackUptimeMillisForAllThreads: VolatileBox<Long>,
    private val target: Runnable
) : ThreadCompat(target) {

    private var mTimer: Timer? = null
    private var mRunning = false
    private val mRunningLock = Object()

    override fun run() {
        scriptRuntime.loopers.prepare()
        mTimer = Timer(scriptRuntime, maxCallbackUptimeMillisForAllThreads).also {
            sTimerMap[currentThread()] = it
        }
        (scriptRuntime.engines.myEngine() as? RhinoJavaScriptEngine)?.enterContext()
        notifyRunning()
        @Suppress("DEPRECATION")
        Looper.myLooper()?.let {
            Handler(it).post(target)
        } ?: Handler().post(target)
        try {
            Looper.loop()
        } catch (e: Throwable) {
            if (!ScriptInterruptedException.causedByInterrupted(e)) {
                scriptRuntime.console.error("${currentThread()}: $e")
            }
        } finally {
            onExit()
            mTimer = null
            Context.exit()
            sTimerMap.remove(currentThread(), mTimer)
        }
    }

    override fun interrupt() {
        LooperHelper.quitForThread(this)
        super.interrupt()
    }

    private fun notifyRunning() {
        synchronized(mRunningLock) {
            mRunning = true
            mRunningLock.notifyAll()
        }
    }

    @CallSuper
    protected open fun onExit() {
        scriptRuntime.loopers.notifyThreadExit(this)
    }

    val timer: Timer
        get() {
            checkNotNull(mTimer) { str(R.string.error_thread_is_not_alive) }
            return mTimer!!
        }

    fun setTimeout(callback: Any, delay: Long, vararg args: Array<out Any?>) = timer.setTimeout(callback, delay, *args)

    fun clearTimeout(id: Int) = timer.clearTimeout(id)

    fun setInterval(listener: Any, interval: Long, vararg args: Array<out Any?>) = timer.setInterval(listener, interval, *args)

    fun clearInterval(id: Int) = timer.clearInterval(id)

    fun setImmediate(listener: Any, vararg args: Array<out Any?>) = timer.setImmediate(listener, *args)

    fun clearImmediate(id: Int) = timer.clearImmediate(id)

    @Throws(InterruptedException::class)
    fun waitFor() {
        synchronized(mRunningLock) {
            if (!mRunning) {
                mRunningLock.wait()
            }
        }
    }

    override fun toString() = "Thread[$name,$priority]"

    companion object {

        private val sTimerMap = ConcurrentHashMap<Thread, Timer?>()

        @JvmStatic
        fun getTimerForThread(thread: Thread) = sTimerMap[thread]

        @JvmStatic
        val timerForCurrentThread
            get() = getTimerForThread(currentThread())

    }

}