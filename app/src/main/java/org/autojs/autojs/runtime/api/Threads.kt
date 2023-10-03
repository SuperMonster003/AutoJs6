package org.autojs.autojs.runtime.api

import org.autojs.autojs.concurrent.VolatileDispose
import org.autojs.autojs.core.looper.Loopers
import org.autojs.autojs.core.looper.MainThreadProxy
import org.autojs.autojs.core.looper.TimerThread
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

/**
 * Created by Stardust on 2017/12/3.
 * Modified by aiselp as of Jun 10, 2023.
 * Modified by SuperMonster003 as of Aug 28, 2023.
 */
class Threads(private val mRuntime: ScriptRuntime) {

    private val mThreads = HashSet<Thread>()
    private val mMainThreadProxy = MainThreadProxy(Thread.currentThread(), mRuntime)
    private var mSpawnCount = 0
    private var mTaskCount = AtomicLong(0)
    private var mExit = false

    private val looperTask = Loopers.AsyncTask("AsyncTaskThreadPool")

    private val threadPool = Executors.newFixedThreadPool(20, ThreadFactory {
        val thread = Thread(fun() {
            Context.enter()
            try {
                it.run()
            } finally {
                Context.exit()
            }
        })
        thread.name = mainThread.name + " (AsyncThread)"
        thread
    })

    val mainThread: Thread = Thread.currentThread()

    fun currentThread(): Any {
        val thread = Thread.currentThread()
        return if (thread === mainThread) mMainThreadProxy else thread
    }

    fun runTaskForThreadPool(runnable: BaseFunction) {
        if (mTaskCount.addAndGet(1) == 1L) mRuntime.loopers.addAsyncTask(looperTask)
        threadPool.execute {
            try {
                runnable.call(
                    Context.getCurrentContext(), runnable.parentScope, runnable,
                    emptyArray()
                )
            } catch (e: Throwable) {
                if (!ScriptInterruptedException.causedByInterrupted(e)) {
                    mRuntime.console.error("$this: ", e)
                }
            } finally {
                if (mTaskCount.addAndGet(-1) == 0L) {
                    mRuntime.loopers.removeAsyncTask(looperTask)
                }
            }
        }
    }

    fun start(runnable: Runnable): TimerThread {
        val thread = createThread(runnable)
        synchronized(mThreads) {
            check(!mExit) { str(R.string.error_script_is_on_exiting) }
            thread.let {
                mThreads.add(it)
                it.name = "${mainThread.name} (Spawn-$mSpawnCount)"
                mSpawnCount++
                it.start()
            }
        }
        return thread
    }

    private fun createThread(runnable: Runnable): TimerThread {
        return object : TimerThread(mRuntime, runnable) {
            override fun onExit() {
                synchronized(mThreads) { mThreads.remove(currentThread()) }
                super.onExit()
            }
        }
    }

    fun disposable() = VolatileDispose<Any?>()

    fun atomic(value: Long) = AtomicLong(value)

    fun atomic() = AtomicLong()

    fun lock() = ReentrantLock()

    fun shutDownAll() {
        threadPool.shutdownNow()
        synchronized(mThreads) {
            mThreads.apply {
                forEach { it.interrupt() }
                clear()
            }
        }
    }

    fun exit() {
        synchronized(mThreads) {
            shutDownAll()
            mExit = true
        }
    }

    fun hasRunningThreads(): Boolean = synchronized(mThreads) { return mThreads.isNotEmpty() }

}