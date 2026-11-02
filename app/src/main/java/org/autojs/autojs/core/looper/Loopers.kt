package org.autojs.autojs.core.looper

import android.os.Handler
import android.os.Looper
import android.os.MessageQueue
import android.util.Log
import org.autojs.autojs.lang.ThreadCompat
import org.autojs.autojs.rhino.AutoJsContext
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.resource.RuntimeResourceScope.Stage
import org.autojs.autojs.util.RhinoUtils.isMainThread
import org.autojs.autojs6.R
import org.mozilla.javascript.AsyncTracker
import org.mozilla.javascript.Context
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Created by Stardust on Jul 29, 2017.
 * Modified by SuperMonster003 as of Aug 28, 2023.
 */
class Loopers(val scriptRuntime: ScriptRuntime) {

    // Added to the MessageQueue of every thread prepared for this script. It references this object only weakly,
    // so a queue which outlives the script does not keep the runtime reachable (docs/memory/ROADMAP.md H2).
    // zh-CN: 加入为本脚本准备过的每个线程的 MessageQueue. 它只弱引用本对象, 因此比脚本活得久的消息队列不会让运行时保持可达 (docs/memory/ROADMAP.md H2).
    private val mIdleHandler = WeakIdleHandler(this)

    private var mMainLooper: Looper?
    private var mMainHandler: Handler?
    private var mMainLooperQuitHandler: LooperQuitHandler? = null
    private var mMainMessageQueue: MessageQueue
    private var mLooperQuitHandlers = ThreadLocal<CopyOnWriteArrayList<LooperQuitHandler>>()
    private var mTimers = scriptRuntime.timers
    private var mThreads = scriptRuntime.threads

    // The async functions of the threads that ended because nothing was left, for the warning at the end of the script. The
    // trackers, not their counts: another thread can still resume such a function.
    // zh-CN: 因无事可做而结束的线程上的 async 函数, 用于脚本结束时的警告. 保存 tracker 而不是数量: 其他线程仍可能恢复这些函数.
    private val mEndedThreadAwaits = CopyOnWriteArrayList<AsyncTracker>()
    private val mServantLooper = ServantLooperHolder<Looper>(
        starter = { publish, fail ->
            ThreadCompat {
                val looper = try {
                    Looper.prepare()
                    checkNotNull(Looper.myLooper())
                } catch (t: Throwable) {
                    fail(t)
                    throw t
                }
                publish(looper)
                Looper.loop()
            }.start()
        },
        disposer = Looper::quit,
    )

    private var mMaxWaitId: ThreadLocal<Int> = object : ThreadLocal<Int>() {
        override fun initialValue() = 0
    }
    private var mWaitIds: ThreadLocal<MutableSet<Int>> = object : ThreadLocal<MutableSet<Int>>() {
        override fun initialValue(): MutableSet<Int> = ConcurrentHashMap.newKeySet()
    }
    private var mWaitWhenIdle: ThreadLocal<Boolean> = object : ThreadLocal<Boolean>() {
        override fun initialValue() = isMainThread()
    }

    val servantLooper: Looper
        get() = mServantLooper.get()

    init {
        prepare()
        mMainLooper = Looper.myLooper()
        @Suppress("DEPRECATION")
        mMainHandler = Handler()
        mMainMessageQueue = Looper.myQueue()
        scriptRuntime.resourceScope.own(Stage.LOOPERS, "loopers.recycle") { recycle() }
    }

    fun prepare() {
        if (Looper.myLooper() == null) {
            LooperHelper.prepare()
        }
        Looper.myQueue().addIdleHandler(mIdleHandler)
    }

    fun waitWhenIdle(): Int {
        val intValue = requireNotNull(mMaxWaitId.get())
        mMaxWaitId.set(intValue + 1)
        requireNotNull(mWaitIds.get()).add(intValue)
        return intValue
    }

