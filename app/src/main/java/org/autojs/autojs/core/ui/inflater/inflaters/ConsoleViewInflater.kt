package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.console.ConsoleView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class ConsoleViewInflater<V: ConsoleView>(resourceParser: ResourceParser): FrameLayoutInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<ConsoleView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): ConsoleView {
            return ConsoleView(context)
        }
    }

}
