package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by SuperMonster003 on Jun 10, 2022.
 */
class StringListEndsWithFilter(private val mSuffix: String, private val mKeysGetter: KeysGetter) : Filter {

    override fun filter(node: UiObject) = mKeysGetter.getKeys(node).any { it?.endsWith(mSuffix) ?: false }

    override fun toString() = "${mKeysGetter}EndsWith(\"$mSuffix\")"

}