    internal fun waitWhenIdleToken(): WaitToken {
        return createWaitToken()
    }

    private fun createWaitToken(): WaitToken {
        val wakeHandler = Handler(requireNotNull(Looper.myLooper()) {
            "Cannot wait for an idle callback on a thread without a looper"
        })
        val intValue = waitWhenIdle()
        val waitIds = requireNotNull(mWaitIds.get())
        return WaitToken(intValue, WakeUpOnRemoveSet(waitIds) {
            wakeHandler.post(EMPTY_RUNNABLE)
        })
    }

    fun waitWhenIdle(b: Boolean) {
        mWaitWhenIdle.set(b)
    }

    fun doNotWaitWhenIdle(waitId: Int) {
        mWaitIds.get()?.remove(waitId)
    }

    /**
     * A latch for a Promise that the current thread waits for (`Promise.prototype.wait`): the thread runs the due callbacks
     * of its timer while it waits, and the UI thread does not block (docs/augment/ROADMAP.md Q26).
     *
     * zh-CN: 当前线程等待 Promise 用的闩 (`Promise.prototype.wait`): 线程在等待期间运行自己 timer 上已到期的回调, UI 线程不阻塞
     * (docs/augment/ROADMAP.md Q26).
     */
    fun newSettlementLatch() = SettlementLatch(
        mTimers.timerForCurrentThread?.callbackQueueForCurrentThread(),
        mayBlock = !isMainThread(),
        microtasks = { (Context.getCurrentContext() as? AutoJsContext)?.runMicrotasks() },
    )

    fun addLooperQuitHandler(looperQuitHandler: LooperQuitHandler?) {
        val list: CopyOnWriteArrayList<LooperQuitHandler> = mLooperQuitHandlers.get()
            ?: CopyOnWriteArrayList<LooperQuitHandler>().also { mLooperQuitHandlers.set(it) }
        list.add(looperQuitHandler)
    }

    fun removeLooperQuitHandler(looperQuitHandler: LooperQuitHandler) = mLooperQuitHandlers.get()?.remove(looperQuitHandler) == true

    fun setMainLooperQuitHandler(looperQuitHandler: LooperQuitHandler?) {
        mMainLooperQuitHandler = looperQuitHandler
    }

    fun getMainLooper() = mMainLooper

    fun recycle() {
        Log.d(LOG_TAG, "recycle")
        quitServantLooper()
        mMainMessageQueue.removeIdleHandler(mIdleHandler)
        mMainHandler = null
        mMainLooperQuitHandler = null
        mMainLooper = null
    }

    fun notifyThreadExit(thread: TimerThread) {
        Log.d(LOG_TAG, "notifyThreadExit: $thread")
        // 当子线程退成时, 主线程需要检查自身是否退出 (主线程在所有子线程执行完成后才能退出, 如果主线程已经执行完任务仍然要等待所有子线程),
        // 此时通过向主线程发送一个空的 Runnable, 主线程执行完这个 Runnable 后会触发 IdleHandler, 从而检查自身是否退出
        mMainHandler?.post(EMPTY_RUNNABLE)
    }

    private fun queueIdle(): Boolean {
        val looper = Looper.myLooper() ?: return true
        runMicrotasks(looper)
        if (looper == mMainLooper) {
            if (!shouldQuitLooper()) return true
            if (mThreads.hasRunningThreads()) return true
            val looperQuitHandler = mMainLooperQuitHandler ?: return true
            if (!looperQuitHandler.shouldQuit()) return true
            warnAboutPendingAwaits()
        } else {
            if (!shouldQuitLooper()) return true
            keepPendingAwaits()
        }
        looper.quit()
        return true
    }

