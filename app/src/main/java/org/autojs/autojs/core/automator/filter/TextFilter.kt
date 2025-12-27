package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject
import org.mozilla.javascript.regexp.NativeRegExp

/**
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Dec 27, 2025.
 */
object TextFilter {

    private val TEXT_GETTER = object : KeyGetter {

        override fun getKey(nodeInfo: UiObject) = nodeInfo.text?.toString()

        override fun toString() = "text"

    }

    @Suppress("CovariantEquals")
    fun equals(s: String) = StringEqualsFilter(s, TEXT_GETTER)

    @Suppress("CovariantEquals")
    fun equals(regex: NativeRegExp) = StringEqualsFilter(regex, TEXT_GETTER)

    fun contains(s: String) = StringContainsFilter(s, TEXT_GETTER)
    fun contains(regex: NativeRegExp) = StringContainsFilter(regex, TEXT_GETTER)

    fun startsWith(prefix: String) = StringStartsWithFilter(prefix, TEXT_GETTER)

    fun endsWith(suffix: String) = StringEndsWithFilter(suffix, TEXT_GETTER)

    fun matches(s: String) = StringMatchesFilter(s, TEXT_GETTER)
    fun matches(regex: NativeRegExp) = StringMatchesFilter(regex, TEXT_GETTER)

    fun match(s: String) = StringMatchFilter(s, TEXT_GETTER)
    fun match(regex: NativeRegExp) = StringMatchFilter(regex, TEXT_GETTER)

}
