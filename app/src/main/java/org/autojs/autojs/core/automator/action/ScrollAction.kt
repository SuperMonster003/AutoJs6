package org.autojs.autojs.core.automator.action

import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.default
import org.autojs.autojs.core.accessibility.node.release
import org.autojs.autojs.core.accessibility.node.releaseAll
import org.autojs.autojs.core.automator.UiObject

import java.util.ArrayList

/**
 * Created by Stardust on Feb 12, 2017.
 * Modified by SuperMonster003 as of Sep 5, 2026.
 */
class ScrollAction(private val mAction: Int, private val mIndex: Int) : SimpleAction() {

    private val lifecycle = NodeLifecycle.default

    override fun perform(root: UiObject): Boolean {
        val scrollableNodes = findScrollableNodes(root)
        val result = mIndex < scrollableNodes.size && scrollableNodes[mIndex].performAction(mAction)
        lifecycle.releaseAll(scrollableNodes, except = root)
        return result
    }

    private fun findScrollableNodes(root: UiObject?): List<UiObject> {
        val list = ArrayList<UiObject>()
        if (root != null) {
            findScrollableNodes(root, list)
            if (root.isScrollable) {
                list.add(root)
            }
        }
        return list
    }

    private fun findScrollableNodes(node: UiObject?, list: MutableList<UiObject>) {
        if (node == null) {
            return
        }
        for (i in 0 until node.childCount) {
            val child = node.child(i) ?: continue
            findScrollableNodes(child, list)
            if (child.isScrollable) {
                list.add(child)
            } else {
                lifecycle.release(child)
            }
        }
    }
}
