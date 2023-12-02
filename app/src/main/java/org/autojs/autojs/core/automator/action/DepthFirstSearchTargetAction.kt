package org.autojs.autojs.core.automator.action

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by Stardust on Jan 27, 2017.
 */
class DepthFirstSearchTargetAction(action: Int, filter: Filter) : SearchTargetAction(action, filter) {

    private val mAble = Able.ABLE_MAP.get(action)

    override fun searchTarget(node: UiObject?): UiObject? {
        node ?: return null
        if (mAble.isAble(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.child(i) ?: continue
            searchTarget(child)?.let { return it }
        }
        return null
    }

}
