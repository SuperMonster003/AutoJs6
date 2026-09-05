package org.autojs.autojs.core.automator.search

import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.release
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.filter.Filter
import java.util.*

import kotlin.collections.ArrayList

/**
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Sep 5, 2026.
 */
object DFS : SearchAlgorithm {

    override fun search(root: UiObject, filter: Filter, limit: Int, lifecycle: NodeLifecycle): ArrayList<UiObject> {
        val result = ArrayList<UiObject>()
        if (limit <= 0) return result
        val stack = LinkedList<UiObject>()
        stack.push(root)
        while (stack.isNotEmpty()) {
            val parent = stack.pop()
            for (i in parent.childCount - 1 downTo 0) {
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
                    lifecycle.release(parent)
                }
            }
        }
        return result
    }

    private fun recycleRemaining(stack: LinkedList<UiObject>, root: UiObject, lifecycle: NodeLifecycle) {
        while (stack.isNotEmpty()) {
            val node = stack.pop()
            if (node !== root) {
                lifecycle.release(node)
            }
        }
    }

}
