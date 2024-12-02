package org.autojs.autojs.runtime.api

import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.concurrent.VolatileDispose
import org.autojs.autojs.core.looper.MainThreadProxy
import org.autojs.autojs.core.looper.Timer
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import org.autojs.autojs.core.looper.TimerThread as CoreTimerThread

/**
 * Created by Stardust on Dec 3, 2017.
 * Modified by SuperMonster003 as of Aug 28, 2023.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Dec 19, 2023.
class Threads(private val scriptRuntime: ScriptRuntime) {

    val mainThread: Thread = Thread.currentThread()

    private val mThreads: MutableSet<TimerThread> = Collections.newSetFromMap(WeakHashMap())
    private val mMainThreadProxy = MainThreadProxy(Thread.currentThread(), scriptRuntime)
    private var mSpawnCount = 0
    private var mExit = false

    val threadPools = HashSet<ThreadPoolExecutor>()

    fun getThreads() = mThreads

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

    @Suppress("unused")
    @JvmName("_pool")
    @ScriptInterface
    fun internalPool(corePoolSize: Int, maximumPoolSize: Int, keepAliveTime: Long): ThreadPoolExecutor {
        require(corePoolSize >= 0) { "Property corePoolSize for threads.pool should be a non-negative integer" }
        require(maximumPoolSize >= 0) { "Property maximumPoolSize for threads.pool should be a non-negative integer" }
        require(keepAliveTime >= 0L) { "Property keepAliveTime for threads.pool should be a non-negative integer" }
        var niceMaximumPoolSize = maximumPoolSize
        if (niceMaximumPoolSize == 0) {
            niceMaximumPoolSize = Int.MAX_VALUE
        }
        require(corePoolSize <= maximumPoolSize) { "Property corePoolSize ($corePoolSize) should not be greater than maximumPoolSize ($maximumPoolSize)" }
        synchronized(threadPools) {
            return ThreadPool(corePoolSize, niceMaximumPoolSize, keepAliveTime, threadPools.size, this).also {
                threadPools.add(it)
            }
        }
    }

    fun allThreads() = emptyList<Thread>() + mainThread + mThreads

    private fun createThread(runnable: Runnable): TimerThread {
        return TimerThread(scriptRuntime, runnable, mThreads)
    }

    @ScriptInterface
    fun currentThread(): Any {
        return when (val currentThread = Thread.currentThread()) {
            mainThread -> mMainThreadProxy
            else -> currentThread
        }
    }

    fun disposable() = VolatileDispose<Any?>()

    fun atomic(value: Long) = AtomicLong(value)

    fun atomic() = AtomicLong()

    fun lock() = ReentrantLock()

    fun shutDownAll() {
        synchronized(mThreads) {
            mThreads.forEach { it.interrupt() }
            mThreads.clear()
        }
    }

    fun exit() {
        synchronized(mThreads) {
            shutDownAll()
            mExit = true
        }
        var threadPoolsCopied: Array<ThreadPoolExecutor>
        synchronized(threadPools) {
            threadPoolsCopied = threadPools.toTypedArray()
            threadPools.clear()
        }
        threadPoolsCopied.forEach { it.shutdownNow() }
    }

    fun hasRunningThreads(): Boolean {
        synchronized(mThreads) {
            if (mThreads.isNotEmpty()) {
                return true
            }
        }
        synchronized(threadPools) {
            val iterator: Iterator<ThreadPoolExecutor> = threadPools.iterator()
            do if (!iterator.hasNext()) return false
            while (iterator.next().let { it.activeCount + it.queue.size } <= 0)
            return true
        }
    }

    companion object {

        class TimerThread(scriptRuntime: ScriptRuntime, runnable: Runnable, set: MutableSet<TimerThread>) : CoreTimerThread(scriptRuntime, runnable) {

            private val mThreads = WeakReference(set)

            public override fun onExit() {
                super.onExit()
                val set = mThreads.get()
                if (set != null) {
                    synchronized(set) { set.remove(this) }
                }
            }

        }

        class ThreadPool(
            corePoolSize: Int,
            maximumPoolSize: Int,
            keepAliveTime: Long,
            threadPoolId: Int,
            threads: Threads,
        ) : ThreadPoolExecutor(
            corePoolSize,
            maximumPoolSize,
            keepAliveTime,
            TimeUnit.MILLISECONDS,
            LinkedBlockingQueue(),
            TimerThreadFactory(threadPoolId, WeakReference<Threads>(threads))
        ) {

            private val mThreads = WeakReference(threads)

            private fun removeFromPools() {
                val threads = mThreads.get() ?: return
                synchronized(threads.threadPools) {
                    threads.threadPools.remove(this)
                }
            }

            public override fun afterExecute(r: Runnable, t: Throwable?) {
                super.afterExecute(r, t)
                val threads = mThreads.get() ?: return
                threads.scriptRuntime.timers.mainTimer.postDelayed(Timer.EMPTY_RUNNABLE, 0L)
            }

            override fun shutdown() {
                super.shutdown()
                removeFromPools()
            }

            override fun shutdownNow(): List<Runnable> {
                val shutdownNow = super.shutdownNow()
                removeFromPools()
                return shutdownNow
            }
        }

        class TimerThreadFactory(private val threadPoolId: Int, private val threads: WeakReference<Threads>) : ThreadFactory {

            private val mThreadCount = AtomicInteger(0)

            override fun newThread(runnable: Runnable): Thread {
                val threads = threads.get() ?: throw IllegalStateException("create thread when runtime is dispose by pool $this")
                return TimerThread(threads.scriptRuntime, runnable, threads.getThreads()).apply {
                    name = "${threads.mainThread.name} (Pool-$threadPoolId, ${mThreadCount.getAndIncrement()})"
                }
            }

        }

    }

}