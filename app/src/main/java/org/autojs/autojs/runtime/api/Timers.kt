package org.autojs.autojs.runtime.api

import android.os.Looper
import android.os.SystemClock
import org.autojs.autojs.concurrent.VolatileBox
import org.autojs.autojs.core.looper.Timer
import org.autojs.autojs.core.looper.Timer.Companion.getTimerId
import org.autojs.autojs.core.looper.TimerThread
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.RhinoUtils.isUiThread
import org.mozilla.javascript.BaseFunction
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by Stardust on Jul 21, 2017.
 * Modified by SuperMonster003 as of May 26, 2022.
 * Transformed by SuperMonster003 on Jul 5, 2024.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Dec 19, 2023.
class Timers(scriptRuntime: ScriptRuntime) {

    val mainTimer: Timer
    val maxCallbackUptimeMillisForAllThreads = VolatileBox(0L)

    val timerForCurrentThread: Timer?
        get() = getTimerForThread(Thread.currentThread())

    private val mTimerForCurrentOrMainThread: Timer
        get() = timerForCurrentThread ?: mainTimer

    private val mThreads = scriptRuntime.threads
    private val mUiTimer: Timer
    private val mTimers = ConcurrentHashMap<Int, WeakReference<Timer>>()
    private val mNextTimerId = AtomicInteger(2)

    init {
        mainTimer = Timer(scriptRuntime, maxCallbackUptimeMillisForAllThreads, TIMER_ID_MAIN)
        mUiTimer = Timer(scriptRuntime, maxCallbackUptimeMillisForAllThreads, Looper.getMainLooper(), TIMER_ID_UI)
    }

    fun getTimerForThread(thread: Thread): Timer? = when {
        thread == mThreads.mainThread -> mainTimer
        else -> {
            val timer = TimerThread.getTimerForThread(thread)
            when {
                timer != null -> timer
                isUiThread() -> mUiTimer
                else -> null
            }
        }
    }

    fun getTimerForId(id: Double): Timer? {
        return when (val timerId = getTimerId(id)) {
            TIMER_ID_MAIN -> mainTimer
            TIMER_ID_UI -> mUiTimer
            else -> mTimers[timerId]?.get()
        }
    }

    fun setTimeout(callback: BaseFunction, delay: Long, vararg args: Any?): Double {
        return mTimerForCurrentOrMainThread.setTimeout(callback, delay, *args)
    }

    fun setTimeout(callback: BaseFunction): Double {
        return setTimeout(callback, 1)
    }

    fun clearTimeout(id: Double): Boolean {
        return getTimerForId(id)?.clearTimeout(id) == true
    }

    fun setInterval(callback: BaseFunction, interval: Long, vararg args: Any?): Double {
        return mTimerForCurrentOrMainThread.setInterval(callback, interval, *args)
    }

    fun setInterval(callback: BaseFunction): Double {
        return setInterval(callback, 1)
    }

    fun clearInterval(id: Double): Boolean {
        return getTimerForId(id)?.clearInterval(id) == true
    }

    fun setImmediate(callback: BaseFunction, vararg args: Any?): Double {
        return mTimerForCurrentOrMainThread.setImmediate(callback, *args)
    }

    fun clearImmediate(id: Double): Boolean {
        return getTimerForId(id)?.clearImmediate(id) == true
    }

    fun recycle() {
        mainTimer.removeAllCallbacks()
    }

    fun hasPendingCallbacks(): Boolean {
        val mainThread = mThreads.mainThread
        val currentThread = Thread.currentThread()
        var hasPendingCallbacks = false
        if (mainThread == currentThread) {
            val upTime = maxCallbackUptimeMillisForAllThreads.get()
            if (upTime > SystemClock.uptimeMillis()) {
                hasPendingCallbacks = true
            }
        } else {
            val timer = timerForCurrentThread
            if (timer != null) {
                hasPendingCallbacks = timer.hasPendingCallbacks()
            }
        }
        return hasPendingCallbacks
    }

    fun newTimer(scriptRuntime: ScriptRuntime): Timer {
        val id = mNextTimerId.getAndIncrement()
        return Timer(scriptRuntime, maxCallbackUptimeMillisForAllThreads, id).also {
            mTimers[id] = WeakReference(it)
        }
    }

    companion object {

        private const val TIMER_ID_MAIN = 0
        private const val TIMER_ID_UI = 1

    }

}
