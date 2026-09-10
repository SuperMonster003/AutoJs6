package org.autojs.autojs.core.automator.bridge

import org.autojs.autojs.core.automator.UiNode

/**
 * Ceilings of the Node Bridge's `accessibility.dump` / `explain` / `screenshot` methods (MCP
 * roadmap P1.3). `McpServerContract` mirrors the dump and screenshot values; a JVM test keeps the
 * two in step.
 * zh-CN: Node Bridge 的 `accessibility.dump` / `explain` / `screenshot` 方法的上限 (MCP 路线图 P1.3). `McpServerContract`
 * 镜像其中的 dump 与截图值, 由 JVM 测试保持一致.
 *
 * Created by SuperMonster003 on Sep 8, 2026.
 */
object BridgeAccessibilityLimits {

    /** Most nodes one dump carries; also the default. zh-CN: 一次快照最多携带的节点数; 也是默认值. */
    const val MAX_NODES = 400

    /** Deepest level below a root a dump descends to; also the default. zh-CN: 快照向下走到的最深层级 (相对根节点); 也是默认值. */
    const val MAX_DEPTH = 32

    /** Longest text form, in UTF-8 bytes; longer output is cut and flagged. zh-CN: 文本形式的最大 UTF-8 字节数; 更长的输出被截断并标记. */
    const val MAX_TEXT_BYTES = 256 * 1024

    /** Most matches and near misses one explanation keeps. zh-CN: 一份解释最多保留的匹配与 "差一点" 节点数. */
    const val MAX_EXPLAIN_KEEP = 20

    /** Most nodes one explanation walks. zh-CN: 一份解释最多遍历的节点数. */
    const val MAX_EXPLAIN_NODES = 2000

    /** Largest encoded screenshot the bridge hands out. zh-CN: bridge 交出的编码后截图的最大字节数. */
    const val MAX_SCREENSHOT_ENCODED_BYTES = 4 * 1024 * 1024

}

/**
 * Options of [BridgeNodeDump.dump]: [maxDepth] is the deepest level below each root that is
 * still walked (0 dumps the roots alone), [maxNodes] the node budget over all roots, and
 * [visibleOnly] skips invisible nodes together with their subtrees.
 * zh-CN: [BridgeNodeDump.dump] 的选项: [maxDepth] 为每个根节点之下仍会遍历的最深层级 (0 只快照根节点), [maxNodes] 为所有根节点合计的
 * 节点预算, [visibleOnly] 跳过不可见节点及其子树.
 */
class BridgeDumpOptions(
    val maxDepth: Int = BridgeAccessibilityLimits.MAX_DEPTH,
    val maxNodes: Int = BridgeAccessibilityLimits.MAX_NODES,
    val visibleOnly: Boolean = false,
) {

    init {
        require(maxDepth in 0..BridgeAccessibilityLimits.MAX_DEPTH) {
            "maxDepth must be within 0..${BridgeAccessibilityLimits.MAX_DEPTH}, got $maxDepth"
        }
        require(maxNodes in 1..BridgeAccessibilityLimits.MAX_NODES) {
            "maxNodes must be within 1..${BridgeAccessibilityLimits.MAX_NODES}, got $maxNodes"
        }
    }

}

/**
 * One node of a dump: the frozen seven-field [node] plus where it sits ([root] is the index of
 * the root it belongs to, [depth] its level below that root, [index] its index among its
 * siblings, -1 for a root), what the walk could not show ([childCount] counts all children,
 * even those the depth limit or the node budget left out), and the state flags the MCP compact
 * tree lists next to the node (MCP roadmap P3.2): [scrollable], [checkable], [checked],
 * [editable], [focused], [selected] and [longClickable].
 * zh-CN: 快照中的一个节点: 冻结的七字段 [node] 加上它的位置 ([root] 为所属根节点的下标, [depth] 为它在该根之下的层级, [index] 为它在
 * 兄弟节点中的下标, 根节点为 -1), 遍历未能展示的部分 ([childCount] 计入全部子节点, 包括被深度限制或节点预算略去的), 以及 MCP 紧凑节点树
 * 在节点旁列出的状态标记 (MCP 路线图 P3.2): [scrollable], [checkable], [checked], [editable], [focused], [selected] 与 [longClickable].
 */
class BridgeDumpEntry(
    val node: BridgeNode,
    val root: Int,
    val depth: Int,
    val index: Int,
    val childCount: Int,
    val visible: Boolean,
    val scrollable: Boolean = false,
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val editable: Boolean = false,
    val focused: Boolean = false,
    val selected: Boolean = false,
    val longClickable: Boolean = false,
)

/** The outcome of [BridgeNodeDump.dump]. zh-CN: [BridgeNodeDump.dump] 的结果. */
class BridgeDumpResult(
    /** Entries in pre-order, root by root. zh-CN: 先序排列的条目, 逐根节点. */
    val entries: List<BridgeDumpEntry>,
    /** How many roots were offered. zh-CN: 给出了多少个根节点. */
    val roots: Int,
    /** Whether the node budget cut the walk short. zh-CN: 节点预算是否截断了遍历. */
    val truncated: Boolean,
) {

    val nodes: Int get() = entries.size

}

/**
 * The compact, Android-free walk behind the Node Bridge's `accessibility.dump` in its JSON form
 * (MCP roadmap P1.3, appendix B): pre-order over each root, bounded by [BridgeDumpOptions], every
 * entry a [BridgeNode] plus its position. The text and XML forms stay with `NodeDump`. Nodes are
 * never recycled here, as in `NodeDump`.
 * zh-CN: Node Bridge 的 `accessibility.dump` JSON 形式背后的紧凑, Android-free 走查 (MCP 路线图 P1.3, 附录 B): 对每个根节点先序遍历,
 * 受 [BridgeDumpOptions] 约束, 每个条目为 [BridgeNode] 加位置. 文本与 XML 形式仍由 `NodeDump` 负责. 与 `NodeDump` 一样, 这里不回收节点.
 */
object BridgeNodeDump {

    fun <N : UiNode<N>> dump(roots: List<N>, options: BridgeDumpOptions = BridgeDumpOptions()): BridgeDumpResult {
        val entries = ArrayList<BridgeDumpEntry>()
        var truncated = false
        for ((rootIndex, root) in roots.withIndex()) {
            if (!walk(root, rootIndex, 0, options, entries)) {
                truncated = true
                break
            }
        }
        return BridgeDumpResult(entries, roots.size, truncated)
    }

    /** Returns `false` once the node budget is exhausted. zh-CN: 节点预算耗尽时返回 `false`. */
    private fun <N : UiNode<N>> walk(node: N, rootIndex: Int, level: Int, options: BridgeDumpOptions, entries: MutableList<BridgeDumpEntry>): Boolean {
        val visible = node.visibleToUser()
        if (options.visibleOnly && !visible) return true
        if (entries.size >= options.maxNodes) return false
        entries += BridgeDumpEntry(
            node = BridgeNode.of(node),
            root = rootIndex,
            depth = level,
            index = if (level == 0) -1 else node.indexInParent(),
            childCount = node.childCount(),
            visible = visible,
            scrollable = node.scrollable(),
            checkable = node.checkable(),
            checked = node.checked(),
            editable = node.editable(),
            focused = node.focused(),
            selected = node.selected(),
            longClickable = node.longClickable(),
        )
        if (level >= options.maxDepth) return true
        for (i in 0 until node.childCount()) {
            val child = node.child(i) ?: continue
            if (!walk(child, rootIndex, level + 1, options, entries)) return false
        }
        return true
    }

}
