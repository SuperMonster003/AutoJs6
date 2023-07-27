package org.autojs.autojs.core.looper

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.SparseArray
import org.autojs.autojs.concurrent.VolatileBox
import org.autojs.autojs.runtime.ScriptRuntime
import kotlin.math.max

/**
 * Created by Stardust on 2017/12/27.
 * Modified by SuperMonster003 as of Jul 12, 2023.
 * Transformed by SuperMonster003 on Jul 12, 2023.
 */
// @Overruled by SuperMonster003 on Jul 12, 2023.
//  ! Author: aiselp
//  ! Related PR:
//  ! http://pr.autojs6.com/75
//  ! http://pr.autojs6.com/78
//  ! Reason:
//  ! Sorry but my current capabilities are not sufficient
//  ! to fully understand everything from above pull request(s),
//  ! so most of the code will remain as is. :)
class Timer @JvmOverloads constructor(runtime: ScriptRuntime, maxCallbackMillisForAllThread: VolatileBox<Long>, private val looper: Looper? = Looper.myLooper()) {

    private val mHandlerCallbacks = SparseArray<Runnable?>()
    private var mCallbackMaxId = 0
    private val mRuntime: ScriptRuntime = runtime
    @Suppress("DEPRECATION")
    private val mHandler = looper?.let { Handler(it) } ?: Handler()
    private var mMaxCallbackUptimeMillis: Long = 0
    private val mMaxCallbackMillisForAllThread: VolatileBox<Long> = maxCallbackMillisForAllThread

    fun setTimeout(callback: Any, delay: Long, vararg args: Array<out Any?>): Int {
        mCallbackMaxId++
        val id = mCallbackMaxId
        val r = Runnable {
            callFunction(callback, args)
            mHandlerCallbacks.remove(id)
        }
        mHandlerCallbacks.put(id, r)
        postDelayed(r, delay)
        return id
    }

    private fun callFunction(callback: Any, args: Array<out Any>) {
        try {
            mRuntime.bridges.callFunction(callback, null, args)
        } catch (e: Exception) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                mRuntime.exit(e)
            } else {
                throw e
            }
        }
    }

    fun clearTimeout(id: Int) = clearCallback(id)

    fun setInterval(listener: Any, interval: Long, vararg args: Any): Int {
        mCallbackMaxId++
        val id = mCallbackMaxId
        val r = object : Runnable {
            override fun run() {
                mHandlerCallbacks[id] ?: return
                callFunction(listener, args)
                postDelayed(this, interval)
            }
        }
        mHandlerCallbacks.put(id, r)
        postDelayed(r, interval)
        return id
    }

    fun postDelayed(r: Runnable, interval: Long) {
        val uptime = SystemClock.uptimeMillis() + interval
        mHandler.postAtTime(r, uptime)
        mMaxCallbackUptimeMillis = mMaxCallbackUptimeMillis.coerceAtLeast(uptime)
        synchronized(mMaxCallbackMillisForAllThread) { mMaxCallbackMillisForAllThread.set(max(mMaxCallbackMillisForAllThread.get(), uptime)) }
    }

    // @Reference to aiselp (https://github.com/aiselp) on Jul 18, 2023.
    fun post(r: Runnable) {
        looper?.let {
            synchronized(it) {
                mHandler.post(r)
            }
        }
    }

    fun clearInterval(id: Int) = clearCallback(id)

    fun setImmediate(listener: Any, vararg args: Any): Int {
        mCallbackMaxId++
        val id = mCallbackMaxId
        val r = Runnable {
            callFunction(listener, args)
            mHandlerCallbacks.remove(id)
        }
        mHandlerCallbacks.put(id, r)
        postDelayed(r, 0)
        return id
    }

    fun clearImmediate(id: Int) = clearCallback(id)

    private fun clearCallback(id: Int): Boolean {
        val callback = mHandlerCallbacks[id]
        if (callback != null) {
            mHandler.removeCallbacks(callback)
            mHandlerCallbacks.remove(id)
            return true
        }
        return false
    }

    fun hasPendingCallbacks() = mMaxCallbackUptimeMillis > SystemClock.uptimeMillis()

    fun removeAllCallbacks() = mHandler.removeCallbacksAndMessages(null)

}