package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.widget.JsConsoleView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

class JsConsoleViewAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ConsoleViewAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsConsoleView

}