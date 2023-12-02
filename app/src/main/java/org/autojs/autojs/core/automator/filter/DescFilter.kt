package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Nov 19, 2022.
 */
object DescFilter {

    private val DESC_GETTER = object : KeyGetter {

        override fun getKey(nodeInfo: UiObject) = nodeInfo.contentDescription?.toString()

        override fun toString() = "desc"

    }

    fun equals(text: String) = StringEqualsFilter(text, DESC_GETTER)

    fun contains(str: String) = StringContainsFilter(str, DESC_GETTER)

    fun startsWith(prefix: String) = StringStartsWithFilter(prefix, DESC_GETTER)

    fun endsWith(suffix: String) = StringEndsWithFilter(suffix, DESC_GETTER)

    fun matches(regex: String) = StringMatchesFilter(regex, DESC_GETTER)

    fun match(regex: String) = StringMatchFilter(regex, DESC_GETTER)

}
