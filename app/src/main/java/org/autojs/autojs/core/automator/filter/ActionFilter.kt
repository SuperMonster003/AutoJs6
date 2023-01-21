package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.pref.Language
import org.autojs.autojs.util.App

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
