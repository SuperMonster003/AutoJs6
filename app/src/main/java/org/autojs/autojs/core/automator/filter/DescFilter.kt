package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject
import org.mozilla.javascript.regexp.NativeRegExp

/**
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Dec 27, 2025.
 */
object DescFilter {

    private val DESC_GETTER = object : KeyGetter {

        override fun getKey(nodeInfo: UiObject) = nodeInfo.contentDescription?.toString()

        override fun toString() = "desc"

    }

    @Suppress("CovariantEquals")
    fun equals(s: String) = StringEqualsFilter(s, DESC_GETTER)

    @Suppress("CovariantEquals")
    fun equals(regex: NativeRegExp) = StringEqualsFilter(regex, DESC_GETTER)

    fun contains(s: String) = StringContainsFilter(s, DESC_GETTER)
    fun contains(regex: NativeRegExp) = StringContainsFilter(regex, DESC_GETTER)

    fun startsWith(prefix: String) = StringStartsWithFilter(prefix, DESC_GETTER)

    fun endsWith(suffix: String) = StringEndsWithFilter(suffix, DESC_GETTER)

    fun matches(s: String) = StringMatchesFilter(s, DESC_GETTER)
    fun matches(regex: NativeRegExp) = StringMatchesFilter(regex, DESC_GETTER)

    fun match(s: String) = StringMatchFilter(s, DESC_GETTER)
    fun match(regex: NativeRegExp) = StringMatchFilter(regex, DESC_GETTER)

}
