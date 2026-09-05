package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiNode

/**
 * Created by SuperMonster003 on Nov 29, 2022.
 */
class MinIntFilter(private val mIntProperty: IntProperty, private val mValue: Int) : Filter {

    interface IntProperty {
        operator fun get(o: UiNode<*>): Int
    }

    override fun filter(node: UiNode<*>): Boolean = mIntProperty[node] >= mValue

    override fun toString() = "$mIntProperty($mValue)"

    companion object {

        val CHILD_COUNT = object : IntProperty {
            override fun get(o: UiNode<*>): Int = o.childCount()
            override fun toString() = "minChildCount"
        }

    }

}
