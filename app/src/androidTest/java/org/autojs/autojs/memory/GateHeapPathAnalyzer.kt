package org.autojs.autojs.memory

import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import shark.AndroidMetadataExtractor
import shark.AndroidObjectInspectors
import shark.AndroidReferenceMatchers
import shark.HeapAnalysisFailure
import shark.HeapAnalysisSuccess
import shark.HeapAnalyzer
import shark.HeapGraph
import shark.HprofHeapGraph.Companion.openHeapGraph
import shark.IgnoredReferenceMatcher
import shark.Leak
import shark.LeakTrace
import shark.LeakTraceReference
import shark.LeakingObjectFinder
import shark.OnAnalysisProgressListener
import shark.ReferenceMatcher
import shark.ReferencePattern
import java.io.File

/**
 * Finds the shortest strong reference path from a GC root to every ScriptRuntime that has
 * already exited (`isExiting == true`) in a heap dump (docs/memory/ROADMAP.md T4).
 * Running scripts are excluded by that flag, so no identity mapping between the dump and the
 * test is needed. Each path keeps only class and field names, at most [MAX_STEPS] steps.
 * zh-CN: 在堆转储中为每个已退出 (`isExiting == true`) 的 ScriptRuntime 找到从 GC 根出发的最短强引用链 (docs/memory/ROADMAP.md T4).
 * 正在运行的脚本由该标志排除, 因此不需要在转储与测试之间对应对象身份. 每条链只保留类名与字段名, 最多 [MAX_STEPS] 层.
 */
internal object GateHeapPathAnalyzer {

    const val RUNTIME_CLASS = "org.autojs.autojs.runtime.ScriptRuntime"
    const val MAX_STEPS = 20
    private const val MAX_LEAKS = 6

    fun analyze(hprof: File): JSONObject {
        val started = SystemClock.elapsedRealtime()
        val result = JSONObject().put("hprofBytes", hprof.length())
        hprof.openHeapGraph().use { graph ->
            val runtimeClass = graph.findClassByName(RUNTIME_CLASS)
            val instances = runtimeClass?.instances?.toList().orEmpty()
            val exiting = instances.filter { it[RUNTIME_CLASS, "isExiting"]?.value?.asBoolean == true }
            val ids = exiting.map { it.objectId }.toSet()
            result.put("scriptRuntimeInstances", instances.size).put("exitedRuntimeInstances", exiting.size)
            if (ids.isEmpty()) {
                return result.put("analysisMs", SystemClock.elapsedRealtime() - started).put("leaks", JSONArray())
            }
            val primary = paths(hprof, graph, ids, AndroidReferenceMatchers.appDefaults)
            result.put("leaks", primary.leaks).put("leakGroups", primary.groups).put("tracedRuntimes", primary.traced)
                .put("unreachableRuntimes", primary.unreachable)
            primary.error?.let { result.put("error", it) }
            // A dead thread kept as a map key reaches its runtime through Thread.target first; the second pass
            // shows the next path, e.g. through the thread's Looper and MessageQueue (ROADMAP 1.2 H1).
            // zh-CN: 作为表键的已结束线程先经 Thread.target 到达 runtime; 第二遍给出下一条链, 例如经线程的 Looper 与 MessageQueue (ROADMAP 1.2 H1).
            if (primary.throughThreadTarget) {
                val ignored = IgnoredReferenceMatcher(ReferencePattern.InstanceFieldPattern("java.lang.Thread", "target"))
                val alternate = paths(hprof, graph, ids, AndroidReferenceMatchers.appDefaults + ignored)
                result.put("alternate", JSONObject().put("ignoring", "java.lang.Thread.target")
                    .put("leaks", alternate.leaks).put("tracedRuntimes", alternate.traced))
            }
        }
        return result.put("analysisMs", SystemClock.elapsedRealtime() - started)
    }

    /**
     * The same analysis for the objects the bridge gate tracks (docs/memory-phase2/ROADMAP.md T4): every instance of [classNames] in the
     * dump, except ScriptRuntime, which keeps the `isExiting` filter. The bridge gate dumps only after the last run of its scenario
     * ended, so every instance found belongs to a finished execution of this process.
     * zh-CN: 对桥接门禁跟踪的对象做同样的分析 (docs/memory-phase2/ROADMAP.md T4): 转储中 [classNames] 的全部实例, ScriptRuntime 仍按 `isExiting` 过滤.
     * 桥接门禁只在场景最后一次运行结束后转储, 因此找到的实例都属于本进程中已结束的执行.
     */
    fun analyzeClasses(hprof: File, classNames: List<String>): JSONObject {
        val started = SystemClock.elapsedRealtime()
        val result = JSONObject().put("hprofBytes", hprof.length())
        hprof.openHeapGraph().use { graph ->
            val counts = JSONObject()
            val ids = mutableSetOf<Long>()
            classNames.forEach { name ->
                val instances = graph.findClassByName(name)?.instances?.toList().orEmpty()
                val chosen = if (name != RUNTIME_CLASS) instances else {
                    instances.filter { it[RUNTIME_CLASS, "isExiting"]?.value?.asBoolean == true }
                }
                counts.put(name.substringAfterLast('.'), chosen.size)
                chosen.mapTo(ids) { it.objectId }
            }
            result.put("instances", counts)
            if (ids.isEmpty()) {
                return result.put("analysisMs", SystemClock.elapsedRealtime() - started).put("leaks", JSONArray())
            }
            val primary = paths(hprof, graph, ids, AndroidReferenceMatchers.appDefaults)
            result.put("leaks", primary.leaks).put("leakGroups", primary.groups).put("tracedObjects", primary.traced)
                .put("unreachableObjects", primary.unreachable)
            primary.error?.let { result.put("error", it) }
        }
        return result.put("analysisMs", SystemClock.elapsedRealtime() - started)
    }

