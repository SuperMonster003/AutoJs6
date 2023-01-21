package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by Stardust on 2017/11/5.
 * Modified by SuperMonster003 as of Nov 19, 2022.
 */
class IntFilter(private val mIntProperty: IntProperty, private val mValue: Int) : Filter {

    interface IntProperty {
        operator fun get(o: UiObject): Int
    }

    override fun filter(node: UiObject): Boolean = mIntProperty[node] == mValue

    override fun toString() = "$mIntProperty($mValue)"

    companion object {

        val DEPTH = object : IntProperty {
            override fun get(o: UiObject): Int = o.depth()
            override fun toString() = "depth"
        }

        val ROW = object : IntProperty {
            override fun get(o: UiObject): Int = o.row()
            override fun toString() = "row"
        }

        val ROW_COUNT = object : IntProperty {
            override fun get(o: UiObject): Int = o.rowCount()
            override fun toString() = "rowCount"
        }

        val ROW_SPAN = object : IntProperty {
            override fun get(o: UiObject): Int = o.rowSpan()
            override fun toString() = "rowSpan"
        }

        val COLUMN = object : IntProperty {
            override fun get(o: UiObject): Int = o.column()
            override fun toString() = "column"
        }

        val COLUMN_COUNT = object : IntProperty {
            override fun get(o: UiObject): Int = o.columnCount()
            override fun toString() = "columnCount"
        }

        val COLUMN_SPAN = object : IntProperty {
            override fun get(o: UiObject): Int = o.columnSpan()
            override fun toString() = "columnSpan"
        }

        val INDEX_IN_PARENT = object : IntProperty {
            override fun get(o: UiObject): Int = o.indexInParent()
            override fun toString() = "indexInParent"
        }

        val CHILD_COUNT = object : IntProperty {
            override fun get(o: UiObject): Int = o.childCount()
            override fun toString() = "childCount"
        }

        val DRAWING_ORDER = object : IntProperty {
            override fun get(o: UiObject): Int = o.drawingOrder()
            override fun toString() = "drawingOrder"
        }

    }

}
