package org.autojs.autojs.core.looper

import android.os.Handler
import android.os.Looper
import android.os.MessageQueue
import android.os.MessageQueue.IdleHandler
import android.util.Log
import org.autojs.autojs.lang.ThreadCompat
import org.autojs.autojs.rhino.AutoJsContext
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.Threads
import org.autojs.autojs.runtime.api.Timers
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.mozilla.javascript.Context
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Created by Stardust on 2017/7/29.
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
class Loopers(runtime: ScriptRuntime) : IdleHandler {
    interface LooperQuitHandler {
        fun shouldQuit(): Boolean
    }

    private val waitWhenIdle: ThreadLocal<Boolean> = object : ThreadLocal<Boolean>() {
        override fun initialValue(): Boolean {
            return Looper.myLooper() == Looper.getMainLooper()
        }
    }
    private val waitIds: ThreadLocal<HashSet<Int>> = object : ThreadLocal<HashSet<Int>>() {
        override fun initialValue(): HashSet<Int> {
            return HashSet()
        }
    }
    private val maxWaitId: ThreadLocal<Int> = object : ThreadLocal<Int>() {
        override fun initialValue(): Int {
            return 0
        }
    }
    private val looperQuitHandlers = ThreadLocal<CopyOnWriteArrayList<LooperQuitHandler>>()

    @Volatile
    private var mServantLooper: Looper? = null
    private val mTimers: Timers
    private var mMainLooperQuitHandler: LooperQuitHandler? = null
    private val mMainHandler: Handler
    private val mMainLooper: Looper?
    private val mThreads: Threads
    private val mMainMessageQueue: MessageQueue

    init {
        mTimers = runtime.timers
        mThreads = runtime.threads
        prepare()
        mMainLooper = Looper.myLooper()
        mMainHandler = Handler(Looper.getMainLooper())
        mMainMessageQueue = Looper.myQueue()
    }

    fun addLooperQuitHandler(handler: LooperQuitHandler) {
        var handlers = looperQuitHandlers.get()
        if (handlers == null) {
            handlers = CopyOnWriteArrayList()
            looperQuitHandlers.set(handlers)
        }
        handlers.add(handler)
    }

    fun removeLooperQuitHandler(handler: LooperQuitHandler): Boolean {
        val handlers = looperQuitHandlers.get()
        return handlers != null && handlers.remove(handler)
    }

    private fun shouldQuitLooper(): Boolean {
        if (Thread.currentThread().isInterrupted) {
            return true
        }
        if (mTimers.hasPendingCallbacks()) {
            return false
        }
        if (waitWhenIdle.get() == true || waitIds.get()?.isNotEmpty() == true) {
            return false
        }
        if ((Context.getCurrentContext() as AutoJsContext).hasPendingContinuation()) {
            return false
        }
        val handlers = looperQuitHandlers.get() ?: return true
        for (handler in handlers) {
            if (!handler.shouldQuit()) {
                return false
            }
        }
        return true
    }

    private fun initServantThread() {
        ThreadCompat {
            Looper.prepare()
            mServantLooper = Looper.myLooper()
            @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
            synchronized(this@Loopers as Object) {
                notifyAll()
            }
            Looper.loop()
        }.start()
    }

    val servantLooper: Looper
        get() {
            if (mServantLooper == null) {
                initServantThread()
                @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                synchronized(this@Loopers as Object) {
                    try {
                        wait()
                    } catch (e: InterruptedException) {
                        throw ScriptInterruptedException(e)
                    }
                }
            }
            return mServantLooper!!
        }

    private fun quitServantLooper() {
        mServantLooper?.quit()
    }

    fun waitWhenIdle(): Int {
        val id = maxWaitId.get()!!
        Log.d(LOG_TAG, "waitWhenIdle: $id")
        maxWaitId.set(id + 1)
        waitIds.get()!!.add(id)
        return id
    }

    fun doNotWaitWhenIdle(waitId: Int) {
        Log.d(LOG_TAG, "doNotWaitWhenIdle: $waitId")
        waitIds.get()!!.remove(waitId)
    }

    fun waitWhenIdle(b: Boolean) {
        waitWhenIdle.set(b)
    }

    fun recycle() {
        quitServantLooper()
        mMainMessageQueue.removeIdleHandler(this)
    }

    fun setMainLooperQuitHandler(mainLooperQuitHandler: LooperQuitHandler?) {
        mMainLooperQuitHandler = mainLooperQuitHandler
    }

    override fun queueIdle(): Boolean {
        val l = Looper.myLooper() ?: return true
        if (l == mMainLooper) {
            Log.d(LOG_TAG, "main looper queueIdle")
            if (shouldQuitLooper() && !mThreads.hasRunningThreads() && mMainLooperQuitHandler != null && mMainLooperQuitHandler!!.shouldQuit()) {
                Log.d(LOG_TAG, "main looper quit")
                l.quit()
            }
        } else {
            Log.d(LOG_TAG, "looper queueIdle: $l")
            if (shouldQuitLooper()) {
                l.quit()
            }
        }
        return true
    }

    fun prepare() {
        if (Looper.myLooper() == null) {
            LooperHelper.prepare()
        }
        Looper.myQueue().addIdleHandler(this)
    }

    fun notifyThreadExit(thread: TimerThread) {
        Log.d(LOG_TAG, "notifyThreadExit: $thread")
        // 当子线程退成时，主线程需要检查自身是否退出（主线程在所有子线程执行完成后才能退出，如果主线程已经执行完任务仍然要等待所有子线程），
        // 此时通过向主线程发送一个空的Runnable，主线程执行完这个Runnable后会触发IdleHandler，从而检查自身是否退出
        mMainHandler.post(EMPTY_RUNNABLE)
    }

    companion object {
        private const val LOG_TAG = "Loopers"
        private val EMPTY_RUNNABLE = Runnable {}
    }
}