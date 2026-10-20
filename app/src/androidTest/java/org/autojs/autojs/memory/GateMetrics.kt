package org.autojs.autojs.memory

import android.os.Debug
import android.os.SystemClock
import org.autojs.autojs.AutoJs
import org.autojs.autojs.rhino.AndroidContextFactory
import org.json.JSONObject
import org.mozilla.javascript.ContextFactory
import java.lang.ref.Reference
import java.util.concurrent.atomic.AtomicInteger

/**
 * The six gate measurements of docs/memory/ROADMAP.md T1, read after a forced collection.
 * This is the temporary test-side implementation named in P0.2; P2.1 replaces it with MemoryDiagnostics.
 * zh-CN: docs/memory/ROADMAP.md T1 的六项度量, 在强制回收后读取. 这是 P0.2 所说的测试内临时实现, P2.1 起改用 MemoryDiagnostics.
 */
internal data class GateSample(
    val javaHeapBytes: Long,
    val nativeHeapBytes: Long,
    val scriptThreads: Int,
    val allThreads: Int,
    val rhinoContexts: Int,
    val engines: Int,
    val elapsedMs: Long,
) {
    fun json(): JSONObject = JSONObject()
        .put("javaHeapBytes", javaHeapBytes)
        .put("nativeHeapBytes", nativeHeapBytes)
        .put("scriptThreads", scriptThreads)
        .put("allThreads", allThreads)
        .put("rhinoContexts", rhinoContexts)
        .put("engines", engines)
        .put("elapsedMs", elapsedMs)
}

internal object GateMetrics {

    /**
     * Thread names created for scripts: the script thread, threads.start, threads.pool, timers,
     * the screen capturer handler thread and looper threads (T1 d).
     * zh-CN: 为脚本创建的线程名: 脚本线程, threads.start, threads.pool, 定时器, 截屏 HandlerThread 与 Looper 线程 (T1 d).
     */
    val SCRIPT_THREAD_MARKERS = listOf("ScriptThread-", "Spawn-", "Pool-", "Timer", "ScreenCapturer", "LooperThread")

    /**
     * Framework threads whose names contain a marker but which scripts never create: `SurfaceSyncGroupTimer`
     * (Android 14+, created on demand by the view system; appendix A of docs/memory/ROADMAP.md, 23046RP50C).
     * zh-CN: 名称含标记但并非脚本创建的框架线程: `SurfaceSyncGroupTimer` (Android 14 起, 视图系统按需创建; docs/memory/ROADMAP.md 附录 A, 23046RP50C).
     */
    val NON_SCRIPT_THREAD_NAMES = setOf("SurfaceSyncGroupTimer")

    private const val GC_ROUNDS = 3

    private val contextCountField by lazy {
        runCatching {
            AndroidContextFactory::class.java.getDeclaredField("mContextCount").apply { isAccessible = true }
        }.getOrNull()
    }

    /**
     * Rhino contexts entered and not yet released, from the private counter of the global factory.
     * Read by reflection so that P0 does not touch host sources; -1 means the counter is unavailable.
     * zh-CN: 已进入未释放的 Rhino Context 数, 取自全局工厂的私有计数器. 用反射读取以免 P0 改动宿主源码; -1 表示计数器不可用.
     */
    fun rhinoContexts(): Int {
        val factory = ContextFactory.getGlobal() as? AndroidContextFactory ?: return -1
        val field = contextCountField ?: return -1
        return (field.get(factory) as? AtomicInteger)?.get() ?: -1
    }

    fun liveThreads(): List<Thread> {
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

    fun isScriptThread(thread: Thread) = thread.name !in NON_SCRIPT_THREAD_NAMES && SCRIPT_THREAD_MARKERS.any { thread.name.contains(it) }

    fun forceGc() {
        val runtime = Runtime.getRuntime()
        repeat(GC_ROUNDS) {
            runtime.gc()
            System.runFinalization()
        }
        runtime.gc()
    }

    /**
     * Collects until every reference is cleared or the budget runs out (T1 a).
     * Returns the time it took, or null when some referent is still reachable.
     * zh-CN: 反复回收直到全部引用被清除或预算用完 (T1 a). 返回耗时, 仍有对象可达时返回 null.
     */
    fun awaitCleared(references: Collection<Reference<*>>, budgetMs: Long): Long? {
        val started = SystemClock.elapsedRealtime()
        forceGc()
        var rounds = 0
        while (true) {
            val elapsed = SystemClock.elapsedRealtime() - started
            if (references.all { it.get() == null }) return elapsed
            if (elapsed >= budgetMs) return null
            SystemClock.sleep(100)
            if (++rounds % 5 == 0) forceGc()
        }
    }

    fun sample(): GateSample {
        val started = SystemClock.elapsedRealtime()
        forceGc()
        val runtime = Runtime.getRuntime()
        val threads = liveThreads()
        return GateSample(
            javaHeapBytes = runtime.totalMemory() - runtime.freeMemory(),
            nativeHeapBytes = Debug.getNativeHeapAllocatedSize(),
            scriptThreads = threads.count(::isScriptThread),
            allThreads = threads.size,
            rhinoContexts = rhinoContexts(),
            engines = AutoJs.instance.scriptEngineService.engines.size,
            elapsedMs = SystemClock.elapsedRealtime() - started,
        )
    }
}
