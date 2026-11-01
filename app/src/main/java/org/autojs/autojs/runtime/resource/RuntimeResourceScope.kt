package org.autojs.autojs.runtime.resource

import android.util.Log
import java.io.Closeable
import java.lang.ref.WeakReference
import java.util.EnumMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The resources of one script runtime (docs/memory/ROADMAP.md T6 / P4.1). Modules register what they create, and
 * [close] (run by `ScriptRuntime.onExit`) releases everything once, stage by stage in the fixed order of [Stage]
 * (appendix B.3 of the roadmap), and within a stage in the reverse order of registration. A failing release is logged
 * and does not stop the others. Two kinds of registration:
 *
 * - [own]: a strong reference to a release action, run by [close]; [Registration.disown] drops it earlier (for example
 *   when the script closes the resource itself). A registration made after its stage has been released is released at
 *   once, so nothing registered by a late thread outlives the runtime unnoticed.
 * - [track]: a weak reference only, never released by the scope; once the script has ended, the memory diagnostics
 *   check whether these objects were reclaimed and report those that were not ([trackedReferences]).
 *
 * The former `CloseableManager` of the runtime is the [Stage.CLOSEABLES] stage ([add] / [remove]).
 *
 * zh-CN: 一个脚本运行时的资源 (docs/memory/ROADMAP.md T6 / P4.1). 各模块登记自己创建的资源, [close] (由 `ScriptRuntime.onExit` 调用)
 * 一次性释放全部资源: 按 [Stage] 的固定顺序 (路线图附录 B.3) 逐阶段进行, 同一阶段内按登记的逆序. 某项释放失败只记日志, 不影响其余各项. 两种登记:
 *
 * - [own]: 强引用一个释放动作, 由 [close] 执行; [Registration.disown] 可提前撤销 (例如脚本自己关闭了该资源). 在其阶段已释放之后才登记的项会立即释放,
 *   因此迟到的线程登记的资源不会悄悄比运行时活得更久.
 * - [track]: 只持弱引用, 作用域从不释放它; 脚本结束后由内存诊断复查这些对象是否已被回收, 并报告未回收的 ([trackedReferences]).
 *
 * 运行时原有的 `CloseableManager` 即 [Stage.CLOSEABLES] 阶段 ([add] / [remove]).
 */
