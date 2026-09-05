package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.UiNode
import java.util.*

open class Selector : Filter {

    val filters = LinkedList<Filter>()

    override fun filter(node: UiNode<*>) = filters.all { it.filter(node) }

    fun add(filter: Filter) = filters.add(filter)

    fun append(uiSelector: UiSelector) = filters.addAll(uiSelector.selector.filters)

    override fun toString() = filters.joinToString(".").ifEmpty { "selector()" }

}