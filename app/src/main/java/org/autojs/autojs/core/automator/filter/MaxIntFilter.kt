package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by SuperMonster003 on Nov 29, 2022.
 */
class MaxIntFilter(private val mIntProperty: IntProperty, private val mValue: Int) : Filter {

    interface IntProperty {
        operator fun get(o: UiObject): Int
    }

    override fun filter(node: UiObject): Boolean = mIntProperty[node] <= mValue

    override fun toString() = "$mIntProperty($mValue)"

    companion object {

        val CHILD_COUNT = object : IntProperty {
            override fun get(o: UiObject): Int = o.childCount()
            override fun toString() = "maxChildCount"
        }

    }

}
