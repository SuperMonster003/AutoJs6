package org.autojs.autojs.core.automator.diagnostics

import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.filter.Filter
import org.autojs.autojs.core.automator.filter.JsLiteral
import org.autojs.autojs.core.automator.filter.Selector
import org.autojs.autojs.core.automator.pick.Compass
import org.autojs.autojs.core.automator.pick.CompassStep
import org.autojs.autojs.core.automator.pick.NodeMethodTable
import org.autojs.autojs.core.automator.pick.PickCompass
import org.autojs.autojs.core.automator.pick.PickParts
import org.autojs.autojs.core.automator.pick.PickResult
import org.autojs.autojs.core.automator.pick.PickResultType
import org.autojs.autojs.core.automator.search.FastPath
import org.autojs.autojs.core.automator.toolkit.UiNodes

/**
 * What a single result of a selector goes through (docs/selector/ROADMAP.md P3.5), the part of
 * `auto.explain(selector, compass?, resultType?)` after the tallies of [SelectorExplanation]: the node a single result
 * takes ([target]: the first match, or the best one under `prefer(...)`), the node each step of the compass reaches from
 * it ([trail], up to the first step that reaches none), what the result type resolves to ([result]: a symbol, or the
 * registry method and its converted arguments; nothing is called), and whether the fast path, the refresh on hit and
 * `normalize(...)` apply. Rendered as text by [render]. Produced by [PickExplainer.explain].
 * zh-CN: 选择器的单个结果所经历的过程 (P3.5), 即 `auto.explain(selector, compass?, resultType?)` 中 [SelectorExplanation] 计数之后的部分: 单个结果取哪个节点
 * ([target]: 首个匹配, 或 `prefer(...)` 下的最优者), 从它起罗盘每一步到达的节点 ([trail], 直到首个没有到达任何节点的步骤), 结果类型解析为什么 ([result]: 符号,
 * 或注册表方法及转换后的参数; 不调用任何方法), 以及快路径, 命中刷新与 `normalize(...)` 是否生效. [render] 渲染为文本. 由 [PickExplainer.explain] 产出.
 */
class PickExplanation<N : UiNode<N>> internal constructor(
    /** How many nodes the selector matches. zh-CN: 选择器匹配的节点数. */
    val candidates: Int,
    /** The node a single result takes, or null. zh-CN: 单个结果取的节点, 或 null. */
    val target: N?,
    /** The 0-based index of [target] among the matches in the order found, `-1` without one. zh-CN: [target] 在按找到顺序的匹配中的下标 (从 0 起), 没有时为 `-1`. */
    val targetIndex: Int,
    /** `prefer(...)` of the selector, or null. zh-CN: 选择器的 `prefer(...)`, 或 null. */
    val preference: String?,
    /** The compass given, as a script literal, or null. zh-CN: 给出的罗盘 (脚本字面量形式), 或 null. */
    val compass: String?,
    /** Why the compass is refused, or null. zh-CN: 罗盘被拒绝的原因, 或 null. */
    val compassError: String?,
    /** One hop per step of the compass walked from [target], up to the first that reaches nothing. zh-CN: 从 [target] 起罗盘每一步一项, 直到首个没有到达节点的步骤. */
    val trail: List<Hop<N>>,
    /** The node at the end of the compass ([target] without a compass), or null. zh-CN: 罗盘终点的节点 (没有罗盘时为 [target]), 或 null. */
    val reached: N?,
    /** What the result type resolves to, or null when none is given. zh-CN: 结果类型的解析, 未给出时为 null. */
    val result: Resolution?,
    /** The key of the fast path (`id(...)`), or null when the selector has none. zh-CN: 快路径的键 (`id(...)`), 选择器没有时为 null. */
    val fastPathKey: String?,
    /** Whether the fast path is on (no flag `noFastPath`). zh-CN: 快路径是否开启 (未设 `noFastPath` 标志). */
    val fastPathEnabled: Boolean,
    /** Whether a single result is refreshed before it is returned (no flag `noRefreshOnHit`). zh-CN: 单个结果返回前是否刷新 (未设 `noRefreshOnHit` 标志). */
    val refreshOnHit: Boolean,
    /** Whether [target] refreshes now; null without a target or with the refresh off. zh-CN: [target] 此刻能否刷新; 没有目标或刷新关闭时为 null. */
    val refreshes: Boolean?,
    /** `normalize(...)` in effect, or null. zh-CN: 生效中的 `normalize(...)`, 或 null. */
    val normalize: String?,
) {

    /** A step of the compass ([step], as the compass writes it) and the node it reaches, null for none. zh-CN: 罗盘的一步 ([step], 罗盘写法) 及其到达的节点, 没有时为 null. */
    class Hop<N>(val step: String, val node: N?)

    /**
     * The result type as given ([given], a script literal) and what it resolves to: [kind] is `symbol`, `arrayForm`,
     * `method`, `empty`, `nothing` or `refused`, [detail] says the rest.
     * zh-CN: 给出的结果类型 ([given], 脚本字面量) 及其解析: [kind] 为 `symbol`, `arrayForm`, `method`, `empty`, `nothing` 或 `refused`, [detail] 为其余说明.
     */
    class Resolution(val given: String, val kind: String, val detail: String)

    /** The text report; [describe] renders a node on one line. zh-CN: 文本报告; [describe] 把一个节点渲染成一行. */
    fun render(describe: (N) -> String = { NodeDump.line(it) }): String = buildString {
        append("single result: ")
        if (target == null) {
            append("none")
        } else {
            append(describe(target)).append(" (")
            when (preference) {
                null -> append("first of ").append(candidates).append(" match(es)")
                else -> append("match ").append(targetIndex + 1).append(" of ").append(candidates).append(" by ").append(preference)
            }
            append(')')
        }
        if (compass != null) {
            append("\ncompass ").append(compass).append(':')
            when {
                compassError != null -> append(" refused, ").append(compassError)
                target == null -> append(" not walked, no single result")
                trail.isEmpty() -> append(" the node itself")
                else -> {
                    val width = trail.maxOf { it.step.length }
                    trail.forEachIndexed { i, hop ->
                        append("\n  ").append(i + 1).append(". ").append(hop.step.padEnd(width)).append("  -> ")
                        append(hop.node?.let(describe) ?: "nothing, the walk stops here")
                    }
                }
            }
        }
        if (result != null) append("\nresult ").append(result.given).append(": ").append(result.detail)
        append("\nfast path: ").append(
            when {
                fastPathKey == null -> "none, no exact id condition"
                !fastPathEnabled -> "off (flag noFastPath), key $fastPathKey"
                else -> "$fastPathKey, looked up by polled queries (findOne with a timeout, untilFindOne, waitFor)"
            }
        )
        append("\nrefresh on hit: ").append(
            when (refreshes) {
                null -> if (refreshOnHit) "on" else "off (flag noRefreshOnHit)"
                true -> "on, the single result refreshes"
                false -> "on, the single result has left the window: a single-result query searches again"
            }
        )
        append("\nnormalize: ").append(normalize ?: "off")
    }

    override fun toString(): String = render()

}

