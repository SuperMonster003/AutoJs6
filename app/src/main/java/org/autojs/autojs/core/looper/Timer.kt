package org.autojs.autojs.core.looper

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.SparseArray
import org.autojs.autojs.concurrent.VolatileBox
import org.autojs.autojs.runtime.ScriptRuntime
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Scriptable
import java.lang.ref.WeakReference
import kotlin.math.max

/**
 * Created by Stardust on Dec 27, 2017.
 * Modified by aiselp as of Jun 14, 2023.
 * Modified by SuperMonster003 as of Jul 12, 2023.
 */
class Timer {

    private var mLooper: Looper
    private var mCallbackMaxId = 0
    private var mHandler: Handler
    private var mHandlerCallbacks = SparseArray<Runnable>()
    private val mPendingCallbacks = HashSet<PendingCallback>()
    private var mMaxCallbackMillisForAllThread: VolatileBox<Long>
    private var mRuntime: WeakReference<ScriptRuntime>
    private var mTimerId = 0

    internal var onPendingCallbacksChanged: (() -> Unit)? = null

    constructor(scriptRuntime: ScriptRuntime, maxCallbackMillisForAllThread: VolatileBox<Long>, timerId: Int) : this(scriptRuntime, maxCallbackMillisForAllThread, currentOrPreparedLooper(), timerId)

    constructor(scriptRuntime: ScriptRuntime, maxCallbackMillisForAllThread: VolatileBox<Long>, looper: Looper, timerId: Int) {
        mRuntime = WeakReference(scriptRuntime)
        mMaxCallbackMillisForAllThread = maxCallbackMillisForAllThread
        mTimerId = timerId
        mLooper = looper
        mHandler = Handler(looper)
    }

    fun setTimeout(callback: Callable, delay: Long, vararg args: Any?): Double {
        synchronized(this) {
            val id = (mCallbackMaxId + 1).also { mCallbackMaxId = it }
            val r = Runnable {
                try {
                    callFunction(callback, null, args)
                } finally {
                    synchronized(this@Timer) {
                        mHandlerCallbacks.remove(id)
                    }
                }
            }
            mHandlerCallbacks.put(id, r)
            try {
                postDelayed(r, delay)
            } catch (error: Throwable) {
                mHandlerCallbacks.remove(id)
                throw error
            }
            return wrapId(id)
        }
    }

    fun clearTimeout(n: Double): Boolean {
        synchronized(this) { return clearCallback(unwrapId(n)) }
    }

    fun setInterval(callback: Callable, interval: Long, vararg args: Any?): Double {
        synchronized(this) {
            val id = (mCallbackMaxId + 1).also { mCallbackMaxId = it }
            val r = object : Runnable {
                override fun run() {
                    synchronized(this@Timer) {
                        if (mHandlerCallbacks[id] !== this) return
                    }
                    try {
                        callFunction(callback, null, args)
                        synchronized(this@Timer) {
                            // A callback can cancel itself, or be cancelled by another thread.
                            if (mHandlerCallbacks[id] === this) postDelayed(this, interval)
                        }
                    } catch (error: Throwable) {
                        synchronized(this@Timer) {
                            mHandlerCallbacks.remove(id)
                        }
                        throw error
                    }
                }
            }
            mHandlerCallbacks.put(id, r)
            try {
                postDelayed(r, interval)
            } catch (error: Throwable) {
                mHandlerCallbacks.remove(id)
                throw error
            }
            return wrapId(id)
        }
    }

    fun clearInterval(n: Double): Boolean {
        synchronized(this) { return clearCallback(unwrapId(n)) }
    }

    fun setImmediate(callback: Callable, vararg args: Any?): Double {
        synchronized(this) {
            val id = (mCallbackMaxId + 1).also { mCallbackMaxId = it }
            val r = Runnable {
                try {
                    callFunction(callback, null, args)
                } finally {
                    synchronized(this@Timer) {
                        mHandlerCallbacks.remove(id)
                    }
                }
            }
            mHandlerCallbacks.put(id, r)
            try {
                postDelayed(r, 0L)
            } catch (error: Throwable) {
                mHandlerCallbacks.remove(id)
                throw error
            }
            return wrapId(id)
        }
    }

