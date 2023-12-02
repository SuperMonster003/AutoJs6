package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Nov 19, 2022.
 */
class StringStartsWithFilter(private val mPrefix: String, private val mKeyGetter: KeyGetter) : Filter {

    override fun filter(node: UiObject) = mKeyGetter.getKey(node)?.startsWith(mPrefix) ?: false

    override fun toString() = "${mKeyGetter}StartsWith(\"$mPrefix\")"

}
