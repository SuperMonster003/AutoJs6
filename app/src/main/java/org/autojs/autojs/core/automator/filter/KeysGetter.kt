package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by SuperMonster003 on Jun 10, 2022.
 */
 
interface KeysGetter {

    fun getKeys(nodeInfo: UiObject): List<CharSequence?>
    
}