    fun clearImmediate(n: Double): Boolean {
        synchronized(this) { return clearCallback(unwrapId(n)) }
    }

    fun postDelayed(r: Runnable, interval: Long) {
        synchronized(this) {
            val uptime = interval + SystemClock.uptimeMillis()
            val pending = PendingCallback(r)
            mPendingCallbacks.add(pending)
            if (!mHandler.postAtTime(pending, uptime)) {
                mPendingCallbacks.remove(pending)
                throw TimerCallbackRejectedException()
            }
            // Retain the historical high-water mark for callers, but never use it to decide
            // whether work remains: cancelled callbacks can leave this far in the future.
            synchronized(mMaxCallbackMillisForAllThread) {
                mMaxCallbackMillisForAllThread.set(max(mMaxCallbackMillisForAllThread.get(), uptime))
            }
        }
    }

    fun hasPendingCallbacks(): Boolean {
        return synchronized(this) { mPendingCallbacks.isNotEmpty() }
    }

    fun removeAllCallbacks() {
        synchronized(this) {
            mHandler.removeCallbacksAndMessages(null)
            mHandlerCallbacks.clear()
            mPendingCallbacks.removeAll { !it.running }
            wakeForIdleCheck()
        }
    }

    @Suppress("SameParameterValue")
    private fun callFunction(callback: Callable, thisArg: Any?, args: Array<*>) {
        val scriptRuntime = mRuntime.get()
            ?: throw IllegalStateException("call function after runtime released")
        try {
            if (callback is Scriptable && callback.parentScope == null) {
                callback.parentScope = scriptRuntime.topLevelScope
            }
            scriptRuntime.bridges.call(callback, thisArg, args)
        } catch (e: Exception) {
            if (mLooper != Looper.getMainLooper()) throw e
            scriptRuntime.exit(e)
        }
    }

    private fun clearCallback(id: Int): Boolean {
        val runnable = mHandlerCallbacks[id]
        if (runnable != null) {
            mHandlerCallbacks.remove(id)
            val iterator = mPendingCallbacks.iterator()
            while (iterator.hasNext()) {
                val pending = iterator.next()
                if (pending.callback === runnable && !pending.running) {
                    mHandler.removeCallbacks(pending)
                    iterator.remove()
                }
            }
            wakeForIdleCheck()
            return true
        }
        return false
    }

    internal fun wakeForIdleCheck() {
        // Removing a future message does not guarantee another IdleHandler invocation.
        // A UI/worker timer also keeps the script's main looper alive until it finishes.
        mHandler.post(EMPTY_RUNNABLE)
        onPendingCallbacksChanged?.invoke()
    }

    private inner class PendingCallback(val callback: Runnable) : Runnable {
        var running = false

        override fun run() {
            synchronized(this@Timer) {
                if (this !in mPendingCallbacks) return
                running = true
            }
            try {
                callback.run()
            } finally {
                synchronized(this@Timer) {
                    mPendingCallbacks.remove(this)
                    if (mPendingCallbacks.isEmpty()) wakeForIdleCheck()
                }
            }
        }
    }

    private fun wrapId(n: Int) = ((mTimerId.toLong() shl 32) + n).toDouble()

    private fun unwrapId(n: Double): Int {
        val nLong = n.toLong()
        if (nLong shr 32 == mTimerId.toLong()) {
            return (-1L and nLong).toInt()
        }
        throw IllegalArgumentException("id $n is not belong to timer $mTimerId")
    }

    companion object {

        @JvmField
        val EMPTY_RUNNABLE = Runnable {}

        @JvmStatic
        fun getTimerId(n: Double) = (n.toLong() shr 32).toInt()

        private fun currentOrPreparedLooper(): Looper {
            LooperHelper.prepare()
            return Looper.myLooper()
                ?: throw IllegalStateException("Failed to prepare looper for script timer")
        }

    }

}
