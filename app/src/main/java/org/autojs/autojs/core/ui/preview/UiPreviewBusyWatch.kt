package org.autojs.autojs.core.ui.preview

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.mozilla.javascript.Context
import java.util.concurrent.CopyOnWriteArrayList
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The busy-wait watch of interactive-preview runs (preview contract v4, 3-Block Compose roadmap P11.5). A preview run
 * executes on the main thread, so a script that never returns to it (`while (true) {}`) freezes the host and the
 * client's surface, and neither `stopScript` nor the host's task list can stop it: interrupting a thread does not stop
 * Rhino on the main thread. The watch gives each run a [Guard] keyed by its global scope; the Rhino instruction
 * observer ([observe], every 10000 instructions) finds the guard of the code running on the main thread and throws
 * [UiPreviewInterruptedException] when the guard was interrupted or when the main thread has not come back to its
 * looper for longer than the guard's limit. A heartbeat posted to the main looper measures that: while any guard
 * exists it runs every [BEAT_INTERVAL_MS] and records when it ran, so a busy main thread is one whose last beat is old.
 * Timers, event handlers and other threads that return in time never trip it.
 *
 * The exception is a [ScriptInterruptedException], which script `catch` blocks do not catch (Rhino only hands Java
 * runtime exceptions to them with `FEATURE_ENHANCED_JAVA_ACCESS`); `finally` blocks still run.
 *
 * zh-CN: 交互预览运行的忙等观察 (预览契约 v4, 3-Block Compose 路线图 P11.5). 预览运行在主线程执行, 不返回主线程的脚本 (`while (true) {}`) 会冻结宿主与客户端的表面,
 * `stopScript` 与宿主运行列表都无法停止它 (中断线程停不下主线程上的 Rhino). 观察为每次运行按其全局作用域登记一个 [Guard]; Rhino 指令观察器 ([observe],
 * 每 10000 条指令) 找到主线程上正在执行的代码所属的 guard, 在 guard 被中断或主线程超过上限未回到消息循环时抛出 [UiPreviewInterruptedException].
 * 后者由投递到主循环的心跳测量: 存在 guard 时心跳每 [BEAT_INTERVAL_MS] 运行一次并记录时间, 心跳过旧即主线程忙. 按时返回的定时器, 事件处理函数与其它线程不会触发.
 * 该异常是 [ScriptInterruptedException], 脚本的 `catch` 块接不到 (Rhino 只在 `FEATURE_ENHANCED_JAVA_ACCESS` 下把 Java 运行时异常交给它们); `finally` 块照常执行.
 */
object UiPreviewBusyWatch {

    /** How often the heartbeat runs while a run is watched; the limit is measured with this much slack at most. */
    const val BEAT_INTERVAL_MS = 200L

    /**
     * What a guard tells its run; called on the main thread from inside the run's script code, right before it unwinds.
     * [sourceName] and [line] are the position Rhino was executing (null / -1 when unknown).
     */
    fun interface Listener {
        fun onBusy(sourceName: String?, line: Int, elapsedMs: Long)
    }

    /** One watched run: its global scope, its limit (0 only honors [interrupt]) and its [Listener]. */
    class Guard internal constructor(internal val scope: Any, internal val limitMs: Long, internal val listener: Listener) {

        @Volatile
        internal var interrupted = false
            private set

        /** Makes the run's script stop at its next instruction check on the main thread; callable from any thread. */
        fun interrupt() {
            interrupted = true
        }
    }

    private val guards = CopyOnWriteArrayList<Guard>()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var beatAt = 0L

    private val beat = object : Runnable {
        override fun run() {
            beatAt = SystemClock.uptimeMillis()
            if (guards.isNotEmpty()) mainHandler.postDelayed(this, BEAT_INTERVAL_MS)
        }
    }

    /** Watches the run whose global scope is [scope]; called on the main thread before the run's script starts. */
    fun register(scope: Any, limitMs: Long, listener: Listener): Guard {
        check(Looper.myLooper() == Looper.getMainLooper()) { "A preview run is watched from the main thread" }
        val guard = Guard(scope, limitMs, listener)
        val first = guards.isEmpty()
        guards.add(guard)
        if (first) {
            mainHandler.removeCallbacks(beat)
            beat.run()
        }
        return guard
    }

    /** Stops watching [guard]; the heartbeat stops by itself with the last guard. */
    fun unregister(guard: Guard) {
        guards.remove(guard)
    }

    /** Called by the Rhino instruction observer on every thread; returns at once unless a watched run is on the main thread. */
    @JvmStatic
    fun observe(cx: Context) {
        if (guards.isEmpty()) return
        if (Looper.myLooper() !== Looper.getMainLooper()) return
        if (!RhinoScriptRuntime.hasTopCall(cx)) return
        val scope = RhinoScriptRuntime.getTopCallScope(cx)
        val guard = guards.firstOrNull { it.scope === scope } ?: return
        if (guard.interrupted) throw UiPreviewInterruptedException("the preview run was stopped")
        if (guard.limitMs <= 0) return
        val elapsed = SystemClock.uptimeMillis() - beatAt
        if (elapsed <= guard.limitMs) return
        guard.interrupt()
        val line = IntArray(1)
        val sourceName = runCatching { Context.getSourcePositionFromStack(line) }.getOrNull()
        guard.listener.onBusy(sourceName, if (line[0] > 0) line[0] else -1, elapsed)
        throw UiPreviewInterruptedException("the script kept the main thread busy for $elapsed ms")
    }
}

/** Unwinds the script of a preview run that was stopped or exceeded its busy-wait limit (see [UiPreviewBusyWatch]). */
class UiPreviewInterruptedException(message: String) : ScriptInterruptedException() {
    private val text = message
    override val message: String get() = text
}
