package org.autojs.autojs.ui.floating.layoutinspector.search

import org.autojs.autojs.core.accessibility.NodeInfo

/**
 * What the inspector views need to know about the current search (roadmap D3 / D4), in terms of
 * the [NodeInfo] they render: whether a node stays in the pruned hierarchy, whether it is a match
 * to draw and highlight, and where the keyword sits inside a row's text. Implemented by
 * [InspectorSearchSession]; Java-friendly so `LayoutHierarchyView` can consume it.
 * zh-CN: 分析视图对当前搜索需要了解的信息 (Roadmap D3 / D4), 以它们所渲染的 [NodeInfo] 表达: 节点是否留在裁剪后的层次树中,
 * 是否为需绘制并高亮的匹配项, 以及关键词在行文本中的位置. 由 [InspectorSearchSession] 实现; 对 Java 友好, 供 `LayoutHierarchyView` 使用.
 *
 * Created by SuperMonster003 on Oct 2, 2026.
 */
interface InspectorMatchView {

    /** True when there is no effective query: every node is kept and matched. zh-CN: 没有有效查询时为 true: 所有节点均保留且匹配. */
    val isUnfiltered: Boolean

    fun isKept(node: NodeInfo): Boolean

    fun isMatched(node: NodeInfo): Boolean

    /** The part of [text] to highlight for the keyword, or null when none applies. zh-CN: [text] 中应为关键词高亮的区间, 无则为 null. */
    fun highlightRange(text: CharSequence): IntRange?

}
