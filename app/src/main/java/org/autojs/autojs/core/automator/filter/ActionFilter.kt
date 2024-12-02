package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by SuperMonster003 on Nov 19, 2022.
 */
class ActionFilter(private val actions: Array<out Any>) : Filter {

    override fun filter(node: UiObject) = node.hasAction(*actions)

    override fun toString() = when (actions.size) {
        1 -> "action(${actions.joinToString()})"
        else /* including 0. */ -> "action([${actions.joinToString(",")}])"
    }

}