/**
 * Tells what a single result of a selector goes through, see [PickExplanation]. It reads the nodes and refreshes the
 * target (through [refresh]), and calls no method of the result type.
 * zh-CN: 说明选择器的单个结果所经历的过程, 见 [PickExplanation]. 只读取节点并刷新目标 (经 [refresh]), 不调用结果类型的任何方法.
 */
object PickExplainer {

    /** The flags of the bridge that decide the fast path and the refresh on hit. zh-CN: 决定快路径与命中刷新的 bridge 标志. */
    class Settings(val fastPath: Boolean = true, val refreshOnHit: Boolean = true)

    /**
     * @param selection the tallies of the selector over [roots] ([SelectorExplainer.explain]), whose first match is the
     * single result without `prefer(...)`.
     * @param parts the compass and the result type given ([org.autojs.autojs.core.automator.pick.PickArguments.explainParts]).
     * zh-CN: [selection] 为选择器在 [roots] 上的计数, 没有 `prefer(...)` 时其首个匹配即单个结果; [parts] 为给出的罗盘与结果类型.
     */
    fun <N : UiNode<N>> explain(
        roots: List<N>,
        selector: Selector,
        selection: SelectorExplanation<N>,
        parts: PickParts,
        settings: Settings,
        refresh: (N) -> Boolean,
        maxNodes: Int = UiNodes.DEFAULT_MAX_NODES,
    ): PickExplanation<N> {
        val preference = selector.preference
        val matches = when (preference) {
            null -> selection.matches.take(1)
            else -> all(roots, selector.snapshot(), maxNodes)
        }
        val target = preference?.best(matches) ?: matches.firstOrNull()
        val trail = ArrayList<PickExplanation.Hop<N>>()
        var reached = target
        when (val walk = parts.walk) {
            is PickCompass.Steps -> if (target != null) {
                // Step by step as PickExecutor walks it, `k` passing over invisible ancestors under prefer('visible').
                // zh-CN: 与 PickExecutor 的行走一致, 逐步进行, prefer('visible') 下 `k` 越过不可见的祖先.
                val visibleOnly = preference?.visibleOnly == true
                var node: N = target
                for (step in walk.steps) {
                    val next = Compass.navigate(node, listOf(step), visibleOnly)
                    trail += PickExplanation.Hop(label(step), next)
                    node = next ?: break
                }
                reached = if (trail.isEmpty()) target else trail.last().node
            }
            is PickCompass.Refused -> reached = null
            PickCompass.PassOn, null -> Unit
        }
        return PickExplanation(
            candidates = if (preference == null) selection.count else matches.size,
            target = target,
            targetIndex = target?.let { t -> matches.indexOfFirst { it === t } } ?: -1,
            preference = preference?.toString(),
            compass = parts.compass?.let(::literal),
            compassError = (parts.walk as? PickCompass.Refused)?.error?.toString(),
            trail = trail,
            reached = reached,
            result = parts.result?.let { resolution(it, parts.resultType) },
            fastPathKey = FastPath.keyOf(selector.effectiveFilters)?.toString(),
            fastPathEnabled = settings.fastPath,
            refreshOnHit = settings.refreshOnHit,
            refreshes = if (settings.refreshOnHit) target?.let(refresh) else null,
            normalize = selector.normalization?.toString(),
        )
    }

