package org.autojs.autojs.core.automator.action

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by Stardust on Jan 27, 2017.
 */
abstract class SimpleAction {

    @Volatile
    var isValid = true
    @Volatile
    var result = false

    abstract fun perform(root: UiObject): Boolean

}
