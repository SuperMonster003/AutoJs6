package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiNode

/**
 * Created by Stardust on Mar 9, 2017.
 */
interface Filter {

    fun filter(node: UiNode<*>): Boolean

}
