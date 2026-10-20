package org.autojs.autojs.runtime.api

import android.os.SystemClock
import android.util.Log
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

    // Worker threads of every pool created by threads.pool (including pools the script has shut down),
    // tracked weakly so that an exiting runtime can wait for them (docs/memory/ROADMAP.md H20).
    // zh-CN: threads.pool 创建的全部线程池 (含脚本已关闭的) 的工作线程, 以弱引用记录, 便于退出中的运行时等待它们结束 (docs/memory/ROADMAP.md H20).
    private val mPoolThreads: MutableSet<Thread> = Collections.newSetFromMap(WeakHashMap())

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

    /**
     * Interrupts every script thread, shuts down every thread pool the script has not shut down itself,
     * and waits up to [timeoutMillis] for all these threads (and the workers of pools the script has shut down) to terminate.
     * Resources shared with these threads, such as images, may only be released after they have terminated
     * (docs/memory/ROADMAP.md H20 / H15). Returns the threads which are still alive when the wait ends.
     * zh-CN: 中断全部脚本线程, 关闭脚本自己未关闭的线程池, 并最多等待 [timeoutMillis] 毫秒让这些线程 (以及脚本已关闭的线程池的工作线程) 结束.
     * 与这些线程共用的资源 (例如图像) 只能在它们结束后释放 (docs/memory/ROADMAP.md H20 / H15). 返回等待结束时仍存活的线程.
     */
    fun shutDownAllAndAwait(timeoutMillis: Long): List<Thread> {
        val spawned: List<Thread> = synchronized(mThreads) { mThreads.toList() }
        shutDownAll()
        val pools = synchronized(threadPools) { threadPools.toList() }
        pools.forEach { it.shutdownNow() }
        val pooled = synchronized(mPoolThreads) { mPoolThreads.toList() }
        return awaitTermination(spawned + pooled, timeoutMillis)
    }

    private fun addPoolThread(thread: Thread) {
        synchronized(mPoolThreads) { mPoolThreads.add(thread) }
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
                // Leave the set before waking the main looper: woken earlier, it could still see this thread as running,
                // go idle again and never end the script (a run of gate S4e N on AVD API 24 hung until its timeout).
                // zh-CN: 先离开线程集合再唤醒主 Looper: 若先唤醒, 主线程可能仍看到本线程在运行, 再次空闲后脚本就不会结束 (门禁 S4e N 在 AVD API 24 上曾有一次挂起至超时).
                val set = mThreads.get()
                if (set != null) {
                    synchronized(set) { set.remove(this) }
                }
                super.onExit()
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
                    threads.addPoolThread(this)
                }
            }

        }

        private const val TAG = "Threads"

        /**
         * Waits until every thread in [threads] (except the current one) has terminated or [timeoutMillis] has elapsed,
         * and returns the threads which are still alive. An interrupt during the wait does not end it early;
         * the interrupt status is restored afterwards.
         * zh-CN: 等待 [threads] 中的线程 (当前线程除外) 全部结束或超过 [timeoutMillis] 毫秒, 返回仍存活的线程.
         * 等待期间收到中断不会提前结束等待, 结束后恢复中断状态.
         */
        @JvmStatic
        fun awaitTermination(threads: Collection<Thread>, timeoutMillis: Long): List<Thread> {
            val current = Thread.currentThread()
            val others = threads.filter { it !== current }
            val deadline = SystemClock.uptimeMillis() + timeoutMillis
            var interrupted = false
            for (thread in others) {
                while (thread.isAlive) {
                    val remaining = deadline - SystemClock.uptimeMillis()
                    if (remaining <= 0L) break
                    try {
                        thread.join(remaining)
                    } catch (_: InterruptedException) {
                        interrupted = true
                    }
                }
            }
            if (interrupted) current.interrupt()
            return others.filter { it.isAlive }
        }

        /**
         * Runs [action] on a helper thread once every thread in [threads] has terminated.
         * If some of them are still alive after [timeoutMillis], [action] is dropped with a warning,
         * because releasing resources still in use by a live thread may crash the process (docs/memory/ROADMAP.md R2).
         * zh-CN: 在 [threads] 中的线程全部结束后, 于辅助线程执行 [action].
         * 若超过 [timeoutMillis] 毫秒后仍有线程存活, 则放弃 [action] 并记录警告, 因为释放仍被存活线程使用的资源可能使进程崩溃 (docs/memory/ROADMAP.md R2).
         */
        @JvmStatic
        fun runAfterTermination(name: String, threads: List<Thread>, timeoutMillis: Long, action: Runnable) {
            Thread({
                val alive = awaitTermination(threads, timeoutMillis)
                if (alive.isEmpty()) {
                    action.run()
                } else {
                    Log.w(TAG, "$name: skipped after ${timeoutMillis}ms because ${alive.size} thread(s) are still alive: $alive")
                }
            }, "$name (Exit-Wait)").apply { isDaemon = true }.start()
        }

    }

}