package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiNode

/**
 * A [Comparison] of one of the integer tree and collection properties of a node (`depth(1)`,
 * `childCount(2)`, `minChildCount(1)`, `maxChildCount(3)`, ...).
 * zh-CN: 对节点整数型树属性与集合属性之一的 [Comparison] (`depth(1)`, `childCount(2)`, `minChildCount(1)`, `maxChildCount(3)`, ...).
 *
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Sep 6, 2026.
 */
class IntFilter(val property: Property, val comparison: Comparison) : Filter {

    enum class Property(private val label: String, private val read: (UiNode<*>) -> Int) {
        DEPTH("depth", { it.depth() }),
        ROW("row", { it.row() }),
        ROW_COUNT("rowCount", { it.rowCount() }),
        ROW_SPAN("rowSpan", { it.rowSpan() }),
        COLUMN("column", { it.column() }),
        COLUMN_COUNT("columnCount", { it.columnCount() }),
        COLUMN_SPAN("columnSpan", { it.columnSpan() }),
        INDEX_IN_PARENT("indexInParent", { it.indexInParent() }),
        CHILD_COUNT("childCount", { it.childCount() }),
        DRAWING_ORDER("drawingOrder", { it.drawingOrder() });

        val capitalized: String = label.replaceFirstChar { it.uppercase() }

        operator fun get(node: UiNode<*>): Int = read(node)

        override fun toString() = label
    }

    sealed class Comparison(val prefix: String, val value: Int) {

        abstract fun test(actual: Int): Boolean

        class Equals(value: Int) : Comparison("", value) {
            override fun test(actual: Int) = actual == value
        }

        class Min(min: Int) : Comparison("min", min) {
            override fun test(actual: Int) = actual >= value
        }

        class Max(max: Int) : Comparison("max", max) {
            override fun test(actual: Int) = actual <= value
        }

    }

    constructor(property: Property, value: Int) : this(property, Comparison.Equals(value))

    /** The builder name this filter prints as, e.g. `minChildCount`. zh-CN: 本过滤器打印时使用的 builder 名, 如 `minChildCount`. */
    val name: String
        get() = if (comparison.prefix.isEmpty()) property.toString() else comparison.prefix + property.capitalized

    override fun filter(node: UiNode<*>) = comparison.test(property[node])

    override fun toString() = "$name(${comparison.value})"

    override val cost get() = Filter.COST_PROPERTY

}
