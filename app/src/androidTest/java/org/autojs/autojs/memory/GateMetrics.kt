package org.autojs.autojs.memory

import android.os.SystemClock
import org.autojs.autojs.runtime.memory.MemoryDiagnostics
import org.autojs.autojs.runtime.memory.MemoryDiagnosticsCore
import org.json.JSONObject
import java.lang.ref.Reference

/**
 * The six gate measurements of docs/memory/ROADMAP.md T1, read after a forced collection.
 * Since P2.1 every value comes from MemoryDiagnostics, the same source as the developer options page and the script API (T3).
 * zh-CN: docs/memory/ROADMAP.md T1 的六项度量, 在强制回收后读取. 自 P2.1 起全部取自 MemoryDiagnostics, 与开发者选项页面和脚本 API 同源 (T3).
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

    fun liveThreads(): List<Thread> = MemoryDiagnostics.liveThreads()

    fun isScriptThread(thread: Thread) = MemoryDiagnosticsCore.isScriptThread(thread)

    fun forceGc() = MemoryDiagnostics.forceGc()

    /**
     * Collects until every reference is cleared or the budget runs out (T1 a).
     * Returns the time it took, or null when some referent is still reachable.
     * zh-CN: 反复回收直到全部引用被清除或预算用完 (T1 a). 返回耗时, 仍有对象可达时返回 null.
     */
    fun awaitCleared(references: Collection<Reference<*>>, budgetMs: Long): Long? =
        MemoryDiagnostics.awaitCleared(budgetMs) { references.all { it.get() == null } }

    fun sample(): GateSample {
        val started = SystemClock.elapsedRealtime()
        forceGc()
        // PSS is not a gate measurement and takes tens of milliseconds to read.
        // zh-CN: PSS 不是门禁度量, 读取耗时数十毫秒.
        val snapshot = MemoryDiagnostics.snapshot(includePss = false)
        return GateSample(
            javaHeapBytes = snapshot.javaHeapUsed,
            nativeHeapBytes = snapshot.nativeHeapAllocated,
            scriptThreads = snapshot.threads,
            allThreads = snapshot.allThreads,
            rhinoContexts = snapshot.contexts,
            engines = snapshot.engines,
            elapsedMs = SystemClock.elapsedRealtime() - started,
        )
    }
}
