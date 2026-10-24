package org.autojs.autojs.core.plugin.scope

import android.util.Log
import org.autojs.autojs.runtime.memory.BridgeExecutionObserver
import java.lang.ref.WeakReference
import java.util.EnumMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The bridge objects of one execution of a plugin engine other than Node.js (docs/memory-phase2/ROADMAP.md T6; a Node.js
 * execution belongs to the `RuntimeResourceScope` of its script runtime instead). The engine creates one scope per execution
 * and closes it in the `finally` of the execution, which every way out reaches: a normal end, `forceStop` (it cancels the run
 * in the plugin, so runScript returns), the end of the plugin process (the pending runScript fails with a dead object) and a
 * timeout. [close] releases everything once, stage by stage in the order of [Stage] (the order a Node.js execution ends in), and
 * within a stage in the reverse order of registration; further and concurrent calls return at once. A failing release is logged
 * and does not stop the others. Two kinds of registration:
 *
 * - [own]: a strong reference to a release action, run by [close]; [Registration.disown] drops it earlier. A registration made
 *   after its stage has been released is released at once.
 * - [track]: a weak reference only, never released by the scope; reported to [BridgeExecutionObserver] for the bridge leak gate
 *   (T1 (a)), and listed by [trackedReferences] while not reclaimed. Those still reachable when [close] ends are reported to
 *   [BridgeExecutionObserver.ended] for the memory diagnostics (P4.4).
 *
 * zh-CN: Node.js 以外的插件引擎一次执行的桥接对象 (docs/memory-phase2/ROADMAP.md T6; Node.js 的执行改由其脚本运行时的 `RuntimeResourceScope` 持有).
 * 引擎每次执行创建一个作用域, 在执行的 `finally` 中关闭它, 每种结束方式都会走到这里: 正常结束, `forceStop` (它在插件中取消运行, runScript 随之返回),
 * 插件进程结束 (在途的 runScript 因对象已死而失败) 与超时. [close] 一次性释放全部登记: 按 [Stage] 的顺序 (即 Node.js 执行结束的顺序) 逐阶段进行,
 * 同一阶段内按登记的逆序; 之后及并发的调用立即返回. 某项释放失败只记日志, 不影响其余各项. 两种登记:
 *
 * - [own]: 强引用一个释放动作, 由 [close] 执行; [Registration.disown] 可提前撤销. 在其阶段已释放之后才登记的项会立即释放.
 * - [track]: 只持弱引用, 作用域从不释放它; 报告给 [BridgeExecutionObserver] 供桥接泄漏门禁使用 (T1 (a)), 未回收前由 [trackedReferences] 列出.
 *   [close] 结束时仍可达的, 报告给 [BridgeExecutionObserver.ended] 供内存诊断使用 (P4.4).
 */
