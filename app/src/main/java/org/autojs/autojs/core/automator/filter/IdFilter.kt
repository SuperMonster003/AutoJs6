package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.accessibility.UiSelector.Companion.ID_IDENTIFIER
import org.autojs.autojs.core.automator.UiObject
import org.mozilla.javascript.regexp.NativeRegExp

/**
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Dec 27, 2025.
 */
object IdFilter {

    private val ID_GETTER = object : KeyGetter {

        override fun getKey(nodeInfo: UiObject) = nodeInfo.viewIdResourceName

        override fun toString() = "id"

    }

    @Suppress("CovariantEquals")
    fun equals(s: String) = when (s.contains(ID_IDENTIFIER)) {
        true -> StringEqualsFilter(s, ID_GETTER)
        else -> object : Filter {
            override fun filter(node: UiObject) = node.id()?.let { id ->
                when (id.contains(ID_IDENTIFIER)) {
                    true -> id.split(ID_IDENTIFIER).last() == s
                    else -> id == s
                }
            } ?: false

            override fun toString() = "${ID_GETTER}(\"$s\")"
        }
    }

    @Suppress("CovariantEquals")
    fun equals(regex: NativeRegExp): Filter {
        return object : StringMatchesFilter(regex, ID_GETTER) {
            override fun toString(): String {
                val literal = JsRegexUtils.formatAsJsRegexLiteral(regex.toString())
                return "${ID_GETTER}($literal)"
            }
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

            override fun toString() = "${ID_GETTER}StartsWith(\"$prefix\")"
        }
    }

    fun endsWith(suffix: String) = StringEndsWithFilter(suffix, ID_GETTER)

    fun contains(s: String) = StringContainsFilter(s, ID_GETTER)
    fun contains(regex: NativeRegExp) = StringContainsFilter(regex, ID_GETTER)

    fun matches(s: String) = StringMatchesFilter(s, ID_GETTER)
    fun matches(regex: NativeRegExp) = StringMatchesFilter(regex, ID_GETTER)

    fun match(s: String) = StringMatchFilter(s, ID_GETTER)
    fun match(regex: NativeRegExp) = StringMatchFilter(regex, ID_GETTER)

}
