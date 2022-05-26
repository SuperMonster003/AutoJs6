package com.stardust.automator.simple_action


import com.stardust.automator.UiObject

/**
 * Created by Stardust on 2017/1/27.
 */
class SearchUpTargetAction(action: Int, filter: Filter) : SearchTargetAction(action, filter) {
    private val mAble: Able = Able.ABLE_MAP.get(action)

    override fun searchTarget(node: UiObject?): UiObject? {
        var o = node
        var i = 0
        while (o != null && !mAble.isAble(o)) {
            i++
            if (i > LOOP_MAX) {
                return null
            }
            o = o.parent()
        }
        return o
    }

    override fun toString(): String {
        return "SearchUpTargetAction{" +
                "mAble=" + mAble + ", " +
                super.toString() +
                '}'.toString()
    }

    companion object {
        private const val LOOP_MAX = 20
    }
}
