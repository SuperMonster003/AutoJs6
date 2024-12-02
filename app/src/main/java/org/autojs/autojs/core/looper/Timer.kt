package org.autojs.autojs.core.looper

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.SparseArray
import org.autojs.autojs.concurrent.VolatileBox
import org.autojs.autojs.runtime.ScriptRuntime
import org.mozilla.javascript.BaseFunction
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
    private var mMaxCallbackMillisForAllThread: VolatileBox<Long>
    private var mMaxCallbackUptimeMillis = 0L
    private var mRuntime: WeakReference<ScriptRuntime>
    private var mTimerId = 0

    constructor(scriptRuntime: ScriptRuntime, maxCallbackMillisForAllThread: VolatileBox<Long>, timerId: Int) : this(scriptRuntime, maxCallbackMillisForAllThread, Looper.myLooper()!!, timerId)

    constructor(scriptRuntime: ScriptRuntime, maxCallbackMillisForAllThread: VolatileBox<Long>, looper: Looper, timerId: Int) {
        mRuntime = WeakReference(scriptRuntime)
        mMaxCallbackMillisForAllThread = maxCallbackMillisForAllThread
        mTimerId = timerId
        mLooper = looper
        mHandler = Handler(looper)
    }

    fun setTimeout(callback: BaseFunction, delay: Long, vararg args: Any?): Double {
        synchronized(this) {
            val id = (mCallbackMaxId + 1).also { mCallbackMaxId = it }
            val r = Runnable {
                callFunction(callback, null, args)
                mHandlerCallbacks.remove(id)
            }
            mHandlerCallbacks.put(id, r)
            postDelayed(r, delay)
            return wrapId(id)
        }
    }

    fun clearTimeout(n: Double): Boolean {
        synchronized(this) { return clearCallback(unwrapId(n)) }
    }

    fun setInterval(callback: BaseFunction, interval: Long, vararg args: Any?): Double {
        synchronized(this) {
            val id = (mCallbackMaxId + 1).also { mCallbackMaxId = it }
            val r = object : Runnable {
                override fun run() {
                    if (mHandlerCallbacks[id] == null) return
                    callFunction(callback, null, args)
                    postDelayed(this, interval)
                }
            }
            mHandlerCallbacks.put(id, r)
            postDelayed(r, interval)
            return wrapId(id)
        }
    }

    fun clearInterval(n: Double): Boolean {
        synchronized(this) { return clearCallback(unwrapId(n)) }
    }

    fun setImmediate(callback: BaseFunction, vararg args: Any?): Double {
        synchronized(this) {
            val id = (mCallbackMaxId + 1).also { mCallbackMaxId = it }
            val r = Runnable {
                callFunction(callback, null, args)
                mHandlerCallbacks.remove(id)
            }
            mHandlerCallbacks.put(id, r)
            postDelayed(r, 0L)
            return wrapId(id)
        }
    }

    fun clearImmediate(n: Double): Boolean {
        synchronized(this) { return clearCallback(unwrapId(n)) }
    }

    fun postDelayed(r: Runnable, interval: Long) {
        synchronized(this) {
            val uptime = interval + SystemClock.uptimeMillis()
            mHandler.postAtTime(r, uptime)
            mMaxCallbackUptimeMillis = mMaxCallbackUptimeMillis.coerceAtLeast(uptime)
            synchronized(mMaxCallbackMillisForAllThread) {
                mMaxCallbackMillisForAllThread.set(max(mMaxCallbackMillisForAllThread.get(), uptime))
            }
        }
    }

    fun hasPendingCallbacks(): Boolean {
        return mMaxCallbackUptimeMillis > SystemClock.uptimeMillis()
    }

    fun removeAllCallbacks() {
        synchronized(this) { mHandler.removeCallbacksAndMessages(null) }
    }

    @Suppress("SameParameterValue")
    private fun callFunction(callback: BaseFunction, thisArg: Any?, args: Array<*>) {
        val scriptRuntime = mRuntime.get()
            ?: throw IllegalStateException("call function after runtime released")
        try {
            if (callback.parentScope == null) {
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
            mHandler.removeCallbacks(runnable)
            mHandlerCallbacks.remove(id)
            return true
        }
        return false
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

    }

}