package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by SuperMonster003 on Jun 10, 2022.
 */
object ContentFilter {

    private val CONTENT_GETTER = object : KeysGetter {

        override fun getKeys(nodeInfo: UiObject) = listOf(nodeInfo.contentDescription, nodeInfo.text)

        override fun toString() = "content"

    }

    fun equals(text: String) = StringListEqualsFilter(text, CONTENT_GETTER)

    fun contains(str: String) = StringListContainsFilter(str, CONTENT_GETTER)

    fun startsWith(prefix: String) = StringListStartsWithFilter(prefix, CONTENT_GETTER)

    fun endsWith(suffix: String) = StringListEndsWithFilter(suffix, CONTENT_GETTER)

    fun matches(regex: String) = StringListMatchesFilter(regex, CONTENT_GETTER)

    fun match(regex: String) = StringListMatchFilter(regex, CONTENT_GETTER)

}
