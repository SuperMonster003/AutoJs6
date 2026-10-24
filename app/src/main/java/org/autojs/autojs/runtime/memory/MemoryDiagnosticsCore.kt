package org.autojs.autojs.runtime.memory

import org.autojs.autojs.runtime.resource.RuntimeResourceScope.TrackedResource
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

/**
 * The readings [MemoryDiagnosticsCore] takes from the process; [MemoryDiagnostics] reads Android and the host, tests use fakes.
 * zh-CN: [MemoryDiagnosticsCore] 从进程读取的数值; [MemoryDiagnostics] 读取 Android 与宿主, 测试使用替身.
 */
interface MemoryProbes {
    fun javaHeapUsed(): Long
    fun javaHeapTotal(): Long
    fun javaHeapMax(): Long
    fun nativeHeapAllocated(): Long
    fun nativeHeapSize(): Long
    fun pss(): Long
    fun engines(): Int
    fun images(): Int
    fun contexts(): Int
    fun liveThreads(): List<Thread>
    fun gc()
    fun runFinalization()
}

/**
 * The parts of [MemoryProbes] that plain Java provides: heap, threads and garbage collection.
 * zh-CN: [MemoryProbes] 中由 Java 本身提供的部分: 堆, 线程与垃圾回收.
 */
abstract class JvmMemoryProbes : MemoryProbes {

    override fun javaHeapUsed() = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

    override fun javaHeapTotal() = Runtime.getRuntime().totalMemory()

    override fun javaHeapMax() = Runtime.getRuntime().maxMemory()

    override fun liveThreads(): List<Thread> {
        var group = Thread.currentThread().threadGroup
        while (group?.parent != null) group = group.parent
        val root = group ?: return emptyList()
        var buffer = arrayOfNulls<Thread>(root.activeCount() * 2 + 16)
        var count = root.enumerate(buffer, true)
        while (count == buffer.size) {
            buffer = arrayOfNulls(buffer.size * 2)
            count = root.enumerate(buffer, true)
        }
        return buffer.take(count).filterNotNull()
    }

    override fun gc() = Runtime.getRuntime().gc()

    @Suppress("DEPRECATION")
    override fun runFinalization() = System.runFinalization()
}

/**
 * Clocks of [MemoryDiagnosticsCore], replaceable in tests.
 * zh-CN: [MemoryDiagnosticsCore] 使用的时钟, 测试中可替换.
 */
interface MemoryClock {

    /** Wall clock time in milliseconds since the epoch. zh-CN: 自纪元起的毫秒数. */
    fun currentTimeMillis(): Long

    /** Monotonic time in milliseconds, for durations. zh-CN: 单调时钟毫秒数, 用于计算时长. */
    fun elapsedRealtime(): Long

    fun sleep(ms: Long)
}

/**
 * The memory diagnostics without Android (docs/memory/ROADMAP.md P2.1): snapshots, forced collection, and the registries of
 * finished script runtimes and of finished plugin engine executions (bridge sessions, docs/memory-phase2/ROADMAP.md P4.4). Both are
 * held by weak references only and leave their registry once the GC reclaims them, so the registries themselves never keep a
 * script alive. [MemoryDiagnostics] is the process-wide instance; the memory gate (T3), the developer options page (T10) and the
 * script API (T9) all read it.
 *
 * zh-CN: 不依赖 Android 的内存诊断 (docs/memory/ROADMAP.md P2.1): 快照, 强制回收, 以及已结束脚本 runtime 与已结束插件引擎执行 (桥接会话,
 * docs/memory-phase2/ROADMAP.md P4.4) 的登记表. 两者都只以弱引用登记, 被 GC 回收后自动出列, 因此登记表本身不会让脚本存活.
 * [MemoryDiagnostics] 是进程级实例; 内存门禁 (T3), 开发者选项页面 (T10) 与脚本 API (T9) 都从它读取.
 */
