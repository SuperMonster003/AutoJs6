package org.autojs.autojs.core.automator.action

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by Stardust on 2017/1/27.
 */
abstract class SearchTargetAction(val action: Int, filter: Filter) : FilterAction(filter) {

    override fun perform(nodes: List<UiObject>): Boolean {
        var performed = false
        for (node in nodes) {
            val targetNode = searchTarget(node)
            if (targetNode != null && performAction(targetNode)) {
                performed = true
            }
        }
        return performed
    }

    protected open fun performAction(node: UiObject): Boolean {
        return node.performAction(action)
    }

    open fun searchTarget(node: UiObject?): UiObject? {
        return node
    }

    override fun toString(): String {
        return "SearchTargetAction{" +
                "mAction=" + action + ", " +
                super.toString() +
                "}"
    }
}
