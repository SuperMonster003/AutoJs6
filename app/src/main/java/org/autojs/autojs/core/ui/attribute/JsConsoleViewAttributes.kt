package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.widget.JsConsoleView
import org.autojs.autojs.core.ui.inflater.ResourceParser

class JsConsoleViewAttributes(resourceParser: ResourceParser, view: View) : ConsoleViewAttributes(resourceParser, view) {

    override val view = super.view as JsConsoleView

}