open class MemoryDiagnosticsCore internal constructor(
    private val probes: MemoryProbes,
    private val clock: MemoryClock,
) {

    private class Ended(
        runtime: Any,
        queue: ReferenceQueue<Any>,
        val id: Int,
        val name: String,
        val source: String,
        val endedAt: Long,
        val endedElapsed: Long,
        val exitKind: ExitKind,
        val engine: String,
        var checked: Boolean,
    ) : WeakReference<Any>(runtime, queue) {
        fun describe(now: Long) = RetainedRuntime(id, name, source, endedAt, now - endedElapsed, exitKind, engine)
    }

    // A finished plugin engine execution (P4.4): its session objects, held weakly; it leaves the registry once all are reclaimed.
    // zh-CN: 已结束的插件引擎执行 (P4.4): 其会话对象, 以弱引用持有; 全部被回收后出列.
    private class BridgeSession(
        val engine: String,
        val executionId: String,
        val endedAt: Long,
        val endedElapsed: Long,
        val objects: List<BridgeObject>,
    ) {
        fun alive() = objects.filter { it.get() != null }
        fun describe(now: Long) = RetainedBridgeSession(engine, executionId, endedAt, now - endedElapsed, alive().map { it.className })
    }

    private class BridgeObject(referent: Any) : WeakReference<Any>(referent) {
        val className: String = referent.javaClass.name.substringAfterLast('.')
    }

    // The resources a finished runtime tracked in its resource scope (roadmap P4.1), checked with the runtime but kept apart from
    // it: a runtime can be reclaimed while one of its threads or windows is still alive.
    // zh-CN: 已结束 runtime 在其资源作用域中跟踪的资源 (ROADMAP P4.1), 与 runtime 一同检查但分开存放: runtime 可能已被回收而它的某个线程或窗口仍存活.
    private class Resources(
        val id: Int,
        val name: String,
        val source: String,
        val endedElapsed: Long,
        val resources: List<TrackedResource>,
    ) {
        fun alive() = resources.filterNot { it.isReclaimed }
    }

    private val lock = Any()
    private val queue = ReferenceQueue<Any>()

    // Guarded by lock; in the order the scripts ended.
    // zh-CN: 由 lock 保护; 按脚本结束的先后排列.
    private val ended = LinkedHashSet<Ended>()

    // Guarded by lock; in the order the executions ended.
    // zh-CN: 由 lock 保护; 按执行结束的先后排列.
    private val bridgeSessions = ArrayList<BridgeSession>()

    // Guarded by lock: resources waiting for the unreclaimed check, and the latest findings (at most MAX_UNRELEASED).
    // zh-CN: 由 lock 保护: 等待未回收检查的资源, 以及最近的检查结果 (最多 MAX_UNRELEASED 条).
    private val pendingResources = ArrayList<Resources>()
    private val unreleased = ArrayDeque<UnreleasedResource>()

    /**
     * Registers a finished script runtime by a weak reference. With [check] false the runtime is never reported by [checkEnded].
     * [resources] are the objects the runtime tracked in its resource scope (held weakly); [checkEnded] reports those still alive.
     * [engine] is [ENGINE_NODE] for the runtime of a Node.js script.
     * zh-CN: 以弱引用登记已结束的脚本 runtime. [check] 为 false 时 [checkEnded] 不会报告它. [resources] 是该 runtime 在资源作用域中跟踪的对象
     * (弱引用), [checkEnded] 报告其中仍存活的. Node.js 脚本的 runtime 其 [engine] 为 [ENGINE_NODE].
     */
    fun track(
        runtime: Any,
        id: Int,
        name: String,
        source: String,
        exitKind: ExitKind,
        check: Boolean = true,
        resources: List<TrackedResource> = emptyList(),
        engine: String = ENGINE_RHINO,
    ) {
        synchronized(lock) {
            prune()
            val endedElapsed = clock.elapsedRealtime()
            ended.add(Ended(runtime, queue, id, name, source, clock.currentTimeMillis(), endedElapsed, exitKind, engine, !check))
            if (check && resources.isNotEmpty()) pendingResources += Resources(id, name, source, endedElapsed, resources)
        }
    }

    /**
     * Registers a finished plugin engine execution of [engine] by weak references to its session [objects] (P4.4,
     * [BridgeExecutionObserver.ended]); an execution without objects is not registered.
     * zh-CN: 以会话对象 [objects] 的弱引用登记 [engine] 的一次已结束插件引擎执行 (P4.4, [BridgeExecutionObserver.ended]); 没有对象的执行不登记.
     */
    fun trackBridgeSession(engine: String, executionId: String, objects: List<Any>) {
        if (objects.isEmpty()) return
        synchronized(lock) {
            prune()
            bridgeSessions += BridgeSession(engine, executionId, clock.currentTimeMillis(), clock.elapsedRealtime(), objects.map(::BridgeObject))
        }
    }

    /**
     * Resources of finished runtimes that the unreclaimed check found still alive, oldest first (the latest [MAX_UNRELEASED]).
     * zh-CN: 未回收检查发现仍存活的已结束 runtime 的资源, 先发现的在前 (最近 [MAX_UNRELEASED] 条).
     */
    fun unreleased(): List<UnreleasedResource> = synchronized(lock) { unreleased.toList() }

    /**
     * Called by [checkEnded] for each resource it found still alive.
     * zh-CN: [checkEnded] 每发现一个仍存活的资源调用一次.
     */
    protected open fun onUnreleased(resource: UnreleasedResource) = Unit

    /**
     * Finished runtimes that are still reachable, earliest first. Right after a script ends its runtime is listed here until the
     * next GC; [collect] tells the runtimes that something still holds.
     * zh-CN: 仍可达的已结束 runtime, 先结束的在前. 脚本刚结束时其 runtime 会列在这里直到下一次 GC; [collect] 之后剩下的才是仍被持有的.
     */
    fun retained(): List<RetainedRuntime> {
        val now = clock.elapsedRealtime()
        return synchronized(lock) {
            prune()
            ended.map { it.describe(now) }
        }
    }

    fun retainedCount(): Int = synchronized(lock) {
        prune()
        ended.size
    }

    /**
     * Finished plugin engine executions with a session object still reachable, earliest first. As with [retained], an execution that
     * has just ended is listed until the next GC; [collect] tells the ones that something still holds.
     * zh-CN: 仍有会话对象可达的已结束插件引擎执行, 先结束的在前. 与 [retained] 一样, 刚结束的执行会列出直到下一次 GC; [collect] 之后剩下的才是仍被持有的.
     */
    fun retainedBridgeSessions(): List<RetainedBridgeSession> {
        val now = clock.elapsedRealtime()
        return synchronized(lock) {
            prune()
            bridgeSessions.map { it.describe(now) }
        }
    }

    fun bridgeSessionCount(): Int = synchronized(lock) {
        prune()
        bridgeSessions.size
    }

    /**
     * [retainedBridgeSessions] counted per engine: every engine of [BRIDGE_ENGINES] in that order, 0 included, then any other engine.
     * zh-CN: 按引擎统计的 [retainedBridgeSessions]: 先按顺序列出 [BRIDGE_ENGINES] 的每个引擎 (含 0), 再列其他引擎.
     */
    fun bridgeSessionCounts(): Map<String, Int> {
        val counts = LinkedHashMap<String, Int>()
        BRIDGE_ENGINES.forEach { counts[it] = 0 }
        synchronized(lock) {
            prune()
            bridgeSessions.forEach { counts[it.engine] = (counts[it.engine] ?: 0) + 1 }
        }
        return counts
    }

    fun liveThreads(): List<Thread> = probes.liveThreads()

    /**
     * Reads every measurement without collecting garbage first; [includePss] false skips the PSS reading (it walks the memory
     * maps of the process and takes tens of milliseconds) and leaves [MemorySnapshot.pss] at -1.
     * zh-CN: 读取全部度量, 不先回收垃圾; [includePss] 为 false 时跳过 PSS (需要遍历进程内存映射, 耗时数十毫秒), [MemorySnapshot.pss] 为 -1.
     */
    fun snapshot(includePss: Boolean = true): MemorySnapshot {
        val javaHeapUsed = probes.javaHeapUsed()
        val javaHeapTotal = probes.javaHeapTotal()
        val javaHeapMax = probes.javaHeapMax()
        val nativeHeapAllocated = probes.nativeHeapAllocated()
        val nativeHeapSize = probes.nativeHeapSize()
        val threads = probes.liveThreads()
        return MemorySnapshot(
            javaHeapUsed = javaHeapUsed,
            javaHeapTotal = javaHeapTotal,
            javaHeapMax = javaHeapMax,
            nativeHeapAllocated = nativeHeapAllocated,
            nativeHeapSize = nativeHeapSize,
            pss = if (includePss) probes.pss() else -1,
            engines = probes.engines(),
            retainedRuntimes = retainedCount(),
            bridgeSessions = bridgeSessionCounts(),
            images = probes.images(),
            threads = threads.count(::isScriptThread),
            allThreads = threads.size,
            contexts = probes.contexts(),
            timestamp = clock.currentTimeMillis(),
        )
    }

    /**
     * [GC_ROUNDS] rounds of GC and finalization, then one more GC (T1 a).
     * zh-CN: [GC_ROUNDS] 轮 GC 与 finalization, 再加一次 GC (T1 a).
     */
    fun forceGc() {
        repeat(GC_ROUNDS) {
            probes.gc()
            probes.runFinalization()
        }
        probes.gc()
    }

    /**
     * Collects garbage until [cleared] holds or [budgetMs] runs out: one [forceGc], then a check every [POLL_INTERVAL_MS]
     * with another [forceGc] every [GC_EVERY_POLLS] checks. Returns the time it took, or null when the budget ran out.
     * zh-CN: 反复回收直到 [cleared] 成立或 [budgetMs] 用完: 先 [forceGc] 一次, 之后每 [POLL_INTERVAL_MS] 检查一次, 每 [GC_EVERY_POLLS]
     * 次检查再 [forceGc] 一次. 返回耗时, 预算用完时返回 null.
     */
    fun awaitCleared(budgetMs: Long, cleared: () -> Boolean): Long? {
        val started = clock.elapsedRealtime()
        forceGc()
        var rounds = 0
        while (true) {
            val elapsed = clock.elapsedRealtime() - started
            if (cleared()) return elapsed
            if (elapsed >= budgetMs) return null
            clock.sleep(POLL_INTERVAL_MS)
            if (++rounds % GC_EVERY_POLLS == 0) forceGc()
        }
    }

    /**
     * Forces garbage collection and waits up to [timeoutMs] for every finished runtime and bridge session to be reclaimed, then
     * returns a new snapshot.
     * zh-CN: 强制回收并最多等待 [timeoutMs] 让全部已结束的 runtime 与桥接会话被回收, 然后返回新的快照.
     */
    fun collect(timeoutMs: Long = COLLECT_TIMEOUT_MS): MemorySnapshot {
        awaitCleared(timeoutMs, ::isCleared)
        return snapshot()
    }

    /**
     * Whether no finished runtime and no bridge session is reachable any more: what [collect] waits for.
     * zh-CN: 是否已没有可达的已结束 runtime 与桥接会话, 即 [collect] 等待的条件.
     */
    fun isCleared(): Boolean = synchronized(lock) {
        prune()
        ended.isEmpty() && bridgeSessions.isEmpty()
    }

    /**
     * The unreclaimed runtime check (P2.3): the runtimes that ended at least [delayMs] ago and were not checked yet are collected
     * once (garbage collection, then up to [budgetMs] of polling), and those still reachable are returned. Each runtime is checked
     * only once.
     * zh-CN: 未回收检查 (P2.3): 对结束已至少 [delayMs] 且未检查过的 runtime 回收一次 (垃圾回收后最多轮询 [budgetMs]), 返回仍可达的那些.
     * 每个 runtime 只检查一次.
     */
    fun checkEnded(delayMs: Long = UNRECLAIMED_CHECK_DELAY_MS, budgetMs: Long = UNRECLAIMED_CHECK_BUDGET_MS): List<RetainedRuntime> {
        val (due, dueResources) = synchronized(lock) {
            prune()
            val now = clock.elapsedRealtime()
            val runtimes = ended.filter { !it.checked && now - it.endedElapsed >= delayMs }.onEach { it.checked = true }
            val resources = pendingResources.filter { now - it.endedElapsed >= delayMs }.also { pendingResources.removeAll(it.toSet()) }
            runtimes to resources
        }
        if (due.isEmpty() && dueResources.isEmpty()) return emptyList()
        awaitCleared(budgetMs) { due.all { it.get() == null } && dueResources.all { it.alive().isEmpty() } }
        val now = clock.elapsedRealtime()
        val found = dueResources.flatMap { entry ->
            entry.alive().map { UnreleasedResource(entry.id, entry.name, entry.source, it.name, now - entry.endedElapsed) }
        }
        if (found.isNotEmpty()) {
            synchronized(lock) {
                unreleased.addAll(found)
                while (unreleased.size > MAX_UNRELEASED) unreleased.removeFirst()
            }
            found.forEach(::onUnreleased)
        }
        return due.filter { it.get() != null }.map { it.describe(now) }
    }

    /**
     * The [MemoryClock.elapsedRealtime] at which the earliest unchecked runtime becomes due for [checkEnded], or null when none is waiting.
     * zh-CN: 最早一个未检查的 runtime 到期接受 [checkEnded] 检查的 [MemoryClock.elapsedRealtime] 时刻, 没有等待中的 runtime 时为 null.
     */
    fun nextCheckAt(delayMs: Long = UNRECLAIMED_CHECK_DELAY_MS): Long? = synchronized(lock) {
        prune()
        val runtimes = ended.filter { !it.checked }.minOfOrNull { it.endedElapsed }
        val resources = pendingResources.minOfOrNull { it.endedElapsed }
        listOfNotNull(runtimes, resources).minOrNull()?.let { it + delayMs }
    }

    // Caller holds lock.
    // zh-CN: 调用方持有 lock.
    private fun prune() {
        while (true) {
            val reference = queue.poll() ?: break
            (reference as? Ended)?.let(ended::remove)
        }
        ended.removeAll { it.get() == null }
        bridgeSessions.removeAll { it.alive().isEmpty() }
    }

    companion object {

        /** [RetainedRuntime.engine] of a Rhino script. zh-CN: Rhino 脚本的 [RetainedRuntime.engine]. */
        const val ENGINE_RHINO = "rhino"

        /**
         * [RetainedRuntime.engine] of a Node.js script, and the engine of the Node.js bridge sessions.
         * zh-CN: Node.js 脚本的 [RetainedRuntime.engine], 也是 Node.js 桥接会话的引擎.
         */
        const val ENGINE_NODE = "node"

        /**
         * The plugin engine families of the bridge sessions ([BridgeExecutionObserver]), in the order of [MemorySnapshot.bridgeSessions].
         * zh-CN: 桥接会话的插件引擎类别 ([BridgeExecutionObserver]), 顺序同 [MemorySnapshot.bridgeSessions].
         */
        @JvmField
        val BRIDGE_ENGINES = listOf(ENGINE_NODE, "bun", "quickjs", "lua", "python")

        const val GC_ROUNDS = 3
        const val POLL_INTERVAL_MS = 100L
        const val GC_EVERY_POLLS = 5
        const val COLLECT_TIMEOUT_MS = 10_000L

        /** Delay between the end of a script and its unreclaimed runtime check (P2.3). zh-CN: 脚本结束到未回收检查的间隔 (P2.3). */
        const val UNRECLAIMED_CHECK_DELAY_MS = 10_000L

        /**
         * Polling budget of the unreclaimed runtime check: the memory gate saw every reclaimable runtime go within about 1 s of a forced
         * collection on four devices (P1.4), so 3 s keeps the warning close to the delay without false alarms.
         * zh-CN: 未回收检查的轮询预算: 内存门禁在四台设备上看到可回收的 runtime 都在强制回收后约 1 s 内消失 (P1.4), 3 s 让警告紧跟间隔且不误报.
         */
        const val UNRECLAIMED_CHECK_BUDGET_MS = 3_000L

        /** How many findings [unreleased] keeps. zh-CN: [unreleased] 保留的结果条数. */
        const val MAX_UNRELEASED = 100

        /**
         * Thread names created for scripts: the script thread, threads.start, threads.pool, timers, the screen capturer handler thread
         * and looper threads (T1 d).
         * zh-CN: 为脚本创建的线程名: 脚本线程, threads.start, threads.pool, 定时器, 截屏 HandlerThread 与 Looper 线程 (T1 d).
         */
        @JvmField
        val SCRIPT_THREAD_MARKERS = listOf("ScriptThread-", "Spawn-", "Pool-", "Timer", "ScreenCapturer", "LooperThread")

        /**
         * Framework threads whose names contain a marker but which scripts never create: `SurfaceSyncGroupTimer` (Android 14+, created
         * on demand by the view system; appendix A of docs/memory/ROADMAP.md, 23046RP50C).
         * zh-CN: 名称含标记但并非脚本创建的框架线程: `SurfaceSyncGroupTimer` (Android 14 起, 视图系统按需创建; docs/memory/ROADMAP.md 附录 A, 23046RP50C).
         */
        @JvmField
        val NON_SCRIPT_THREAD_NAMES = setOf("SurfaceSyncGroupTimer")

        @JvmStatic
        fun isScriptThread(thread: Thread) = isScriptThread(thread.name)

        @JvmStatic
        fun isScriptThread(name: String) = name !in NON_SCRIPT_THREAD_NAMES && SCRIPT_THREAD_MARKERS.any { name.contains(it) }
    }
}
