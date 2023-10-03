package org.autojs.autojs.core.looper

import android.os.Looper
import android.os.MessageQueue
import android.util.Log
import org.autojs.autojs.lang.ThreadCompat
import org.autojs.autojs.rhino.AutoJsContext
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.mozilla.javascript.Context
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Created by Stardust on 2017/7/29.
 * Modified by aiselp as of Jun 4, 2023.
 *  ! 调整内容:
 *  ! 使此类只负责单 loop 线程生命周期管理, 移除繁琐的调用链
 *  ! 调整 timer 由此类创建
 *  ! 通过向此类添加 AsyncTask 以监听线程退出事件
 * Modified by SuperMonster003 as of Aug 28, 2023.
 */
class Loopers(val runtime: ScriptRuntime) {

    @Volatile
    private var mServantLooper: Looper? = null
    @Suppress("DEPRECATION")
    private var mMainLooperQuitHandler: LooperQuitHandler? = null
    private var waitWhenIdle: Boolean
    private val allTasks = ConcurrentLinkedQueue<AsyncTask>()

    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    val servantLooper: Looper
        get() {
            if (mServantLooper == null) {
                initServantThread()
                lock.withLock {
                    try {
                        condition.await()
                    } catch (e: InterruptedException) {
                        throw ScriptInterruptedException(e)
                    }
                }
            }
            return mServantLooper!!
        }

    val timer: Timer
    val myLooper: Looper

    init {
        prepare()
        myLooper = Looper.myLooper()!!
        timer = Timer(runtime, myLooper)
        waitWhenIdle = myLooper == Looper.getMainLooper()
    }

    @Deprecated("Deprecated in Java", ReplaceWith("AsyncTask"))
    interface LooperQuitHandler {
        fun shouldQuit(): Boolean
    }

    open class AsyncTask(private val desc: String) {

        private val allBind = ConcurrentLinkedQueue<Loopers>()

        var isEnd: Boolean = false
            private set

        // 线程即将退出时调用, 返回 true 阻止线程退出, 只要有一个 task 返回 true 线程就不会退出
        open fun onFinish(loopers: Loopers) = true

        fun end() {
            isEnd = true
        }

        // 线程正在退出, 这里应该结束任务的执行, 回收资源
        open fun onStop(loopers: Loopers) = Unit

        override fun toString() = "AsyncTask: $desc"

    }

    fun createAndAddAsyncTask(desc: String): AsyncTask {
        return AsyncTask(desc).also { allTasks.add(it) }
    }

    fun addAsyncTask(task: AsyncTask) {
        synchronized(myLooper) {
            allTasks.add(task)
        }
    }

    fun removeAsyncTask(task: AsyncTask) {
        synchronized(myLooper) {
            allTasks.remove(task)
            timer.post(EMPTY_RUNNABLE)
        }
    }

    private fun checkTask(): Boolean {
        allTasks.removeAll(allTasks.filter { it.isEnd }.toSet())
        return allTasks.any { it.onFinish(this) }
    }

    private fun shouldQuitLooper(): Boolean {
        synchronized(myLooper) {
            return when {
                Thread.currentThread().isInterrupted -> true
                timer.hasPendingCallbacks() -> false
                // 检查是否有运行中的线程
                checkTask() -> false
                waitWhenIdle -> false
                (Context.getCurrentContext() as AutoJsContext).hasPendingContinuation() -> false
                else -> true
            }
        }
    }

    private fun initServantThread() {
        ThreadCompat {
            Looper.prepare()
            mServantLooper = Looper.myLooper()
            lock.withLock {
                condition.signalAll()
            }
            Looper.loop()
        }.start()
    }

    @Deprecated("Deprecated in Java", ReplaceWith("AsyncTask"))
    fun waitWhenIdle(b: Boolean) {
        waitWhenIdle = b
    }

    fun recycle() {
        Log.d(LOG_TAG, "recycle")
        for (task in allTasks.filter { !it.isEnd }) {
            try {
                task.onStop(this)
            } catch (e: Exception) {
                Log.w(LOG_TAG, e)
            }
        }
        mServantLooper?.quit()
    }

    @Deprecated("Deprecated in Java", ReplaceWith("AsyncTask"))
    fun setMainLooperQuitHandler(@Suppress("DEPRECATION") mainLooperQuitHandler: LooperQuitHandler?) {
        mMainLooperQuitHandler = mainLooperQuitHandler
    }

    private fun prepare() {
        if (Looper.myLooper() == null) {
            LooperHelper.prepare()
        }
        Looper.myQueue().addIdleHandler(MessageQueue.IdleHandler {
            if (this == runtime.loopers) {
                Log.d(LOG_TAG, "main looper queueIdle")
                if (shouldQuitLooper() && mMainLooperQuitHandler?.shouldQuit() == true) {
                    Log.d(LOG_TAG, "main looper quit")
                    Looper.myLooper()?.quitSafely()
                }
            } else {
                Log.d(LOG_TAG, "looper queueIdle $this")
                if (shouldQuitLooper()) {
                    Log.d(LOG_TAG, "looper quit $this")
                    Looper.myLooper()?.quitSafely()
                }
            }
            return@IdleHandler true
        })
    }

    fun notifyThreadExit(thread: TimerThread) {
        Log.d(LOG_TAG, "notifyThreadExit: $thread")
        // 当子线程退成时, 主线程需要检查自身是否退出 (主线程在所有子线程执行完成后才能退出, 如果主线程已经执行完任务仍然要等待所有子线程),
        // 此时通过向主线程发送一个空的 Runnable, 主线程执行完这个 Runnable 后会触发 IdleHandler, 从而检查自身是否退出
        // mHandler.post(EMPTY_RUNNABLE)
    }

    companion object {

        private const val LOG_TAG = "Loopers"
        private val EMPTY_RUNNABLE = Runnable {}

    }

}