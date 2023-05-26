package org.autojs.autojs.core.ui.inflater.inflaters

import org.autojs.autojs.core.console.JsConsoleView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

/**
 * Created by SuperMonster003 on May 20, 2023.
 */
class JsConsoleViewInflater(resourceParser: ResourceParser) : ConsoleViewInflater<JsConsoleView>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsConsoleView> = ViewCreator { context, _ -> JsConsoleView(context) }

}