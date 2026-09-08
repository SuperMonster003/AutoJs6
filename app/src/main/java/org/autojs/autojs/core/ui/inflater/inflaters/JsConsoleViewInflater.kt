package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsConsoleView
import org.autojs.autojs.runtime.ScriptRuntime

/**
 * Created by SuperMonster003 on May 20, 2023.
 * Modified by SuperMonster003 as of Sep 8, 2026.
 */
open class JsConsoleViewInflater(private val scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : ConsoleViewInflater<JsConsoleView>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in JsConsoleView> = object : ViewCreator<JsConsoleView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsConsoleView {
            // Bound to the runtime console by default; the "global" attribute rebinds afterwards.
            // zh-CN: 默认绑定运行时控制台; "global" 属性随后会重新绑定.
            return JsConsoleView(context).also { it.setConsole(scriptRuntime.console) }
        }
    }

}
