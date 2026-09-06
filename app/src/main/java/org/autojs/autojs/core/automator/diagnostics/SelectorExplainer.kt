package org.autojs.autojs.core.automator.diagnostics

import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.filter.Filter
import org.autojs.autojs.core.automator.toolkit.UiNodes

/**
 * Why a selector matches what it matches: for every filter, how many nodes it accepts on its own
 * and how many are still left once the filters before it are applied too, the nodes that pass
 * everything, and (when nothing does) the nodes that passed every filter up to the one that left
 * nothing. Rendered as text by [render]. Produced by [SelectorExplainer.explain] for
 * `auto.explain(selector)`.
 * zh-CN: 选择器为何匹配到它所匹配的: 对每个过滤器, 它单独接受多少节点, 以及叠加前面的过滤器后还剩多少; 通过全部过滤器的节点;
 * 以及 (一个都不通过时) 通过了直到 "使结果清空的那个" 之前所有过滤器的节点. [render] 渲染为文本. 由 [SelectorExplainer.explain]
 * 产出, 供 `auto.explain(selector)`.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class SelectorExplanation<N : UiNode<N>> internal constructor(
    /** The selector as a string. zh-CN: 选择器的字符串形式. */
    val selector: String,
    /** How many roots were walked. zh-CN: 遍历了多少个根节点. */
    val roots: Int,
    /** How many nodes were walked. zh-CN: 遍历了多少个节点. */
    val nodes: Int,
    /** Whether the node budget cut the walk short. zh-CN: 节点预算是否截断了遍历. */
    val truncated: Boolean,
    /** One step per filter, in selector order. zh-CN: 每个过滤器一步, 按选择器顺序. */
    val steps: List<Step>,
    /** The nodes passing every filter, at most the `keep` of the walk. zh-CN: 通过全部过滤器的节点, 至多为遍历的 `keep` 个. */
    val matches: List<N>,
    /** With a failing step past the first: the nodes passing every step before it, at most `keep`. zh-CN: 失败步骤不是第一步时: 通过它之前每一步的节点, 至多 `keep` 个. */
    val nearMisses: List<N>,
) {

    /**
     * A filter's tally: [matched] nodes accepted by the filter alone, [cumulative] nodes left
     * after this and every filter before it.
     * zh-CN: 一个过滤器的计数: [matched] 为该过滤器单独接受的节点数, [cumulative] 为叠加它与之前所有过滤器后剩下的节点数.
     */
    class Step(val index: Int, val filter: String, val matched: Int, val cumulative: Int)

    /** How many nodes pass the whole selector (every walked node when there is no filter). zh-CN: 通过整个选择器的节点数 (没有过滤器时为遍历的全部节点). */
    val count: Int = steps.lastOrNull()?.cumulative ?: nodes

    val found: Boolean get() = count > 0

    /** The 0-based index of the first step after which nothing is left, or `-1` when the selector matches. zh-CN: 首个使结果清空的步骤的下标 (从 0 起), 选择器有匹配时为 `-1`. */
    val failingIndex: Int = steps.indexOfFirst { it.cumulative == 0 }

    val failingFilter: String? = steps.getOrNull(failingIndex)?.filter

    /** The text report; [describe] renders a node on one line. zh-CN: 文本报告; [describe] 把一个节点渲染成一行. */
    fun render(describe: (N) -> String = { NodeDump.line(it) }): String = buildString {
        append("selector ").append(selector).append(": ")
        append(
            when {
                steps.isEmpty() -> "$count node(s), no filter"
                count == 0 -> "no match"
                else -> "$count match(es)"
            }
        )
        append(" (").append(nodes).append(" node(s) in ").append(roots).append(" root(s)")
        if (truncated) append(", walk truncated")
        append(')')
        if (steps.isNotEmpty()) {
            val width = steps.maxOf { it.filter.length }.coerceAtMost(MAX_FILTER_WIDTH)
            for (step in steps) {
                append("\n  ").append(step.index + 1).append(". ").append(step.filter.padEnd(width))
                append("  matched ").append(step.matched).append(", cumulative ").append(step.cumulative)
                if (step.index == failingIndex) append("  <- nothing left")
            }
        }
        if (matches.isNotEmpty()) {
            append("\nmatches")
            if (count > matches.size) append(" (first ").append(matches.size).append(" of ").append(count).append(')')
            append(':')
            for (node in matches) append("\n  - ").append(describe(node))
        }
        if (failingIndex >= 1 && nearMisses.isNotEmpty()) {
            append("\nnear misses (pass ").append(if (failingIndex == 1) "step 1" else "steps 1..$failingIndex")
            append(", fail step ").append(failingIndex + 1).append("):")
            for (node in nearMisses) append("\n  - ").append(describe(node))
        }
    }

    override fun toString(): String = render()

    companion object {

        private const val MAX_FILTER_WIDTH = 48

    }

}

/**
 * Walks the roots once, evaluating every filter of a selector on every node, and tallies the
 * filters alone and cumulatively; see [SelectorExplanation]. The walk never recycles a node and
 * stops at [UiNodes.DEFAULT_MAX_NODES] nodes by default.
 * zh-CN: 遍历各根一次, 对每个节点求值选择器的每个过滤器, 分别按单独与叠加计数; 见 [SelectorExplanation]. 遍历从不回收节点,
 * 默认在 [UiNodes.DEFAULT_MAX_NODES] 个节点处停止.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
object SelectorExplainer {

    /** How many matches and near misses an explanation keeps. zh-CN: 一份解释保留多少个匹配与 "差一点" 的节点. */
    const val DEFAULT_KEEP = 5

    fun <N : UiNode<N>> explain(
        roots: List<N>,
        filters: List<Filter>,
        selector: String = describe(filters),
        maxNodes: Int = UiNodes.DEFAULT_MAX_NODES,
        keep: Int = DEFAULT_KEEP,
    ): SelectorExplanation<N> {
        require(maxNodes > 0) { "maxNodes must be > 0, got $maxNodes" }
        require(keep >= 0) { "keep must be >= 0, got $keep" }
        val matched = IntArray(filters.size)
        val cumulative = IntArray(filters.size)
        val matches = ArrayList<N>()
        val missesAt = List(filters.size) { ArrayList<N>() }
        var visited = 0
        var truncated = false
        for (root in roots) {
            val budget = maxNodes - visited
            if (budget <= 0) {
                truncated = true
                break
            }
            val completed = UiNodes.walk(root, maxNodes = budget) { node ->
                visited += 1
                var alive = true
                for (i in filters.indices) {
                    val ok = filters[i].filter(node)
                    if (ok) matched[i] += 1
                    if (alive) {
                        if (ok) {
                            cumulative[i] += 1
                        } else {
                            alive = false
                            if (missesAt[i].size < keep) missesAt[i] += node
                        }
                    }
                }
                if (alive && matches.size < keep) matches += node
                true
            }
            if (!completed) truncated = true
        }
        val steps = filters.indices.map { SelectorExplanation.Step(it, filters[it].toString(), matched[it], cumulative[it]) }
        val failing = steps.indexOfFirst { it.cumulative == 0 }
        val nearMisses = if (failing >= 1) missesAt[failing].toList() else emptyList()
        return SelectorExplanation(selector, roots.size, visited, truncated, steps, matches, nearMisses)
    }

    /** The selector string of [filters], `selector()` when empty. zh-CN: [filters] 的选择器字符串, 为空时为 `selector()`. */
    fun describe(filters: List<Filter>): String = filters.joinToString(".").ifEmpty { "selector()" }

}
