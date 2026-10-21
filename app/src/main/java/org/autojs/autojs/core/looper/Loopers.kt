package org.autojs.autojs.core.looper

import android.os.Handler
import android.os.Looper
import android.os.MessageQueue
import android.util.Log
import org.autojs.autojs.lang.ThreadCompat
import org.autojs.autojs.rhino.AutoJsContext
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.resource.RuntimeResourceScope.Stage
import org.autojs.autojs.util.RhinoUtils.isMainThread
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
        if (looper == mMainLooper) {
            if (!shouldQuitLooper()) return true
            if (mThreads.hasRunningThreads()) return true
            val looperQuitHandler = mMainLooperQuitHandler ?: return true
            if (!looperQuitHandler.shouldQuit()) return true
        } else {
            if (!shouldQuitLooper()) return true
        }
        looper.quit()
        return true
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
