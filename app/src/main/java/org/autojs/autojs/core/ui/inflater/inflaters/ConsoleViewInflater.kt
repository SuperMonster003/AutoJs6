package org.autojs.autojs.core.ui.inflater.inflaters

import org.autojs.autojs.core.console.ConsoleView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class ConsoleViewInflater<V: ConsoleView>(resourceParser: ResourceParser): FrameLayoutInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> ConsoleView(context) }

}
