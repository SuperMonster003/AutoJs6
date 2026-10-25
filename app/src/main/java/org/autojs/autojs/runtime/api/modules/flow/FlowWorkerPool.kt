package org.autojs.autojs.runtime.api.modules.flow

import android.util.Log
import org.autojs.autojs.core.automator.flow.FlowLane
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.runtime.ScriptRuntime
import org.mozilla.javascript.Context
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The worker threads of one script's flows: a cached pool of daemon threads named
 * `flow-<engineId>-<n>` (idle ones die after [KEEP_ALIVE_SECONDS]), and serial lanes on it, one
 * per chain root ([newLane]). A lane runs its tasks one at a time, in order, on whichever pool
 * thread picks it up, inside the engine's Rhino context; different lanes run concurrently, so
 * independent chains and the sources of `race` / `all` / `any` block at the same time. A lane's
 * `interruptCurrent()` interrupts the thread running its current task only, and the interrupt
 * is cleared between tasks. [shutdown] (script exit) interrupts everything.
 * zh-CN: 一个脚本的 Flow 工作线程: 线程名 `flow-<engineId>-<n>` 的守护线程缓存池 (空闲 [KEEP_ALIVE_SECONDS] 秒后消亡), 以及其上的串行
 * lane, 每条链的根一个 ([newLane]). 一条 lane 在任一取到它的池线程上按序逐个运行任务, 处于引擎的 Rhino Context 内; 不同 lane 并发运行,
 * 因此独立的链与 `race` / `all` / `any` 的各来源同时阻塞. lane 的 `interruptCurrent()` 只中断运行其当前任务的线程, 且任务之间清除中断.
 * [shutdown] (脚本退出) 中断一切.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
internal class FlowWorkerPool(private val scriptRuntime: ScriptRuntime, val namePrefix: String) {

    /** A pool thread; `Thread.currentThread() is WorkerThread` tells worker code apart. zh-CN: 池线程; 以 `Thread.currentThread() is WorkerThread` 辨别工作线程上的代码. */
    class WorkerThread(target: Runnable, name: String) : Thread(target, name) {
        init {
            isDaemon = true
        }
    }

    private val counter = AtomicInteger()

    private val executor = ThreadPoolExecutor(0, Int.MAX_VALUE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, SynchronousQueue()) { r ->
        WorkerThread(r, "$namePrefix-${counter.incrementAndGet()}")
    }

    /** Live pool threads (busy or idle). zh-CN: 存活的池线程数 (忙碌或空闲). */
    val threadCount: Int get() = executor.poolSize

    fun newLane(): FlowLane = Lane()

    fun shutdown() {
        executor.shutdownNow()
    }

    private inner class Lane : FlowLane {

        private val queue = ConcurrentLinkedQueue<Runnable>()

        private val lock = Any()

        /** Whether a pool thread is (about to be) draining [queue]; guarded by [lock]. zh-CN: 是否已有池线程正在 (或即将) 排空 [queue]; 受 [lock] 保护. */
        private var draining = false

        @Volatile
        private var runningThread: Thread? = null

        override fun post(task: Runnable) {
            queue.add(task)
            val start = synchronized(lock) {
                (!draining).also { if (it) draining = true }
            }
            if (start) {
                try {
                    executor.execute(::drain)
                } catch (e: RejectedExecutionException) {
                    // The pool is shut down (the script is exiting): the tasks are dropped.
                    // zh-CN: 线程池已关闭 (脚本正在退出): 任务被丢弃.
                    synchronized(lock) { draining = false }
                    queue.clear()
                    Log.w(TAG, "Dropped a flow task after shutdown: ${e.message}")
                }
            }
        }

        override fun interruptCurrent() {
            runningThread?.interrupt()
        }

        private fun drain() {
            (scriptRuntime.engines.myEngine() as? RhinoJavaScriptEngine)?.enterContext() ?: Context.enter()
            try {
                while (true) {
                    val task = queue.poll() ?: synchronized(lock) {
                        // Under the lock, so a post() that just enqueued either sees draining or is seen here.
                        // zh-CN: 在锁内, 使刚入队的 post() 要么看到 draining, 要么在此被看到.
                        if (queue.isEmpty()) {
                            draining = false
                            return
                        }
                        null
                    } ?: continue
                    runningThread = Thread.currentThread()
                    try {
                        task.run()
                    } catch (e: Throwable) {
                        Log.w(TAG, "A flow task failed", e)
                    } finally {
                        runningThread = null
                        Thread.interrupted()
                    }
                }
            } finally {
                Context.exit()
            }
        }

    }

    companion object {
        private const val TAG = "FlowWorkerPool"
        private const val KEEP_ALIVE_SECONDS = 10L
    }

}
