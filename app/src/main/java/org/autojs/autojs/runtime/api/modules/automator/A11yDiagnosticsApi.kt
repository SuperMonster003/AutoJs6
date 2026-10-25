package org.autojs.autojs.runtime.api.modules.automator

import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.diagnostics.A11yStats
import org.autojs.autojs.core.automator.diagnostics.NodeDump
import org.autojs.autojs.core.automator.diagnostics.PickExplainer
import org.autojs.autojs.core.automator.diagnostics.SelectorExplainer
import org.autojs.autojs.core.automator.pick.PickArguments
import org.autojs.autojs.core.automator.pick.PickResultAdapter
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject.DONTENUM
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.Wrapper

/**
 * The diagnostics of the `auto` module (roadmap P5.1): `auto.explain(selector, root?, compass?, resultType?)` tallies
 * every filter of a selector over the window roots (or under [root]) and reports which one
 * leaves nothing (see [SelectorExplainer]), then what a single result goes through (selector P3.5,
 * [PickExplainer]); `auto.dump(options?)` renders the window roots (or
 * `options.root`) as text / JSON / XML (see [NodeDump], options per [DumpArguments]);
 * `auto.stats` is a snapshot of the script's search, query and Flow step tallies with `reset()`
 * (see [A11yStats]). Results are plain JavaScript objects whose `toString()` is the text report.
 * zh-CN: `auto` 模块的诊断 (路线图 P5.1): `auto.explain(selector, root?, compass?, resultType?)` 在各窗口根 (或 [root] 之下) 对选择器的每个过滤器计数,
 * 报告哪一个使结果清空 (见 [SelectorExplainer]), 以及单个结果经历的过程 (selector P3.5, [PickExplainer]); `auto.dump(options?)` 把各窗口根 (或 `options.root`) 渲染为文本 / JSON / XML
 * (见 [NodeDump], 选项见 [DumpArguments]); `auto.stats` 为脚本的搜索, 查询与 Flow 步骤计数的快照, 带 `reset()` (见 [A11yStats]).
 * 结果为普通 JavaScript 对象, 其 `toString()` 为文本报告.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
internal class A11yDiagnosticsApi(private val scriptRuntime: ScriptRuntime) {

    private val bridge: AccessibilityBridge get() = scriptRuntime.accessibilityBridge

    private val scope: VarScope get() = scriptRuntime.topLevelScope

    /**
     * [rest]: the arguments after the selector, `root?, compass?, resultType?`: the first is the root when it is a
     * `UiObject`, or nullish among three (P3.5).
     * zh-CN: [rest] 为选择器之后的参数 `root?, compass?, resultType?`: 首个参数为 `UiObject` 时 (或三个参数中为空值时) 是 root (P3.5).
     */
    fun explain(selectorArg: Any?, rest: List<Any?>): NativeObject {
        val selector = PickArguments.uiObject.buildSelector("auto.explain", "selector", unwrap(selectorArg), PickResultAdapter.binding(scriptRuntime))
        val args = rest.map { unwrap(it) }
        val hasRoot = args.firstOrNull() is UiObject || args.size == 3
        val parts = PickArguments.uiObject.explainParts("auto.explain", if (hasRoot) args.drop(1) else args)
        bridge.ensureServiceStarted()
        val roots = roots(if (hasRoot) args[0] else null)
        val explanation = SelectorExplainer.explain(roots, selector.selector.effectiveFilters, selector.toString())
        val settings = PickExplainer.Settings(fastPath = bridge.isFastPathEnabled, refreshOnHit = bridge.isRefreshOnHit)
        val pick = PickExplainer.explain(roots, selector.selector, explanation, parts, settings, { it.refresh() })
        val text = explanation.render() + "\n" + pick.render()
        return RhinoUtils.withRhinoContext(scriptRuntime) { _ ->
            obj(
                "selector" to explanation.selector,
                "roots" to explanation.roots.toDouble(),
                "nodes" to explanation.nodes.toDouble(),
                "truncated" to explanation.truncated,
                "found" to explanation.found,
                "count" to explanation.count.toDouble(),
                "failingIndex" to explanation.failingIndex.toDouble(),
                "failingFilter" to explanation.failingFilter,
                "steps" to explanation.steps.map { step ->
                    linkedMapOf<String, Any?>(
                        "index" to step.index.toDouble(),
                        "filter" to step.filter,
                        "matched" to step.matched.toDouble(),
                        "cumulative" to step.cumulative.toDouble(),
                    )
                },
                "matches" to explanation.matches,
                "nearMisses" to explanation.nearMisses,
                // What a single result goes through (P3.5). zh-CN: 单个结果经历的过程 (P3.5).
                "target" to pick.target,
                "targetIndex" to pick.targetIndex.toDouble(),
                "preference" to pick.preference,
                "compass" to pick.compass,
                "compassError" to pick.compassError,
                "trail" to pick.trail.map { hop -> linkedMapOf<String, Any?>("step" to hop.step, "node" to hop.node) },
                "reached" to pick.reached,
                "result" to pick.result?.let { r -> linkedMapOf<String, Any?>("given" to r.given, "kind" to r.kind, "detail" to r.detail) },
                "fastPath" to pick.fastPathKey,
                "fastPathEnabled" to pick.fastPathEnabled,
                "refreshOnHit" to pick.refreshOnHit,
                "refreshes" to pick.refreshes,
                "normalize" to pick.normalize,
                "text" to text,
            ).withToString { text }
        }
    }

    fun dump(optionsArg: Any?): String {
        val parsed = DumpArguments.parse(optionsArg)
        bridge.ensureServiceStarted()
        val roots = parsed.root?.let { listOf(it) } ?: windowRoots()
        return NodeDump.dump(roots, parsed.options, asList = parsed.root == null).text
    }

    fun stats(): NativeObject {
        val stats = bridge.stats
        val snapshot = stats.snapshot()
        val text = snapshot.render()
        return RhinoUtils.withRhinoContext(scriptRuntime) { _ ->
            obj(
                "since" to snapshot.since.toDouble(),
                "now" to snapshot.now.toDouble(),
                "elapsed" to snapshot.elapsed.toDouble(),
                "searches" to timing(snapshot.searches),
                "queries" to timing(A11yStats.Timing(snapshot.queries.count, snapshot.queries.totalMillis, snapshot.queries.maxMillis)).apply {
                    put("found", snapshot.queries.found.toDouble())
                    put("missed", snapshot.queries.missed.toDouble())
                    put("attempts", snapshot.queries.attempts.toDouble())
                    // Searches again after a single result that had left the window (docs/selector/ROADMAP.md P3.2).
                    // zh-CN: 单个结果已离开窗口后的重新搜索 (P3.2).
                    put("refinds", snapshot.queries.refinds.toDouble())
                },
                // The framework fast path of the polled queries (docs/selector/ROADMAP.md P2.4).
                // zh-CN: 轮询查询的框架快路径 (P2.4).
                "fastPath" to linkedMapOf<String, Any?>(
                    "hits" to snapshot.fastPath.hits.toDouble(),
                    "misses" to snapshot.fastPath.misses.toDouble(),
                    "fallbacks" to snapshot.fastPath.fallbacks.toDouble(),
                    "mismatches" to snapshot.fastPath.mismatches.toDouble(),
                    "walks" to snapshot.fastPath.walks.toDouble(),
                ),
                "selectors" to snapshot.selectors.map { selectorStats(it) },
                "slowest" to snapshot.slowest?.let { selectorStats(it) },
                "steps" to snapshot.steps.map { step ->
                    linkedMapOf<String, Any?>(
                        "name" to step.name,
                        "runs" to timing(step.runs),
                        "rejected" to step.rejected.toDouble(),
                    )
                },
                "text" to text,
            ).apply {
                defineProperty("reset", newBaseFunction(scope, "reset", { _ -> stats.reset(); UNDEFINED }, NOT_CONSTRUCTABLE), DONTENUM or READONLY or PERMANENT)
            }.withToString { text }
        }
    }

    private fun roots(rootArg: Any?): List<UiObject> = when (val root = unwrap(rootArg)) {
        null -> windowRoots()
        is UiObject -> listOf(root)
        else -> throw WrappedIllegalArgumentException("Argument root must be a UiObject, got ${root::class.java.simpleName}")
    }

    private fun windowRoots(): List<UiObject> = bridge.windowRoots().map { UiObject.createRoot(it, null, bridge) }

    private fun timing(t: A11yStats.Timing): LinkedHashMap<String, Any?> = linkedMapOf(
        "count" to t.count.toDouble(),
        "totalMillis" to t.totalMillis.toDouble(),
        "averageMillis" to t.averageMillis,
        "maxMillis" to t.maxMillis.toDouble(),
    )

    private fun selectorStats(s: A11yStats.SelectorStats): LinkedHashMap<String, Any?> = linkedMapOf(
        "selector" to s.selector,
        "searches" to timing(s.searches),
        "queries" to timing(s.queries),
        "found" to s.found.toDouble(),
    )

    private fun obj(vararg pairs: Pair<String, Any?>): NativeObject = RhinoUtils.newNativeObject(scope).apply {
        for ((key, value) in pairs) {
            put(key, this, RhinoUtils.toJsValue(scope, value))
        }
    }

    private fun NativeObject.withToString(text: () -> String): NativeObject = apply {
        defineProperty("toString", newBaseFunction(scope, "toString", { _ -> text() }, NOT_CONSTRUCTABLE), DONTENUM or READONLY or PERMANENT)
    }

    private fun unwrap(value: Any?): Any? = when (value) {
        is Undefined -> null
        is Wrapper -> unwrap(value.unwrap())
        else -> value
    }

}
