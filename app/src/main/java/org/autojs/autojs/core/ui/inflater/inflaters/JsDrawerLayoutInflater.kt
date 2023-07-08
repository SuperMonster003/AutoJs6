package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsDrawerLayout

/**
 * Created by SuperMonster003 on May 22, 2023.
 */
class JsDrawerLayoutInflater(resourceParser: ResourceParser) : DrawerLayoutInflater<JsDrawerLayout>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsDrawerLayout> = object : ViewCreator<JsDrawerLayout> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsDrawerLayout {
            return JsDrawerLayout(context)
        }
    }

}