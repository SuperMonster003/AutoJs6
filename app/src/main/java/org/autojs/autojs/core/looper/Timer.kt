package org.autojs.autojs.core.looper

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.SparseArray
import org.autojs.autojs.concurrent.VolatileBox
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.mozilla.javascript.Callable
import org.mozilla.javascript.ContinuationPending
import org.mozilla.javascript.Scriptable
import java.lang.ref.WeakReference
import java.util.concurrent.locks.LockSupport
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
    private var mPostOrder = 0L
    private var mWaiters = 0

    internal var onPendingCallbacksChanged: (() -> Unit)? = null

    // The due callbacks of this timer, run by its looper thread while it waits for a Promise instead of polling its looper
    // (SettlementLatch, docs/augment/ROADMAP.md Q26). A callback taken here is removed from the looper first, and runs
    // like the looper would run it.
    // zh-CN: 本 timer 已到期的回调, 由其 Looper 线程在等待 Promise 而不轮询 Looper 时运行 (SettlementLatch, docs/augment/ROADMAP.md Q26).
    // 在此取出的回调先从 Looper 中移除, 运行方式与 Looper 相同.
    private val mCallbackQueue = object : SettlementLatch.CallbackQueue {

        override fun takeDueCallback(): Runnable? = synchronized(this@Timer) {
            val next = earliestPendingCallback()?.takeIf { it.uptime <= SystemClock.uptimeMillis() } ?: return null
            mHandler.removeCallbacks(next)
            next
        }

        override fun millisUntilNextCallback(): Long = synchronized(this@Timer) {
            val next = earliestPendingCallback() ?: return -1L
            max(next.uptime - SystemClock.uptimeMillis(), 1L)
        }

        override fun beginWait() {
            synchronized(this@Timer) { mWaiters++ }
        }

        override fun endWait() {
            synchronized(this@Timer) { mWaiters-- }
        }

    }

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
            val pending = PendingCallback(r, uptime, mPostOrder++)
            mPendingCallbacks.add(pending)
            if (!mHandler.postAtTime(pending, uptime)) {
                mPendingCallbacks.remove(pending)
                throw TimerCallbackRejectedException(if (isStopping()) ScriptInterruptedException() else null)
            }
            if (mWaiters > 0) LockSupport.unpark(mLooper.thread)
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

    /** The due callbacks of this timer for [SettlementLatch], or null when the current thread is not its looper thread. */
    internal fun callbackQueueForCurrentThread(): SettlementLatch.CallbackQueue? = mCallbackQueue.takeIf { mLooper.thread === Thread.currentThread() }

    // In the order of the looper: by due time, then in the order of posting.
    private fun earliestPendingCallback(): PendingCallback? = mPendingCallbacks.filter { !it.running }.minWithOrNull(compareBy<PendingCallback>({ it.uptime }, { it.order }))

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
        runCallback({ mLooper == Looper.getMainLooper() }, { scriptRuntime.exit(it) }) {
            if (callback is Scriptable && callback.parentScope == null) {
                callback.parentScope = scriptRuntime.topLevelScope
            }
            scriptRuntime.bridges.call(callback, thisArg, args)
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

    // Whether the looper rejected a callback because the calling thread is being stopped. A stop (forceStop of the
    // engine, interrupt of a TimerThread) first quits the looper of the thread from another thread, removing it from
    // LooperHelper, and only then interrupts it, so a setInterval callback finishing in between and posting to its own
    // looper sees neither an interrupt nor an exiting runtime. Such a rejection was reported as a script error with an
    // error toast (ROADMAP Q32, about 1 in 20 forced stops in the memory gate). Posting to the looper of another thread
    // that has ended remains an error of the script.
    // zh-CN: Looper 拒绝回调是否因为调用线程正在被停止. 停止 (引擎的 forceStop, TimerThread 的 interrupt) 先由其他线程退出该线程的 Looper
    // 并将其移出 LooperHelper, 之后才中断它, 因此在两者之间结束并向自身 Looper 投递的 setInterval 回调既看不到中断也看不到 runtime 正在退出.
    // 这样的拒绝曾被当作脚本错误报告并弹出错误 Toast (路线图 Q32, 内存门禁中约每 20 次强制停止出现 1 次). 向已结束的其他线程的 Looper 投递仍是脚本的错误.
    private fun isStopping(): Boolean {
        val runtime = mRuntime.get() ?: return true
        val current = Thread.currentThread()
        return runtime.isExiting
                || current.isInterrupted
                || mLooper.thread === current && !LooperHelper.contains(current)
    }

    internal fun wakeForIdleCheck() {
        // Removing a future message does not guarantee another IdleHandler invocation.
        // A UI/worker timer also keeps the script's main looper alive until it finishes.
        mHandler.post(EMPTY_RUNNABLE)
        onPendingCallbacksChanged?.invoke()
    }

    // Drops the callback once it starts (or turns out to be cancelled), like TimerThread.ReleasingRunnable: when the
    // callback throws (exit() or a forced stop), Looper.loop does not recycle the dispatched Message, and another looper
    // thread may still hold a stale reference to that pooled Message, which kept the script alive through
    // Message.callback (ROADMAP H21, third path, S7 N on 23046RP50C and AVD API 24 in P3.5).
    // zh-CN: 回调一开始运行 (或发现已取消) 就丢弃它, 同 TimerThread.ReleasingRunnable: 回调抛出时 (exit() 或强制停止) Looper.loop 不回收已分发的 Message,
    // 另一个 Looper 线程可能仍持有该池化 Message 的陈旧引用, 曾经由 Message.callback 钉住脚本 (路线图 H21 第三条路径, P3.5 中 23046RP50C 与 AVD API 24 的 S7 N).
    private inner class PendingCallback(callback: Runnable, val uptime: Long, val order: Long) : Runnable {
        var callback: Runnable? = callback
            private set
        var running = false

        override fun run() {
            val callback = synchronized(this@Timer) {
                val callback = this.callback
                this.callback = null
                if (callback == null || this !in mPendingCallbacks) return
                running = true
                callback
            }
            try {
                runPosted(callback)
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

        /**
         * Runs a script callback of setTimeout / setInterval / setImmediate. A [ContinuationPending] ends the callback but is not
         * its failure: a coroutine resumed in it (continuation.delay, or any Promise.await, as promise.js settles through
         * setImmediate) suspended again, and the continuation is captured until it is resumed. It formerly exited the script on
         * the main looper (UI mode) and cancelled the callback of setInterval elsewhere (docs/augment/ROADMAP.md Q23).
         * Other exceptions exit the script on the main looper and propagate elsewhere, as before.
         *
         * zh-CN: 运行 setTimeout / setInterval / setImmediate 的脚本回调. ContinuationPending 会结束回调, 但不是回调的失败: 回调中恢复的协程
         * (continuation.delay, 以及任何 Promise.await, 因为 promise.js 经 setImmediate 结算) 再次挂起, 协程已被捕获, 等待之后恢复. 原先它在主 Looper 上 (UI 模式)
         * 会结束脚本, 在其他 Looper 上会使 setInterval 的回调被取消 (docs/augment/ROADMAP.md Q23). 其他异常照旧: 在主 Looper 上结束脚本, 其他情况继续抛出.
         */
        internal fun runCallback(isOnMainLooper: () -> Boolean, exit: (Throwable) -> Unit, call: () -> Unit) {
            try {
                call()
            } catch (_: ContinuationPending) {
                /* Suspended again, see above. */
            } catch (e: Exception) {
                if (!isOnMainLooper()) throw e
                exit(e)
            }
        }

        /**
         * Runs a runnable posted through [postDelayed]. A [ContinuationPending] does not reach the looper, for the same reason as
         * in [runCallback]: `Continuation.resumeWith` posts the resume when it is called on another thread, and a coroutine
         * suspending again there formerly crashed the app on the main looper (docs/augment/ROADMAP.md Q23).
         *
         * zh-CN: 运行经 [postDelayed] 投递的 Runnable. 与 [runCallback] 同理, ContinuationPending 不会抛到 Looper: 在其他线程调用 `Continuation.resumeWith` 时
         * 会投递恢复操作, 协程在其中再次挂起时, 原先会在主 Looper 上使应用崩溃 (docs/augment/ROADMAP.md Q23).
         */
        internal fun runPosted(r: Runnable) {
            try {
                r.run()
            } catch (_: ContinuationPending) {
                /* Suspended again, see above. */
            }
        }

        private fun currentOrPreparedLooper(): Looper {
            LooperHelper.prepare()
            return Looper.myLooper()
                ?: throw IllegalStateException("Failed to prepare looper for script timer")
        }

    }

}
