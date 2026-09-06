package org.autojs.autojs.core.automator.search

import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.default
import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.filter.Filter

/**
 * A lazy tree search (roadmap P5.3, `findIterator()`): the nodes under [roots] passing [filter],
 * produced one at a time as the walk proceeds, in the order of [DFS] (or [BFS] with
 * [breadthFirst]). A consumer that stops early never visits the rest of the tree; [close] releases
 * what the walk has not reached. Nodes the filter rejects are released through [lifecycle] as the
 * walk passes them; the roots never are. Not thread-safe: one consumer.
 * zh-CN: 惰性树搜索 (Roadmap P5.3, `findIterator()`): [roots] 之下通过 [filter] 的节点, 随遍历逐个产出, 顺序同 [DFS] (或
 * [breadthFirst] 时同 [BFS]). 提前停止的消费方不会访问树的其余部分; [close] 释放尚未到达的节点. 被过滤器拒绝的节点在经过时经
 * [lifecycle] 释放; 根节点从不释放. 非线程安全: 只有一个消费方.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class LazySearch<N : UiNode<N>>(
    roots: List<N>,
    private val filter: Filter,
    private val breadthFirst: Boolean = false,
    private val lifecycle: NodeLifecycle = NodeLifecycle.default,
) : Iterator<N>, Iterable<N>, AutoCloseable {

    private val roots: List<N> = roots.toList()

    private val pending = ArrayDeque<N>()

    private var next: N? = null

    /** Nodes examined so far. zh-CN: 至今检查过的节点数. */
    var visited: Int = 0
        private set

    /** Nodes produced so far. zh-CN: 至今产出的节点数. */
    var produced: Int = 0
        private set

    /** Whether the walk is over (exhausted or closed). zh-CN: 遍历是否已结束 (穷尽或关闭). */
    var isClosed: Boolean = false
        private set

    init {
        // Depth-first pops from the end, so the roots go in reversed; breadth-first takes from the front.
        // zh-CN: 深度优先从末尾取, 因此根节点倒序放入; 广度优先从头部取.
        if (breadthFirst) pending.addAll(this.roots) else this.roots.asReversed().forEach { pending.addLast(it) }
    }

    override fun iterator(): Iterator<N> = this

    override fun hasNext(): Boolean {
        if (next == null && !isClosed) advance()
        return next != null
    }

    override fun next(): N {
        if (!hasNext()) throw NoSuchElementException("The search is exhausted")
        val node = next!!
        next = null
        produced += 1
        return node
    }

    private fun advance() {
        while (pending.isNotEmpty()) {
            val node = if (breadthFirst) pending.removeFirst() else pending.removeLast()
            visited += 1
            if (breadthFirst) {
                for (i in 0 until node.childCount()) node.child(i)?.let { pending.addLast(it) }
            } else {
                for (i in node.childCount() - 1 downTo 0) node.child(i)?.let { pending.addLast(it) }
            }
            if (filter.filter(node)) {
                next = node
                return
            }
            releaseUnlessRoot(node)
        }
        isClosed = true
    }

    /** The remaining matches, exhausting the walk. zh-CN: 其余全部匹配, 走完遍历. */
    fun drain(): List<N> {
        val rest = ArrayList<N>()
        while (hasNext()) rest += next()
        return rest
    }

    /** Ends the walk, releasing the nodes it has not reached. zh-CN: 结束遍历, 释放尚未到达的节点. */
    override fun close() {
        while (pending.isNotEmpty()) releaseUnlessRoot(pending.removeLast())
        next = null
        isClosed = true
    }

    private fun releaseUnlessRoot(node: N) {
        if (roots.none { it === node }) lifecycle.release { node.recycle() }
    }

}
