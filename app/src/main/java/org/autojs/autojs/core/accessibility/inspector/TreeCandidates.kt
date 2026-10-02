package org.autojs.autojs.core.accessibility.inspector

import org.autojs.autojs.core.automator.UiNode

/**
 * What the current tree offers for the filter dialog's pickers (roadmap P3.2, D16): the distinct
 * package names, the distinct action names and the simplified class names with their counts, all
 * taken from the tree itself so that offline layouts work the same as live captures.
 * zh-CN: 当前树能为筛选对话框的选择器提供的候选 (Roadmap P3.2, D16): 不重复的包名, 不重复的动作名, 以及带计数的简化类名,
 * 全部取自树本身, 因此离线布局与实时捕获行为一致.
 *
 * Created by SuperMonster003 on Oct 2, 2026.
 */
class TreeCandidates private constructor(
    /** Sorted. zh-CN: 已排序. */
    val packages: List<String>,
    /** Sorted. zh-CN: 已排序. */
    val actions: List<String>,
    /** Simplified class name to node count, most frequent first, ties by name. zh-CN: 简化类名到节点数, 最多的在前, 同数按名称. */
    val classNames: List<Pair<String, Int>>,
    val nodeCount: Int,
) {

    companion object {

        fun <N : UiNode<N>> collect(root: N): TreeCandidates {
            val packages = sortedSetOf<String>()
            val actions = sortedSetOf<String>()
            val classNames = HashMap<String, Int>()
            val nodes = InspectorNodes.preOrder(root)
            for (node in nodes) {
                node.packageName()?.takeIf { it.isNotBlank() }?.let(packages::add)
                actions.addAll(node.actionNames())
                node.className()?.takeIf { it.isNotBlank() }?.let { classNames.merge(ClassPreset.simplify(it), 1, Int::plus) }
            }
            val sortedClassNames = classNames.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .map { it.key to it.value }
            return TreeCandidates(packages.toList(), actions.toList(), sortedClassNames, nodes.size)
        }

    }

}