class RuntimeResourceScope @JvmOverloads constructor(
    private val owner: String,
    private val warn: (String, Throwable?) -> Unit = ::logWarning,
) {

    /**
     * Release stages in the order [close] runs them; the order of `ScriptRuntime.onExit` before P4.1 (roadmap appendix B.3).
     * zh-CN: [close] 执行各阶段的顺序; 即 P4.1 之前 `ScriptRuntime.onExit` 的顺序 (路线图附录 B.3).
     */
    enum class Stage {
        /** `images.cancelPendingQuantizations`. */
        IMAGE_QUANTIZATIONS,

        /** `console.onRuntimeExit` (and hiding the console when it is set to exit on close). */
        CONSOLE,

        /** The WebSocket connections of the runtime (`core/web/WebSocket.onExit`, H19). */
        WEBSOCKETS,

        /** Accessibility event callbacks registered by this script (`automator.removeAllEventsForThisRuntime`). */
        ACCESSIBILITY_EVENTS,

        /** The event subscription of event-driven waits (`accessibilityBridge.releasePulse`). */
        ACCESSIBILITY_PULSE,

        /** `auto.stateListener` (`accessibilityBridge.setAccessibilityListener(null)`). */
        ACCESSIBILITY_LISTENER,

        /** `RootUtils.resetRuntimeOverriddenRootModeState`. */
        ROOT_MODE,

        /** `compose.close`. */
        COMPOSE,

        /** Clears the interrupt status of the exiting thread, so that the stages after it can wait. */
        INTERRUPT_STATUS,

        /** `floaty.closeAll`: floating windows close first, before any script code runs again in `exit` handlers. */
        FLOATY,

        /** `events.emit("exit")`. */
        EXIT_EVENT,

        /**
         * WebViews of `web.newInjectableWebView` not attached to a window: destroyed on their thread after the `exit` handlers,
         * which may still use them (appendix C Q42, P4.4).
         */
        WEB_VIEWS,

        MAIL,
        INSTALLER,
        EPUB,
        GALLERY,
        TERMINAL,
        SPEECH,
        AI,
        TTS,

        /** `threads.shutDownAllAndAwait`; leaves the threads still alive in [survivingThreads] (H20 / H15). */
        THREADS,

        /** `ImageWrapper.recycleAll(runtime)`, after the threads of [THREADS] have ended (H20). */
        IMAGES,

        /** `plugins.unbindAll` (H8). */
        PLUGINS,

        /** `events.recycle`. */
        EVENTS,

        /** `media.recycle`. */
        MEDIA,

        /** `loopers.recycle`. */
        LOOPERS,

        /** `accessibilityBridge.tap = null` (H17). */
        ACCESSIBILITY_TAP,

        /** What the modules left on the accessibility buses for this runtime, by owner id (`AccessibilityService.releaseOwner`, P4.2). */
        ACCESSIBILITY_OWNER,

        /** The root shell of the runtime. */
        SHELL,

        /** `images.releaseScreenCapturer`. */
        SCREEN_CAPTURER,

        /** `images.stopScreenCapturerForegroundService`. */
        SCREEN_CAPTURE_SERVICE,

        /** `sensors.unregisterAll`. */
        SENSORS,

        /**
         * `device.cancelKeepingAwake`: the wake lock that `device.keepAwake` / `keepScreenOn` / `keepScreenDim` hold without a
         * timeout, after the `exit` handlers so that they may still use `device`.
         * zh-CN: `device.cancelKeepingAwake`: `device.keepAwake` / `keepScreenOn` / `keepScreenDim` 不带超时时持有的唤醒锁, 放在 `exit`
         * 处理器之后, 处理器里仍可使用 `device`.
         */
        DEVICE,

        /** `timers.recycle`. */
        TIMERS,

        /** `ui.recycle`. */
        UI,

        /** Closeables added while the script runs (the former `CloseableManager`): databases, shell processes, sessions. */
        CLOSEABLES,

        /** The loaders of `runtime.loadDex` / `loadJar` / `loadAar` this script registered (`AndroidClassLoader.releaseOwner`, H3 / P4.4). */
        CLASS_LOADERS,
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
            synchronized(lock) { forget(this) }
            return true
        }

        /**
         * Releases now rather than in [close], for a resource whose owner ends before the runtime (one Node.js execution
         * of the script, docs/memory-phase2/ROADMAP.md T6): the action runs once, on the calling thread, and what it throws
         * reaches the caller. False if it was already released or dropped.
         * zh-CN: 立即释放而不等 [close], 用于其所有者先于运行时结束的资源 (脚本的一次 Node.js 执行, docs/memory-phase2/ROADMAP.md T6):
         * 动作只执行一次, 在调用线程上执行, 其抛出的异常交给调用方. 已释放或已撤销时返回 false.
         */
        fun releaseNow(): Boolean {
            if (!done.compareAndSet(false, true)) return false
            synchronized(lock) { forget(this) }
            action.run()
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

    private val lock = Any()

    // Guarded by lock.
    // zh-CN: 由 lock 保护.
    private val pending = EnumMap<Stage, MutableList<Registration>>(Stage::class.java)
    private val keyed = HashMap<String, Registration>()
    private val closeables = HashMap<Closeable, Registration>()
    private val tracked = ArrayList<TrackedResource>()
    private var trackedPruneAt = TRACKED_PRUNE_MIN
    private var closing = false
    private var releasing: Stage? = null
    private var closed = false

    /**
     * Threads of the script still alive after [Stage.THREADS]: resources they may still use are released only after
     * they have ended (H20, R2). Set by the threads module during [close].
     * zh-CN: [Stage.THREADS] 之后仍存活的脚本线程: 它们可能仍在使用的资源要等它们结束后才释放 (H20, R2). 由 threads 模块在 [close] 中设置.
     */
    @Volatile
    var survivingThreads: List<Thread> = emptyList()

    val isClosed: Boolean
        get() = synchronized(lock) { closed }

    /**
     * Registers [action] to run in [stage] when the runtime exits.
     * zh-CN: 登记在运行时结束时于 [stage] 执行的 [action].
     */
    fun own(stage: Stage, name: String, action: Runnable): Registration {
        val registration = Registration(stage, name, action)
        enqueue(registration)
        return registration
    }

    /**
     * Like [own], but at most once per [stage] and [name]: later calls return the first registration while it is pending.
     * For modules that release all their resources of one kind at once, registered when the first one is created.
     * zh-CN: 与 [own] 相同, 但同一 [stage] 与 [name] 最多登记一次: 第一次的登记仍待释放时, 之后的调用返回它. 用于一次释放同类全部资源的模块,
     * 在创建第一个资源时登记.
     */
    fun ownOnce(stage: Stage, name: String, action: Runnable): Registration {
        val key = "${stage.name}:$name"
        val registration = synchronized(lock) {
            keyed[key]?.takeUnless { it.isDone }?.let { return it }
            Registration(stage, name, action).also { keyed[key] = it }
        }
        enqueue(registration)
        return registration
    }

    /**
     * Adds a closeable to [Stage.CLOSEABLES] (the former `CloseableManager.add`); adding the same closeable again keeps one registration.
     * zh-CN: 把 closeable 加入 [Stage.CLOSEABLES] (原 `CloseableManager.add`); 重复加入同一个 closeable 只保留一项登记.
     */
    fun add(closeable: Closeable) {
        val registration = synchronized(lock) {
            closeables[closeable]?.takeUnless { it.isDone }?.let { return }
            Registration(Stage.CLOSEABLES, closeable.javaClass.name, Runnable { closeable.close() }).also { closeables[closeable] = it }
        }
        enqueue(registration)
    }

    /**
     * Drops a closeable added by [add] without closing it (the former `CloseableManager.remove`).
     * zh-CN: 撤销由 [add] 加入的 closeable, 不关闭它 (原 `CloseableManager.remove`).
     */
    fun remove(closeable: Closeable) {
        synchronized(lock) { closeables[closeable] }?.disown()
    }

    /**
     * Holds [obj] weakly so that the memory diagnostics can tell whether it was reclaimed after the script ended.
     * zh-CN: 以弱引用持有 [obj], 供内存诊断在脚本结束后判断它是否已被回收.
     */
    fun track(obj: Any, name: String) {
        synchronized(lock) {
            tracked += TrackedResource(name, obj)
            if (tracked.size >= trackedPruneAt) {
                tracked.removeAll { it.isReclaimed }
                trackedPruneAt = maxOf(TRACKED_PRUNE_MIN, tracked.size * 2)
            }
        }
    }

    /**
     * The [track]ed objects not reclaimed yet; the list holds them weakly, as the scope does.
     * zh-CN: 尚未被回收的 [track] 对象; 列表与作用域一样只弱引用它们.
     */
    fun trackedReferences(): List<TrackedResource> = synchronized(lock) { tracked.filterNot { it.isReclaimed } }

    /**
     * Releases every registration, stage by stage in the order of [Stage], each stage in the reverse order of registration.
     * Runs once; later calls return an empty list. Returns the releases that threw (each also logged).
     * zh-CN: 按 [Stage] 的顺序逐阶段释放全部登记, 每个阶段内按登记的逆序. 只执行一次, 之后的调用返回空列表. 返回抛出异常的释放 (每项也已记日志).
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
            keyed.clear()
            closeables.clear()
        }
        return failures
    }

    private fun enqueue(registration: Registration) {
        val late = synchronized(lock) {
            val passed = closed || releasing?.let { registration.stage <= it } == true
            if (!passed) pending.getOrPut(registration.stage) { ArrayList() } += registration
            passed
        }
        if (late) {
            warn("$owner: ${registration.name} (${registration.stage.name}) registered after its release stage, released now", null)
            registration.release(ArrayList())
        }
    }

    // Caller holds lock.
    // zh-CN: 调用方持有 lock.
    private fun forget(registration: Registration) {
        pending[registration.stage]?.remove(registration)
        keyed.values.remove(registration)
        closeables.values.remove(registration)
    }

    companion object {

        private const val TAG = "RuntimeResourceScope"
        private const val TRACKED_PRUNE_MIN = 64

        private fun logWarning(message: String, failure: Throwable?) {
            Log.w(TAG, message, failure)
        }
    }
}
