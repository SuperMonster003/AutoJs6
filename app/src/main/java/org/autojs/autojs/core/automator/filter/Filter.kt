package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by Stardust on 2017/3/9.
 */
interface Filter {

    fun filter(node: UiObject): Boolean

}
