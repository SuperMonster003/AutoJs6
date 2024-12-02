package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.runtime.ScriptRuntime

open class ToolbarInflater<V: Toolbar>(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser): ViewGroupInflater<V>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<Toolbar> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): Toolbar {
            return Toolbar(context)
        }
    }

}
