package org.autojs.autojs.runtime.api.modules.flow

import android.util.Log
import org.autojs.autojs.core.automator.flow.FlowLane
import org.autojs.autojs.core.looper.Timer
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.mozilla.javascript.BaseFunction

/**
 * The script lane of one script thread: callbacks are queued and drained through the thread's
 * [Timer] (`setImmediate`), so they run on that thread between its other events. The owning
 * thread may also drain the queue itself while it blocks in `sync()` ([runPending] +
 * [awaitSignal]); without that, a chain whose callbacks belong to the very thread waiting for
 * it could never settle.
 * zh-CN: 一个脚本线程的脚本 lane: 回调排队并经该线程的 [Timer] (`setImmediate`) 排空, 因此在该线程的其它事件之间运行. 拥有线程在
 * `sync()` 中阻塞时也可自行排空队列 ([runPending] + [awaitSignal]); 否则回调属于等待线程自身的链永远无法结算.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
internal class FlowScriptLane(private val scriptRuntime: ScriptRuntime, private val timer: Timer) : FlowLane {

    // Object exposes the JVM monitor's wait/notifyAll methods used below.
    // zh-CN: Object 提供下方使用的 JVM 监视器 wait/notifyAll 方法.
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val lock = Object()

    /** Guarded by [lock]. zh-CN: 受 [lock] 保护. */
    private val queue = ArrayDeque<Runnable>()

    /** Whether a `setImmediate` drain is on its way; guarded by [lock]. zh-CN: 是否已有 `setImmediate` 排空在路上; 受 [lock] 保护. */
    private var scheduled = false

    /** Bumped on every post and every wake-up, so a waiter can tell a missed signal; guarded by [lock]. zh-CN: 每次投递与唤醒递增, 使等待者能察觉错过的信号; 受 [lock] 保护. */
    private var signals = 0L

    @Volatile
    private var closed = false

    private val drain: BaseFunction = newBaseFunction(scriptRuntime.topLevelScope, "__flow_dispatch__", { runPending(); UNDEFINED }, NOT_CONSTRUCTABLE)

    override fun post(task: Runnable) {
        val schedule = synchronized(lock) {
            if (closed) return
            queue.addLast(task)
            signals += 1
            lock.notifyAll()
            (!scheduled).also { if (it) scheduled = true }
        }
        if (schedule) {
            try {
                timer.setImmediate(drain)
            } catch (e: Throwable) {
                // The looper is gone (the script is exiting): nothing will ever drain the queue.
                // zh-CN: Looper 已退出 (脚本正在退出): 队列不会再被排空.
                synchronized(lock) {
                    scheduled = false
                    queue.clear()
                }
                Log.w(TAG, "Dropped a flow callback: ${e.message}")
            }
        }
    }

    override fun interruptCurrent() = Unit

    /** Runs every queued task now, on the calling thread (the owning thread); returns how many ran. zh-CN: 立即在调用线程 (拥有线程) 上运行全部排队任务; 返回运行的个数. */
    fun runPending(): Int {
        var count = 0
        while (true) {
            val task = synchronized(lock) {
                queue.removeFirstOrNull().also { if (it == null) scheduled = false }
            } ?: return count
            count += 1
            try {
                task.run()
            } catch (e: Throwable) {
                Log.w(TAG, "A flow callback failed", e)
                scriptRuntime.console.error("Flow callback failed: $e")
            }
        }
    }

    /** The current signal count, to pass to [awaitSignal]. zh-CN: 当前信号计数, 传给 [awaitSignal]. */
    val signal: Long get() = synchronized(lock) { signals }

    /** Wakes a thread blocked in [awaitSignal] (a node settled). zh-CN: 唤醒阻塞在 [awaitSignal] 的线程 (有节点结算). */
    fun wakeUp() = synchronized(lock) {
        signals += 1
        lock.notifyAll()
    }

    /**
     * Blocks up to [timeoutMillis] until a task is posted or [wakeUp] is called after [seen]
     * was read; returns at once when that already happened.
     * zh-CN: 最多阻塞 [timeoutMillis], 直到读取 [seen] 之后有任务投递或 [wakeUp] 被调用; 若已发生则立即返回.
     */
    @Throws(InterruptedException::class)
    fun awaitSignal(seen: Long, timeoutMillis: Long) = synchronized(lock) {
        if (signals == seen && queue.isEmpty() && timeoutMillis > 0) lock.wait(timeoutMillis)
    }

    fun close() {
        synchronized(lock) {
            closed = true
            queue.clear()
            lock.notifyAll()
        }
    }

    companion object {
        private const val TAG = "FlowScriptLane"
    }

}
