package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject
import org.mozilla.javascript.regexp.NativeRegExp

/**
 * Created by SuperMonster003 on Jun 10, 2022.
 * Modified by SuperMonster003 as of Dec 27, 2025.
 */
object ContentFilter {

    private val CONTENT_GETTER = object : KeysGetter {

        override fun getKeys(nodeInfo: UiObject) = listOf(nodeInfo.contentDescription, nodeInfo.text)

        override fun toString() = "content"

    }

    @Suppress("CovariantEquals")
    fun equals(s: String) = StringListEqualsFilter(s, CONTENT_GETTER)

    @Suppress("CovariantEquals")
    fun equals(regex: NativeRegExp) = StringListEqualsFilter(regex, CONTENT_GETTER)

    fun contains(s: String) = StringListContainsFilter(s, CONTENT_GETTER)
    fun contains(regex: NativeRegExp) = StringListContainsFilter(regex, CONTENT_GETTER)

    fun startsWith(prefix: String) = StringListStartsWithFilter(prefix, CONTENT_GETTER)

    fun endsWith(suffix: String) = StringListEndsWithFilter(suffix, CONTENT_GETTER)

    fun matches(s: String) = StringListMatchesFilter(s, CONTENT_GETTER)
    fun matches(regex: NativeRegExp) = StringListMatchesFilter(regex, CONTENT_GETTER)

    fun match(s: String) = StringListMatchFilter(s, CONTENT_GETTER)
    fun match(regex: NativeRegExp) = StringListMatchFilter(regex, CONTENT_GETTER)

}