    /**
     * The checkpoint before the looper decides that nothing is left, for what no top call ended on this thread. MessageQueue only
     * logs what an IdleHandler throws, so a failure leaves as that of a timer callback.
     * zh-CN: Looper 判定无事可做之前的检查点, 用于本线程上没有顶层调用收尾的情况. MessageQueue 只记录 IdleHandler 抛出的异常, 因此失败时按 timer 回调的方式处理.
     */
    private fun runMicrotasks(looper: Looper) {
        if (Thread.currentThread().isInterrupted) return
        val context = Context.getCurrentContext() as? AutoJsContext ?: return
        try {
            Timer.runCallback({ looper == Looper.getMainLooper() }, { scriptRuntime.exit(it) }) {
                context.microtaskCheckpoint(scriptRuntime.eventLoop)
            }
        } catch (e: Exception) {
            Handler(looper).post { throw e }
        }
    }

    private fun endedByItself() = !Thread.currentThread().isInterrupted && !scriptRuntime.isExiting

    private fun keepPendingAwaits() {
        val context = Context.getCurrentContext() ?: return
        if (endedByItself()) mEndedThreadAwaits += AsyncTracker.of(context)
    }

    /**
     * Warns once when the script ends because nothing is left to run while async functions wait for a Promise: a pending Promise
     * does not keep a script alive (appendix C Q7 of Rhino-For-AutoJs6). `exit()` and a manual stop end the script on purpose.
     * zh-CN: 脚本因无事可做而结束, 而 async 函数仍在等待 Promise 时警告一次: 未落定的 Promise 不会让脚本继续运行 (Rhino-For-AutoJs6 附录 C Q7). exit() 与手动停止是有意结束, 不提示.
     */
    private fun warnAboutPendingAwaits() {
        if (!endedByItself()) return
        val trackers = listOfNotNull(Context.getCurrentContext()?.let { AsyncTracker.of(it) }) + mEndedThreadAwaits
        val count = trackers.sumOf { it.pendingCount() }
        val first = trackers.firstNotNullOfOrNull { it.firstPending() } ?: return
        scriptRuntime.console.warn(
            ScriptMessages.resource(
                R.string.warn_async_functions_pending_at_script_end,
                "The script ended with %1\$d async function(s) still waiting for a Promise, the first at %2\$s:%3\$d. A pending Promise does not keep a script running",
                count,
                first.sourceName().toString(),
                first.lineNumber(),
            )
        )
    }

    private fun shouldQuitLooper(): Boolean {
        return when {
            Thread.currentThread().isInterrupted -> true
            mTimers.hasPendingCallbacks() -> false
            mWaitWhenIdle.get() != false -> false
            mWaitIds.get()?.isEmpty() != true -> false
            else -> {
                val context = Context.getCurrentContext()
                if (context != null && (context as AutoJsContext).continuations.isNotEmpty()) {
                    return false
                }

                val looperQuitHandlers = mLooperQuitHandlers.get() ?: return true

                val iterator = looperQuitHandlers.iterator()
                do if (!iterator.hasNext()) return true
                while (iterator.next().shouldQuit())

                return false
            }
        }
    }

    private fun quitServantLooper() {
        mServantLooper.close()
    }

    interface LooperQuitHandler {
        fun shouldQuit(): Boolean
    }

    private class WeakIdleHandler(loopers: Loopers) : MessageQueue.IdleHandler {

        private val mLoopers = WeakReference(loopers)

        // Returning false removes this handler from the queue once the script runtime has been collected.
        // zh-CN: 脚本运行时被回收后返回 false, 使本处理器从队列中移除.
        override fun queueIdle() = mLoopers.get()?.queueIdle() ?: false
    }

    internal class WaitToken internal constructor(
        internal val id: Int,
        private val waitIds: MutableSet<Int>,
    ) {

        private val released = AtomicBoolean(false)

        fun release() {
            if (released.compareAndSet(false, true)) {
                waitIds.remove(id)
            }
        }
    }

    companion object {

        private const val LOG_TAG = "Loopers"
        private val EMPTY_RUNNABLE = Runnable {}

    }

}
