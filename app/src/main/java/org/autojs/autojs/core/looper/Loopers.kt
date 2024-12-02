package org.autojs.autojs.core.looper

import android.os.Handler
import android.os.Looper
import android.os.MessageQueue
import android.util.Log
import org.autojs.autojs.lang.ThreadCompat
import org.autojs.autojs.rhino.AutoJsContext
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.RhinoUtils.isMainThread
import org.mozilla.javascript.Context
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.Volatile

/**
 * Created by Stardust on Jul 29, 2017.
 * Modified by SuperMonster003 as of Aug 28, 2023.
 */
class Loopers(val scriptRuntime: ScriptRuntime) : MessageQueue.IdleHandler {

    @Volatile
    private var mServantLooper: Looper? = null

    private var mMainLooper: Looper?
    private var mMainHandler: Handler?
    private var mMainLooperQuitHandler: LooperQuitHandler? = null
    private var mMainMessageQueue: MessageQueue
    private var mLooperQuitHandlers = ThreadLocal<CopyOnWriteArrayList<LooperQuitHandler>>()
    private var mTimers = scriptRuntime.timers
    private var mThreads = scriptRuntime.threads
    private val mLock = Object()

    private var mMaxWaitId: ThreadLocal<Int> = object : ThreadLocal<Int>() {
        override fun initialValue() = 0
    }
    private var mWaitIds: ThreadLocal<HashSet<Int>> = object : ThreadLocal<HashSet<Int>>() {
        override fun initialValue() = HashSet<Int>()
    }
    private var mWaitWhenIdle: ThreadLocal<Boolean> = object : ThreadLocal<Boolean>() {
        override fun initialValue() = isMainThread()
    }

    val servantLooper: Looper
        get() {
            if (mServantLooper == null) {
                initServantThread()
                synchronized(mLock) {
                    try {
                        try {
                        mLock.wait()
                        } finally {
                            mLock.notify()
                        }
                    } catch (ex: InterruptedException) {
                        /* Ignored. */
                    }
                }
            }
            return mServantLooper!!
        }

    init {
        prepare()
        mMainLooper = Looper.myLooper()
        @Suppress("DEPRECATION")
        mMainHandler = Handler()
        mMainMessageQueue = Looper.myQueue()
    }

    fun prepare() {
        if (Looper.myLooper() == null) {
            LooperHelper.prepare()
        }
        Looper.myQueue().addIdleHandler(this)
    }

    fun waitWhenIdle(): Int {
        val intValue = mMaxWaitId.get() ?: return -1
        mMaxWaitId.set(intValue + 1)
        mWaitIds.get()?.add(intValue)
        return intValue
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
        mMainMessageQueue.removeIdleHandler(this)
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

    override fun queueIdle(): Boolean {
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

    private fun initServantThread() {
        ThreadCompat {
            Looper.prepare()
            mServantLooper = Looper.myLooper()
            synchronized(mLock) {
                mLock.notifyAll()
            }
            Looper.loop()
        }.start()
    }

    private fun quitServantLooper() {
        mServantLooper?.quit()
    }

    interface LooperQuitHandler {
        fun shouldQuit(): Boolean
    }

    companion object {

        private const val LOG_TAG = "Loopers"
        private val EMPTY_RUNNABLE = Runnable {}

    }

}