    private class Paths(
        val leaks: JSONArray,
        val groups: Int,
        val traced: Int,
        val unreachable: Int,
        val throughThreadTarget: Boolean,
        val error: String?,
    )

    private fun paths(hprof: File, graph: HeapGraph, ids: Set<Long>, matchers: List<ReferenceMatcher>): Paths {
        val finder = object : LeakingObjectFinder {
            override fun findLeakingObjectIds(graph: HeapGraph): Set<Long> = ids
        }
        val analysis = HeapAnalyzer(OnAnalysisProgressListener.NO_OP).analyze(
            heapDumpFile = hprof,
            graph = graph,
            leakingObjectFinder = finder,
            referenceMatchers = matchers,
            computeRetainedHeapSize = false,
            objectInspectors = AndroidObjectInspectors.appDefaults,
            metadataExtractor = AndroidMetadataExtractor,
        )
        return when (analysis) {
            is HeapAnalysisSuccess -> {
                val all: List<Pair<String, Leak>> = analysis.applicationLeaks.map { "application" to it } +
                    analysis.libraryLeaks.map { "library" to it }
                val leaks = JSONArray()
                all.sortedByDescending { it.second.leakTraces.size }.take(MAX_LEAKS).forEach { (kind, leak) -> leaks.put(describe(kind, leak)) }
                val throughTarget = all.any { (_, leak) ->
                    leak.leakTraces.any { trace ->
                        trace.referencePath.any { it.referenceName == "target" && it.originObject.className.contains("Thread") }
                    }
                }
                Paths(leaks, all.size, all.sumOf { it.second.leakTraces.size }, analysis.unreachableObjects.size, throughTarget, null)
            }
            is HeapAnalysisFailure -> Paths(JSONArray(), 0, 0, 0, false, analysis.exception.toString().take(600))
        }
    }

    private fun describe(kind: String, leak: Leak): JSONObject {
        val trace = leak.leakTraces.first()
        val steps = trace.referencePath.map(::step)
        val kept = if (steps.size <= MAX_STEPS) steps else {
            steps.take(MAX_STEPS - 8) + "... (${steps.size - MAX_STEPS + 1} steps omitted)" + steps.takeLast(7)
        }
        return JSONObject()
            .put("kind", kind)
            .put("runtimes", leak.leakTraces.size)
            .put("gcRoot", trace.gcRootType.name)
            .put("root", trace.referencePath.firstOrNull()?.let(::step) ?: trace.leakingObject.className)
            .put("steps", steps.size)
            .put("path", JSONArray(kept + "-> ${trace.leakingObject.className}"))
            .put("signature", leak.signature.take(16))
            .put("summary", summary(trace))
    }

    private fun step(reference: LeakTraceReference): String {
        val owner = reference.originObject.className
        return when (reference.referenceType) {
            LeakTraceReference.ReferenceType.STATIC_FIELD -> "$owner.${reference.referenceName} (static)"
            LeakTraceReference.ReferenceType.INSTANCE_FIELD -> "$owner.${reference.referenceName}"
            LeakTraceReference.ReferenceType.ARRAY_ENTRY -> "$owner[]"
            LeakTraceReference.ReferenceType.LOCAL -> "$owner <local>"
        }
    }

    /** Simple class names only, so that a report line can be compared at a glance. */
    private fun summary(trace: LeakTrace): String = trace.referencePath.joinToString(" -> ") { reference ->
        val simple = reference.originObject.classSimpleName
        when (reference.referenceType) {
            LeakTraceReference.ReferenceType.STATIC_FIELD, LeakTraceReference.ReferenceType.INSTANCE_FIELD -> "$simple.${reference.referenceName}"
            LeakTraceReference.ReferenceType.ARRAY_ENTRY -> "$simple[]"
            LeakTraceReference.ReferenceType.LOCAL -> "$simple<local>"
        }
    } + " -> " + trace.leakingObject.classSimpleName
}
