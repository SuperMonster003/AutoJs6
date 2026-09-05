package org.autojs.autojs.core.automator.search

import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.filter.Filter
import java.util.*
import kotlin.collections.ArrayList

/**
 * Modified by SuperMonster003 as of Sep 5, 2026.
 */
object BFS : SearchAlgorithm {

    override fun <N : UiNode<N>> search(root: N, filter: Filter, limit: Int, lifecycle: NodeLifecycle): ArrayList<N> {
        val result = ArrayList<N>()
        if (limit <= 0) return result
        val queue = ArrayDeque<N>()
        queue.add(root)
        while (!queue.isEmpty()) {
            val top = queue.poll()!!
            val isTarget = filter.filter(top)
            if (isTarget) {
                result.add(top)
                if (result.size >= limit) {
                    recycleRemaining(queue, root, lifecycle)
                    return result
                }
            }
            for (i in 0 until top.childCount()) {
                queue.add(top.child(i) ?: continue)
            }
            if (!isTarget && top !== root) {
                lifecycle.release { top.recycle() }
            }
        }
        return result
    }

    private fun <N : UiNode<N>> recycleRemaining(queue: Queue<N>, root: N, lifecycle: NodeLifecycle) {
        while (!queue.isEmpty()) {
            val node = queue.poll() ?: continue
            if (node !== root) {
                lifecycle.release { node.recycle() }
            }
        }
    }

}
