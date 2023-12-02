package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Nov 19, 2022.
 */
object PackageNameFilter {

    private val PACKAGE_NAME_GETTER = object : KeyGetter {

        override fun getKey(nodeInfo: UiObject) = nodeInfo.packageName?.toString()

        override fun toString() = "packageName"

    }

    fun equals(text: String) = StringEqualsFilter(text, PACKAGE_NAME_GETTER)

    fun contains(str: String) = StringContainsFilter(str, PACKAGE_NAME_GETTER)

    fun startsWith(prefix: String) = StringStartsWithFilter(prefix, PACKAGE_NAME_GETTER)

    fun endsWith(suffix: String) = StringEndsWithFilter(suffix, PACKAGE_NAME_GETTER)

    fun matches(regex: String) = StringMatchesFilter(regex, PACKAGE_NAME_GETTER)

    fun match(regex: String) = StringMatchFilter(regex, PACKAGE_NAME_GETTER)

}
