package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsConsoleView
import org.autojs.autojs.core.ui.widget.JsGlobalConsoleView
import org.autojs.autojs.runtime.ScriptRuntime

/**
 * Created by SuperMonster003 on Sep 8, 2026.
 */
class JsGlobalConsoleViewInflater(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : JsConsoleViewInflater(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in JsConsoleView> = object : ViewCreator<JsConsoleView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsConsoleView {
            return JsGlobalConsoleView(context).also { it.setConsole(AutoJs.instance.globalConsole) }
        }
    }

}
