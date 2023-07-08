package org.autojs.autojs.runtime.api

import org.autojs.autojs.concurrent.VolatileDispose
import org.autojs.autojs.core.looper.MainThreadProxy
import org.autojs.autojs.core.looper.TimerThread
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

/**
 * Created by Stardust on 2017/12/3.
 */
class Threads(private val mRuntime: ScriptRuntime) {

    private val mThreads = HashSet<Thread>()
    private val mMainThreadProxy = MainThreadProxy(Thread.currentThread(), mRuntime)
    private var mSpawnCount = 0
    private var mExit = false

    val mainThread: Thread = Thread.currentThread()

    fun currentThread(): Any = Thread.currentThread().let { thread ->
        if (thread === mainThread) mMainThreadProxy else thread
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
        val millis = mRuntime.timers.maxCallbackUptimeMillisForAllThreads
        return object : TimerThread(mRuntime, millis, runnable) {

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