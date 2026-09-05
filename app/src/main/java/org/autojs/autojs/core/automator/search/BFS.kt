package org.autojs.autojs.core.automator.search

import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.release
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.filter.Filter
import java.util.*
import kotlin.collections.ArrayList

/**
 * Modified by SuperMonster003 as of Sep 5, 2026.
 */
object BFS : SearchAlgorithm {

    override fun search(root: UiObject, filter: Filter, limit: Int, lifecycle: NodeLifecycle): ArrayList<UiObject> {
        val result = ArrayList<UiObject>()
        if (limit <= 0) return result
        val queue = ArrayDeque<UiObject>()
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
            for (i in 0 until top.childCount) {
                queue.add(top.child(i) ?: continue)
            }
            if (!isTarget && top !== root) {
                lifecycle.release(top)
            }
        }
        return result
    }

    private fun recycleRemaining(queue: Queue<UiObject>, root: UiObject, lifecycle: NodeLifecycle) {
        while (!queue.isEmpty()) {
            val node = queue.poll() ?: continue
            if (node !== root) {
                lifecycle.release(node)
            }
        }
    }

}
