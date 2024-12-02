package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.console.ConsoleView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.runtime.ScriptRuntime

open class ConsoleViewInflater<V: ConsoleView>(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser): FrameLayoutInflater<V>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<ConsoleView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): ConsoleView {
            return ConsoleView(context)
        }
    }

}