class BridgeExecutionScope @JvmOverloads constructor(
    val engine: String,
    val executionId: String,
    private val warn: (String, Throwable?) -> Unit = ::logWarning,
) {

    /**
     * Release stages in the order [close] runs them: the order a Node.js execution ends in (`NodeJsRuntimePluginBridge`:
     * the broker first, then what was handed to the plugin, then the host side of the execution).
     * zh-CN: [close] 执行各阶段的顺序: 即 Node.js 执行结束的顺序 (`NodeJsRuntimePluginBridge`: 先 broker, 再交给插件的对象, 最后是执行的宿主侧).
     */
    enum class Stage {
        /** The host capability broker: its in-flight host calls end and its exported Binder lets go of it. */
        BROKER,

        /** The callbacks handed to the plugin: closed, and their exported Binders let go of the execution. */
        CALLBACKS,

        /** The console of the execution. */
        CONSOLE,

        /** Files the execution left behind (payloads and source snapshots not unlinked yet). */
        FILES,
    }

    /**
     * One [own] registration.
     * zh-CN: 一项 [own] 登记.
     */
    inner class Registration internal constructor(val stage: Stage, val name: String, private val action: Runnable) {

        private val done = AtomicBoolean(false)

        val isDone: Boolean
            get() = done.get()

        /**
         * Drops the registration without releasing; false if it was already released or dropped.
         * zh-CN: 撤销登记而不释放; 已释放或已撤销时返回 false.
         */
        fun disown(): Boolean {
            if (!done.compareAndSet(false, true)) return false
            synchronized(lock) { pending[stage]?.remove(this) }
            return true
        }

        internal fun release(failures: MutableList<Failure>) {
            if (!done.compareAndSet(false, true)) return
            try {
                action.run()
            } catch (failure: Throwable) {
                failures += Failure(stage, name, failure)
                warn("$owner: releasing $name (${stage.name}) failed", failure)
            }
        }
    }

    /**
     * A release that threw; [close] logs it and goes on.
     * zh-CN: 抛出异常的释放; [close] 记录后继续.
     */
    class Failure(val stage: Stage, val name: String, val cause: Throwable)

    /**
     * A [track]ed object, held weakly.
     * zh-CN: [track] 登记的对象, 以弱引用持有.
     */
    class TrackedResource(val name: String, referent: Any) {
        val reference = WeakReference(referent)
        val isReclaimed: Boolean
            get() = reference.get() == null
    }

    private val owner = "$engine.execution:$executionId"
    private val lock = Any()

    // Guarded by lock.
    // zh-CN: 由 lock 保护.
    private val pending = EnumMap<Stage, MutableList<Registration>>(Stage::class.java)
    private val tracked = ArrayList<TrackedResource>()
    private var closing = false
    private var releasing: Stage? = null
    private var closed = false

    val isClosed: Boolean
        get() = synchronized(lock) { closed }

    /**
     * Registers [action] to run in [stage] when the execution ends.
     * zh-CN: 登记在执行结束时于 [stage] 执行的 [action].
     */
    fun own(stage: Stage, name: String, action: Runnable): Registration {
        val registration = Registration(stage, name, action)
        val late = synchronized(lock) {
            val passed = closed || releasing?.let { stage <= it } == true
            if (!passed) pending.getOrPut(stage) { ArrayList() } += registration
            passed
        }
        if (late) {
            warn("$owner: $name (${stage.name}) registered after its release stage, released now", null)
            registration.release(ArrayList())
        }
        return registration
    }

    /**
     * Holds [obj] weakly, and reports it to [BridgeExecutionObserver] as the [kind] object of this execution.
     * zh-CN: 以弱引用持有 [obj], 并作为本次执行的 [kind] 对象报告给 [BridgeExecutionObserver].
     */
    fun track(obj: Any, kind: String) {
        synchronized(lock) {
            tracked.removeAll { it.isReclaimed }
            tracked += TrackedResource(kind, obj)
        }
        BridgeExecutionObserver.created(engine, kind, executionId, obj)
    }

    /**
     * The [track]ed objects not reclaimed yet; the list holds them weakly, as the scope does.
     * zh-CN: 尚未被回收的 [track] 对象; 列表与作用域一样只弱引用它们.
     */
    fun trackedReferences(): List<TrackedResource> = synchronized(lock) { tracked.filterNot { it.isReclaimed } }

    /**
     * Releases every registration, stage by stage in the order of [Stage], each stage in the reverse order of registration.
     * Runs once; later and concurrent calls return an empty list at once. Returns the releases that threw (each also logged).
     * Then reports the [track]ed objects still reachable to [BridgeExecutionObserver.ended].
     * zh-CN: 按 [Stage] 的顺序逐阶段释放全部登记, 每个阶段内按登记的逆序. 只执行一次, 之后及并发的调用立即返回空列表.
     * 返回抛出异常的释放 (每项也已记日志). 之后把仍可达的 [track] 对象报告给 [BridgeExecutionObserver.ended].
     */
    fun close(): List<Failure> {
        synchronized(lock) {
            if (closing) return emptyList()
            closing = true
        }
        val failures = ArrayList<Failure>()
        for (stage in Stage.entries) {
            val batch = synchronized(lock) {
                releasing = stage
                pending.remove(stage)
            } ?: continue
            batch.asReversed().forEach { it.release(failures) }
        }
        synchronized(lock) {
            releasing = null
            closed = true
        }
        BridgeExecutionObserver.ended(engine, executionId, trackedReferences().mapNotNull { it.reference.get() })
        return failures
    }

    override fun toString(): String = "BridgeExecutionScope($owner)"

    companion object {

        private const val TAG = "BridgeExecutionScope"

        private fun logWarning(message: String, failure: Throwable?) {
            Log.w(TAG, message, failure)
        }
    }
}
