package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.UiObject
import java.util.*

class Selector : Filter {

    private val mFilters = LinkedList<Filter>()

    override fun filter(node: UiObject) = mFilters.all { it.filter(node) }

    private fun getFilter() = mFilters

    fun add(filter: Filter) = mFilters.add(filter)

    fun append(uiSelector: UiSelector) = mFilters.addAll(uiSelector.selector.getFilter())

    override fun toString() = mFilters.joinToString(".").ifEmpty { Selector::class.java.toString() }

}