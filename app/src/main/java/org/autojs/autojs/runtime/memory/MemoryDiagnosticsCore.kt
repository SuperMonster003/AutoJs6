package org.autojs.autojs.runtime.memory

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
 * The memory diagnostics without Android (docs/memory/ROADMAP.md P2.1): snapshots, forced collection, and the registry of
 * finished script runtimes. A finished runtime is held by a weak reference only and leaves the registry once the GC reclaims
 * it, so the registry itself never keeps a script alive. [MemoryDiagnostics] is the process-wide instance; the memory gate
 * (T3), the developer options page (T10) and the script API (T9) all read it.
 *
 * zh-CN: 不依赖 Android 的内存诊断 (docs/memory/ROADMAP.md P2.1): 快照, 强制回收, 以及已结束脚本 runtime 的登记表. 已结束的 runtime
 * 只以弱引用登记, 被 GC 回收后自动出列, 因此登记表本身不会让脚本存活. [MemoryDiagnostics] 是进程级实例; 内存门禁 (T3), 开发者选项页面
 * (T10) 与脚本 API (T9) 都从它读取.
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
        var checked: Boolean,
    ) : WeakReference<Any>(runtime, queue) {
        fun describe(now: Long) = RetainedRuntime(id, name, source, endedAt, now - endedElapsed, exitKind)
    }

    private val lock = Any()
    private val queue = ReferenceQueue<Any>()

    // Guarded by lock; in the order the scripts ended.
    // zh-CN: 由 lock 保护; 按脚本结束的先后排列.
    private val ended = LinkedHashSet<Ended>()

    /**
     * Registers a finished script runtime by a weak reference. With [check] false the runtime is never reported by [checkEnded].
     * zh-CN: 以弱引用登记已结束的脚本 runtime. [check] 为 false 时 [checkEnded] 不会报告它.
     */
    fun track(runtime: Any, id: Int, name: String, source: String, exitKind: ExitKind, check: Boolean = true) {
        synchronized(lock) {
            prune()
            ended.add(Ended(runtime, queue, id, name, source, clock.currentTimeMillis(), clock.elapsedRealtime(), exitKind, !check))
        }
    }

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
     * Forces garbage collection and waits up to [timeoutMs] for every finished runtime to be reclaimed, then returns a new snapshot.
     * zh-CN: 强制回收并最多等待 [timeoutMs] 让全部已结束的 runtime 被回收, 然后返回新的快照.
     */
    fun collect(timeoutMs: Long = COLLECT_TIMEOUT_MS): MemorySnapshot {
        awaitCleared(timeoutMs) { retainedCount() == 0 }
        return snapshot()
    }

    /**
     * The unreclaimed runtime check (P2.3): the runtimes that ended at least [delayMs] ago and were not checked yet are collected
     * once (garbage collection, then up to [budgetMs] of polling), and those still reachable are returned. Each runtime is checked
     * only once.
     * zh-CN: 未回收检查 (P2.3): 对结束已至少 [delayMs] 且未检查过的 runtime 回收一次 (垃圾回收后最多轮询 [budgetMs]), 返回仍可达的那些.
     * 每个 runtime 只检查一次.
     */
    fun checkEnded(delayMs: Long = UNRECLAIMED_CHECK_DELAY_MS, budgetMs: Long = UNRECLAIMED_CHECK_BUDGET_MS): List<RetainedRuntime> {
        val due = synchronized(lock) {
            prune()
            val now = clock.elapsedRealtime()
            ended.filter { !it.checked && now - it.endedElapsed >= delayMs }.onEach { it.checked = true }
        }
        if (due.isEmpty()) return emptyList()
        awaitCleared(budgetMs) { due.all { it.get() == null } }
        val now = clock.elapsedRealtime()
        return due.filter { it.get() != null }.map { it.describe(now) }
    }

    /**
     * The [MemoryClock.elapsedRealtime] at which the earliest unchecked runtime becomes due for [checkEnded], or null when none is waiting.
     * zh-CN: 最早一个未检查的 runtime 到期接受 [checkEnded] 检查的 [MemoryClock.elapsedRealtime] 时刻, 没有等待中的 runtime 时为 null.
     */
    fun nextCheckAt(delayMs: Long = UNRECLAIMED_CHECK_DELAY_MS): Long? = synchronized(lock) {
        prune()
        ended.filter { !it.checked }.minOfOrNull { it.endedElapsed + delayMs }
    }

    // Caller holds lock.
    // zh-CN: 调用方持有 lock.
    private fun prune() {
        while (true) {
            val reference = queue.poll() ?: break
            (reference as? Ended)?.let(ended::remove)
        }
        ended.removeAll { it.get() == null }
    }

    companion object {

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
