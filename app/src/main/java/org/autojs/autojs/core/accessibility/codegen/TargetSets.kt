package org.autojs.autojs.core.accessibility.codegen

import org.autojs.autojs.core.automator.UiNode
import java.util.ArrayDeque
import java.util.Collections

/** A collection proposal, still subject to the user's choice and subsequent plan verification.
 * zh-CN: 待用户选择的集合建议, 后续仍须验证具体定位计划. */
class TargetSet<N : UiNode<N>> internal constructor(
    val kind: Kind,
    nodes: List<N>,
    val description: Description,
) {
    enum class Kind { FULL_ID_CLASS, REPEATED_CONTAINER, SAME_TEXT }

    val nodes: List<N> = Collections.unmodifiableList(ArrayList(nodes))
    val count: Int get() = nodes.size

    /** Message keys and arguments let the dialog supply localized resources without Android here.
     * zh-CN: 消息键与参数由对话框映射到本地化资源, 核心不依赖 Android. */
    class Description internal constructor(val key: Kind, arguments: List<String>) {
        val arguments: List<String> = Collections.unmodifiableList(ArrayList(arguments))

        /** Diagnostic text only; the UI uses [key] and [arguments]. zh-CN: 仅供诊断, 界面使用键与参数. */
        fun renderChinese(): String = when (key) {
            Kind.FULL_ID_CLASS -> "整个活动窗口中 id 与类型相同的控件, 共 ${arguments[0]} 个"
            Kind.REPEATED_CONTAINER -> "最近重复容器中位置与类型相同的控件, 共 ${arguments[0]} 个"
            Kind.SAME_TEXT -> "整个活动窗口中文本相同的控件, 共 ${arguments[0]} 个"
        }
    }
}

/**
 * At most three distinct suggestions in stable preference order. Structure compares the full
 * ordered subtree (class/id/shape), ignoring content and transient state. Interned bottom-up
 * signatures avoid recursion and quadratic signature strings on deep captured trees.
 * zh-CN: 按固定优先级给出至多三个不同集合. 结构比较完整有序子树的类型, id 与形状, 忽略内容与瞬时状态;
 * 自底向上的签名驻留避免深树递归和长度平方增长的签名字符串.
 */
object TargetSets {
    fun <N : UiNode<N>> suggest(root: N, target: N): List<TargetSet<N>> {
        val tree = Tree(root)
        val targetIndex = tree.indices[target] ?: return emptyList()
        val suggestions = ArrayList<TargetSet<N>>(3)
        val seen = HashSet<Set<N>>()

        fun add(kind: TargetSet.Kind, nodes: List<N>) {
            if (nodes.size < 2 || !seen.add(nodes.toSet())) return
            suggestions += TargetSet(kind, nodes, TargetSet.Description(kind, listOf(nodes.size.toString())))
        }

        val fullId = target.id()
        if (!fullId.isNullOrEmpty()) {
            add(TargetSet.Kind.FULL_ID_CLASS, tree.nodes.filter { it.id() == fullId && it.className() == target.className() })
        }
        add(TargetSet.Kind.REPEATED_CONTAINER, tree.repeatedTargets(targetIndex))
        val text = target.text()
        if (text.isNotEmpty()) {
            add(TargetSet.Kind.SAME_TEXT, tree.nodes.filter { it.text() == text })
        }
        return Collections.unmodifiableList(suggestions)
    }

    private data class Signature(val className: String?, val fullId: String?, val children: List<Int>)
    private data class Pending<N>(val node: N, val parent: Int, val childIndex: Int)

    private class Tree<N : UiNode<N>>(root: N) {
        val nodes = ArrayList<N>()
        val indices = HashMap<N, Int>()
        private val parents = ArrayList<Int>()
        private val childIndices = ArrayList<Int>()
        private val children = ArrayList<MutableList<Int>>()
        private val signatures: IntArray

        init {
            val pending = ArrayDeque<Pending<N>>()
            pending.add(Pending(root, -1, -1))
            while (pending.isNotEmpty()) {
                val (node, parent, childIndex) = pending.removeLast()
                // InspectorNode creates fresh wrappers; equality represents the captured identity.
                // zh-CN: InspectorNode 每次产生新包装对象, 相等性表示被捕获节点的身份.
                if (indices.containsKey(node)) continue
                val index = nodes.size
                indices[node] = index
                nodes += node
                parents += parent
                childIndices += childIndex
                children.add(ArrayList())
                if (parent >= 0) children[parent].add(index)
                for (i in node.childCount() - 1 downTo 0) {
                    node.child(i)?.let { pending.add(Pending(it, index, i)) }
                }
            }
            signatures = IntArray(nodes.size)
            val intern = HashMap<Signature, Int>()
            for (i in nodes.indices.reversed()) {
                val node = nodes[i]
                val signature = Signature(node.className(), node.id(), children[i].map { signatures[it] })
                signatures[i] = intern.getOrPut(signature) { intern.size }
            }
        }

        fun repeatedTargets(targetIndex: Int): List<N> {
            val target = nodes[targetIndex]
            var item = targetIndex
            // Reversed child positions from the current item down to the selected target.
            // zh-CN: 当前容器项至所选节点的逆序子位置路径.
            val reversedPath = ArrayList<Int>()
            while (parents[item] >= 0) {
                val container = parents[item]
                val peers = children[container].filter { signatures[it] == signatures[item] }
                if (peers.size >= 2) {
                    return peers.mapNotNull { peer ->
                        var current: N? = nodes[peer]
                        for (i in reversedPath.indices.reversed()) current = current?.child(reversedPath[i])
                        current?.takeIf { it.className() == target.className() && it.id() == target.id() }
                    }
                }
                reversedPath += childIndices[item]
                item = container
            }
            return emptyList()
        }
    }
}
