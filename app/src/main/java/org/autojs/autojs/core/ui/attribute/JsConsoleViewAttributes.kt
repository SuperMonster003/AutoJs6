package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsConsoleView
import org.autojs.autojs.runtime.ScriptRuntime

class JsConsoleViewAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ConsoleViewAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsConsoleView

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        // <console global="true"> (or the bare <console global>) shows the global console.
        // zh-CN: <console global="true"> (或裸属性写法 <console global>) 显示全局控制台.
        registerAttr("global") { value ->
            val console = when (value.toBoolean()) {
                true -> AutoJs.instance.globalConsole
                else -> scriptRuntime.console
            }
            if (view.console !== console) view.setConsole(console)
        }
    }

}
