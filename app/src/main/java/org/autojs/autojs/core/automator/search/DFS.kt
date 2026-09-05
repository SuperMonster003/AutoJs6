package org.autojs.autojs.core.automator.search

import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.filter.Filter
import java.util.*

import kotlin.collections.ArrayList

/**
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Sep 5, 2026.
 */
object DFS : SearchAlgorithm {

    override fun <N : UiNode<N>> search(root: N, filter: Filter, limit: Int, lifecycle: NodeLifecycle): ArrayList<N> {
        val result = ArrayList<N>()
        if (limit <= 0) return result
        val stack = LinkedList<N>()
        stack.push(root)
        while (stack.isNotEmpty()) {
            val parent = stack.pop()
            for (i in parent.childCount() - 1 downTo 0) {
                val child = parent.child(i) ?: continue
                stack.push(child)
            }
            if (filter.filter(parent)) {
                result.add(parent)
                if (result.size >= limit) {
                    recycleRemaining(stack, root, lifecycle)
                    break
                }
            } else {
                if (parent !== root) {
                    lifecycle.release { parent.recycle() }
                }
            }
        }
        return result
    }

    private fun <N : UiNode<N>> recycleRemaining(stack: LinkedList<N>, root: N, lifecycle: NodeLifecycle) {
        while (stack.isNotEmpty()) {
            val node = stack.pop()
            if (node !== root) {
                lifecycle.release { node.recycle() }
            }
        }
    }

}
