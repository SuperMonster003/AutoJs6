package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.accessibility.UiSelector.Companion.ID_IDENTIFIER
import org.autojs.autojs.core.automator.UiObject

/**
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Nov 19, 2022.
 */
object IdFilter {

    private val ID_GETTER = object : KeyGetter {

        override fun getKey(nodeInfo: UiObject) = nodeInfo.viewIdResourceName

        override fun toString() = "id"

    }

    @Suppress("CovariantEquals")
    fun equals(str: String) = when (str.contains(ID_IDENTIFIER)) {
        true -> StringEqualsFilter(str, ID_GETTER)
        else -> object : Filter {
            override fun filter(node: UiObject) = node.id()?.let { id ->
                when (id.contains(ID_IDENTIFIER)) {
                    true -> id.split(ID_IDENTIFIER).last() == str
                    else -> id == str
                }
            } ?: false

            override fun toString() = "id(\"$str\")"
        }
    }

    fun startsWith(prefix: String) = when (prefix.contains(ID_IDENTIFIER)) {
        true -> StringStartsWithFilter(prefix, ID_GETTER)
        else -> object : Filter {
            override fun filter(node: UiObject) = node.id()?.let { id ->
                when (id.contains(ID_IDENTIFIER)) {
                    true -> id.split(ID_IDENTIFIER).last().startsWith(prefix)
                    else -> id.startsWith(prefix)
                }
            } ?: false

            override fun toString() = "idStartsWith(\"$prefix\")"
        }
    }

    fun endsWith(suffix: String) = StringEndsWithFilter(suffix, ID_GETTER)

    fun contains(contains: String) = StringContainsFilter(contains, ID_GETTER)

    fun matches(regex: String) = StringMatchesFilter(regex, ID_GETTER)

    fun match(regex: String) = StringMatchFilter(regex, ID_GETTER)

}
