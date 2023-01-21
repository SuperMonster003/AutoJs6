package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by SuperMonster003 on Jun 10, 2022.
 */
class StringListContainsFilter internal constructor(private val mContains: String, private val mKeysGetter: KeysGetter) : Filter {

    override fun filter(node: UiObject) = mKeysGetter.getKeys(node).any { it?.contains(mContains) ?: false }

    override fun toString() = "${mKeysGetter}Contains(\"$mContains\")"

}
