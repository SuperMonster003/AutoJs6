package org.autojs.autojs.core.accessibility.codegen

import org.autojs.autojs.core.automator.UiNode
import java.util.Collections

/**
 * Canonical identities and captured parent/child positions of a detached tree. UiNode equality
 * identifies the captured node, not its wrapper: InspectorNode creates new wrappers on navigation.
 * No node is recycled or modified. The captured root is a boundary even when it has a parent.
 * zh-CN: 独立捕获树的规范节点身份与父子位置. UiNode 相等性标识被捕获节点, 而非包装对象;
 * InspectorNode 导航时会创建新包装. 不回收或修改节点, 捕获根即为边界, 即使其存在父节点.
 */
class NodeTree<N : UiNode<N>>(
    val root: N,
    cancellation: CancellationSignal = CancellationSignal(),
) {
    private class Entry<N>(val node: N, val parent: N?, val childIndex: Int, val depth: Int) {
        val children = ArrayList<N?>()
    }

    private val entries = HashMap<N, Entry<N>>()
    val nodes: List<N>
    private val positions: Map<N, Int>
    private val subtreeEnds: IntArray

    init {
        val ordered = ArrayList<N>()
        val pending = ArrayDeque<Entry<N>>()
        pending.addLast(Entry(root, null, -1, 0))
        while (pending.isNotEmpty()) {
            cancellation.throwIfCancelled()
            val entry = pending.removeLast()
            // Captured layouts are trees; ignoring repeated identities also prevents cycles here.
            // zh-CN: 捕获布局为树; 忽略重复身份也可避免此处的循环.
            if (entries.putIfAbsent(entry.node, entry) != null) continue
            ordered += entry.node
            for (i in 0 until entry.node.childCount()) {
                cancellation.throwIfCancelled()
                entry.children += entry.node.child(i)
            }
            for (i in entry.children.lastIndex downTo 0) {
                entry.children[i]?.let { pending.addLast(Entry(it, entry.node, i, entry.depth + 1)) }
            }
        }
        nodes = Collections.unmodifiableList(ordered)
        positions = HashMap<N, Int>(ordered.size * 2).also { map -> ordered.forEachIndexed { index, node -> map[node] = index } }
        subtreeEnds = IntArray(ordered.size) { it }
        for (index in ordered.indices.reversed()) {
            cancellation.throwIfCancelled()
            val parent = entries.getValue(ordered[index]).parent ?: continue
            val parentIndex = positions.getValue(parent)
            if (subtreeEnds[index] > subtreeEnds[parentIndex]) subtreeEnds[parentIndex] = subtreeEnds[index]
        }
    }

    /** The pre-order position of [node], or -1 outside the capture. zh-CN: [node] 的先序位置, 不在捕获中为 -1. */
    fun position(node: N): Int = positions[node] ?: -1

    /** The last pre-order position inside the subtree of [node]. zh-CN: [node] 子树内的最后一个先序位置. */
    fun subtreeEnd(node: N): Int = positions[node]?.let { subtreeEnds[it] } ?: -1

    /** Whether [node] is [ancestor] or one of its descendants. zh-CN: [node] 是否为 [ancestor] 自身或其后代. */
    fun isWithin(node: N, ancestor: N): Boolean {
        val index = positions[node] ?: return false
        val start = positions[ancestor] ?: return false
        return index in start..subtreeEnds[start]
    }

    fun canonical(node: N): N? = entries[node]?.node

    fun contains(node: N): Boolean = entries.containsKey(node)

    fun parent(node: N): N? = entries[node]?.parent?.let(::canonical)

    fun child(node: N, index: Int): N? {
        val children = entries[node]?.children ?: return null
        val actual = if (index < 0) index + children.size else index
        return children.getOrNull(actual)?.let(::canonical)
    }

    fun children(node: N): List<N> = entries[node]?.children?.mapNotNull { it?.let(::canonical) }.orEmpty()

    fun childIndex(node: N): Int = entries[node]?.childIndex ?: -1

    fun depth(node: N): Int = entries[node]?.depth ?: -1
}
