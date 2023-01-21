package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by Stardust on 2017/3/9.
 */
interface KeyGetter {

    fun getKey(nodeInfo: UiObject): String?

}
