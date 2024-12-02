package org.autojs.autojs.core.automator.action

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by Stardust on Jan 27, 2017.
 */
class SearchUpTargetAction(action: Int, filter: Filter) : SearchTargetAction(action, filter) {

    private val mAble = Able.ABLE_MAP.get(action)

    override fun searchTarget(node: UiObject?): UiObject? {
        var temp = node
        var i = 0
        while (temp != null && !mAble.isAble(temp)) {
            if (i++ > LOOP_MAX) return null
            temp = temp.parent()
        }
        return temp
    }

    override fun toString() = "SearchUpTargetAction{mAble=$mAble, ${super.toString()}}"

    companion object {

        private const val LOOP_MAX = 20

    }
}