    private fun <N : UiNode<N>> all(roots: List<N>, filter: Filter, maxNodes: Int): List<N> {
        val found = ArrayList<N>()
        var visited = 0
        for (root in roots) {
            val budget = maxNodes - visited
            if (budget <= 0) break
            UiNodes.walk(root, maxNodes = budget) { node ->
                visited += 1
                if (filter.filter(node)) found += node
                true
            }
        }
        return found
    }

    /** A step as the compass writes it. zh-CN: 罗盘写法的一步. */
    internal fun label(step: CompassStep): String = when (step) {
        is CompassStep.Up -> "p${step.levels}"
        is CompassStep.Children -> "c" + step.indices.joinToString(">")
        is CompassStep.Sibling -> when (step.mode) {
            CompassStep.Sibling.Mode.ABSOLUTE -> "s${step.value}"
            CompassStep.Sibling.Mode.FORWARD -> "s>${step.value}"
            CompassStep.Sibling.Mode.BACKWARD -> "s<${step.value}"
        }
        is CompassStep.Clickable -> "k${step.maxLevels}"
    }

    private fun resolution(result: PickResult, given: Any?): PickExplanation.Resolution {
        val shown = if (given == null) "${literal(PickResultType.WIDGET)} (default)" else literal(given)
        return when (result) {
            PickResult.Empty -> PickExplanation.Resolution(shown, "empty", "nothing, null without calling the callback")
            PickResult.Nothing -> PickExplanation.Resolution(shown, "nothing", "no result, null")
            is PickResult.Symbol -> PickExplanation.Resolution(shown, "symbol", symbol(result.type))
            is PickResult.ArrayForm -> PickExplanation.Resolution(shown, "arrayForm", "no method of that name, the array form: " + symbol(result.type))
            is PickResult.Call -> PickExplanation.Resolution(shown, "method", method(result, given))
            is PickResult.Refused -> PickExplanation.Resolution(shown, "refused", "refused, ${result.error}")
        }
    }

    private fun symbol(type: PickResultType): String {
        val name = literal(PickResultType.spellings(type).firstOrNull() ?: "")
        val what = when (type) {
            PickResultType.Widget -> "the node"
            PickResultType.WidgetCollection -> "a collection of the nodes found"
            PickResultType.Widgets -> "an array of the nodes found"
            PickResultType.Content -> "the content of the node"
            PickResultType.Contents -> "an array of the contents of the nodes found"
            PickResultType.Point -> "the centre point of the node"
            PickResultType.Points -> "an array of the centre points of the nodes found"
            PickResultType.Selector -> "the selector itself"
            PickResultType.Existence -> "whether anything is found"
            else -> type.toString()
        }
        return "symbol $name, $what"
    }

    private fun method(call: PickResult.Call, given: Any?): String = buildString {
        val method = call.method.method
        append("method ").append(method.declaringClass.simpleName).append('.').append(NodeMethodTable.signature(method))
        append(": ").append(method.returnType.simpleName)
        val converted = call.method.arguments
        if (converted.isNotEmpty()) {
            val arguments = (PickResultType.of(given) as? PickResultType.Invocation)?.arguments.orEmpty()
            append(", arguments ").append(arguments.joinToString(", ", "[", "]") { literal(it) })
            append(" -> ").append(converted.joinToString(", ", "[", "]") { "$it (${it?.javaClass?.simpleName ?: "null"})" })
        }
        append(", called on the node the compass reaches (explain calls nothing)")
    }

    /** [value] as a script literal: strings quoted, lists bracketed. zh-CN: [value] 的脚本字面量形式: 字符串加引号, 列表加方括号. */
    private fun literal(value: Any?): String = when (value) {
        null -> "null"
        is CharSequence -> JsLiteral.quote(value.toString())
        is List<*> -> value.joinToString(", ", "[", "]") { literal(it) }
        is Double -> if (value % 1.0 == 0.0 && !value.isInfinite()) value.toLong().toString() else value.toString()
        else -> value.toString()
    }

}
