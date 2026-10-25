package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiNode

/**
 * Created by SuperMonster003 on Nov 19, 2022.
 */
class ActionFilter(val actions: Array<out Any>) : Filter {

    override fun filter(node: UiNode<*>) = node.hasAction(*actions)

    override val cost get() = Filter.COST_ACTIONS

    override fun toString() = when (actions.size) {
        1 -> "action(${actions.joinToString()})"
        else /* including 0. */ -> "action([${actions.joinToString(",")}])"
    }

}
