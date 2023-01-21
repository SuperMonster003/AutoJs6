package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by Stardust on 2017/3/9.
 * Modified by SuperMonster003 as of Nov 19, 2022.
 */
object ClassNameFilter {

    private val CLASS_NAME_GETTER = object : KeyGetter {

        override fun getKey(nodeInfo: UiObject) = nodeInfo.className?.toString()

        override fun toString() = "className"

    }

    @Suppress("CovariantEquals")
    fun equals(text: String) = when (text.contains(".")) {
        true -> text
        else -> "android.widget.$text"
    }.let { StringEqualsFilter(it, CLASS_NAME_GETTER) }

    fun contains(str: String) = StringContainsFilter(str, CLASS_NAME_GETTER)

    fun startsWith(prefix: String) = when (prefix.contains(".")) {
        true -> prefix
        else -> "android.widget.$prefix"
    }.let { StringStartsWithFilter(it, CLASS_NAME_GETTER) }

    fun endsWith(suffix: String) = StringEndsWithFilter(suffix, CLASS_NAME_GETTER)

    fun matches(regex: String) = StringMatchesFilter(regex, CLASS_NAME_GETTER)

    fun match(regex: String) = StringMatchFilter(regex, CLASS_NAME_GETTER)

}
