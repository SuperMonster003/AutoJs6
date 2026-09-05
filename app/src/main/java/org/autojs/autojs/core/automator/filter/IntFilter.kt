package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiNode

/**
 * Created by Stardust on Nov 5, 2017.
 * Modified by SuperMonster003 as of Nov 19, 2022.
 */
class IntFilter(private val mIntProperty: IntProperty, private val mValue: Int) : Filter {

    interface IntProperty {
        operator fun get(o: UiNode<*>): Int
    }

    override fun filter(node: UiNode<*>): Boolean = mIntProperty[node] == mValue

    override fun toString() = "$mIntProperty($mValue)"

    companion object {

        val DEPTH = object : IntProperty {
            override fun get(o: UiNode<*>): Int = o.depth()
            override fun toString() = "depth"
        }

        val ROW = object : IntProperty {
            override fun get(o: UiNode<*>): Int = o.row()
            override fun toString() = "row"
        }

        val ROW_COUNT = object : IntProperty {
            override fun get(o: UiNode<*>): Int = o.rowCount()
            override fun toString() = "rowCount"
        }

        val ROW_SPAN = object : IntProperty {
            override fun get(o: UiNode<*>): Int = o.rowSpan()
            override fun toString() = "rowSpan"
        }

        val COLUMN = object : IntProperty {
            override fun get(o: UiNode<*>): Int = o.column()
            override fun toString() = "column"
        }

        val COLUMN_COUNT = object : IntProperty {
            override fun get(o: UiNode<*>): Int = o.columnCount()
            override fun toString() = "columnCount"
        }

        val COLUMN_SPAN = object : IntProperty {
            override fun get(o: UiNode<*>): Int = o.columnSpan()
            override fun toString() = "columnSpan"
        }

        val INDEX_IN_PARENT = object : IntProperty {
            override fun get(o: UiNode<*>): Int = o.indexInParent()
            override fun toString() = "indexInParent"
        }

        val CHILD_COUNT = object : IntProperty {
            override fun get(o: UiNode<*>): Int = o.childCount()
            override fun toString() = "childCount"
        }

        val DRAWING_ORDER = object : IntProperty {
            override fun get(o: UiNode<*>): Int = o.drawingOrder()
            override fun toString() = "drawingOrder"
        }

    }

}
