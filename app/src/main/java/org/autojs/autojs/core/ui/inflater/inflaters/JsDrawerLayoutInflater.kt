package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsDrawerLayout
import org.autojs.autojs.runtime.ScriptRuntime

/**
 * Created by SuperMonster003 on May 22, 2023.
 */
class JsDrawerLayoutInflater(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : DrawerLayoutInflater<JsDrawerLayout>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in JsDrawerLayout> = object : ViewCreator<JsDrawerLayout> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsDrawerLayout {
            return JsDrawerLayout(context)
        }
    }

}