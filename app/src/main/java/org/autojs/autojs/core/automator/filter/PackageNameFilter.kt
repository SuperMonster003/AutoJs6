package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject
import org.mozilla.javascript.regexp.NativeRegExp

/**
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Dec 27, 2025.
 */
object PackageNameFilter {

    private val PACKAGE_NAME_GETTER = object : KeyGetter {

        override fun getKey(nodeInfo: UiObject) = nodeInfo.packageName?.toString()

        override fun toString() = "packageName"

    }

    @Suppress("CovariantEquals")
    fun equals(s: String) = StringEqualsFilter(s, PACKAGE_NAME_GETTER)

    @Suppress("CovariantEquals")
    fun equals(regex: NativeRegExp) = StringEqualsFilter(regex, PACKAGE_NAME_GETTER)

    fun contains(s: String) = StringContainsFilter(s, PACKAGE_NAME_GETTER)
    fun contains(regex: NativeRegExp) = StringContainsFilter(regex, PACKAGE_NAME_GETTER)

    fun startsWith(prefix: String) = StringStartsWithFilter(prefix, PACKAGE_NAME_GETTER)

    fun endsWith(suffix: String) = StringEndsWithFilter(suffix, PACKAGE_NAME_GETTER)

    fun matches(s: String) = StringMatchesFilter(s, PACKAGE_NAME_GETTER)
    fun matches(regex: NativeRegExp) = StringMatchesFilter(regex, PACKAGE_NAME_GETTER)

    fun match(s: String) = StringMatchFilter(s, PACKAGE_NAME_GETTER)
    fun match(regex: NativeRegExp) = StringMatchFilter(regex, PACKAGE_NAME_GETTER)

}
