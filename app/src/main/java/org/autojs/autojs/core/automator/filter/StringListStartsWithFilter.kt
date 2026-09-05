package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiNode

/**
 * Created by SuperMonster003 on Jun 10, 2022.
 */
class StringListStartsWithFilter(private val mPrefix: String, private val mKeysGetter: KeysGetter) : Filter {

    override fun filter(node: UiNode<*>) = mKeysGetter.getKeys(node).any { it?.startsWith(mPrefix) ?: false }

    override fun toString() = "${mKeysGetter}StartsWith(\"$mPrefix\")"

}
