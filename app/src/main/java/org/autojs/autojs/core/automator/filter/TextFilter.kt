package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by Stardust on 2017/3/9.
 * Modified by SuperMonster003 as of Nov 19, 2022.
 */
object TextFilter {

    private val TEXT_GETTER = object : KeyGetter {

        override fun getKey(nodeInfo: UiObject) = nodeInfo.text?.toString()

        override fun toString() = "text"

    }

    fun equals(text: String) = StringEqualsFilter(text, TEXT_GETTER)

    fun contains(str: String) = StringContainsFilter(str, TEXT_GETTER)

    fun startsWith(prefix: String) = StringStartsWithFilter(prefix, TEXT_GETTER)

    fun endsWith(suffix: String) = StringEndsWithFilter(suffix, TEXT_GETTER)

    fun matches(regex: String) = StringMatchesFilter(regex, TEXT_GETTER)

    fun match(regex: String) = StringMatchFilter(regex, TEXT_GETTER)

}
