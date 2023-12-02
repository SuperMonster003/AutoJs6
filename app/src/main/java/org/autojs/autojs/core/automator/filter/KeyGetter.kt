package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by Stardust on Mar 9, 2017.
 */
interface KeyGetter {

    fun getKey(nodeInfo: UiObject): String?

}
