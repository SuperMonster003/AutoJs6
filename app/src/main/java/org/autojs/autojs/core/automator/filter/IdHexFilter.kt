package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by SuperMonster003 on Oct 17, 2022.
 */
object IdHexFilter {

    private val TEXT_GETTER = object : KeyGetter {

        override fun getKey(nodeInfo: UiObject) = nodeInfo.idHex()

        override fun toString() = "idHex"

    }

    fun equals(text: String) = StringEqualsFilter(text, TEXT_GETTER)

}
