package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class ToolbarInflater<V: Toolbar>(resourceParser: ResourceParser): ViewGroupInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<Toolbar> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): Toolbar {
            return Toolbar(context)
        }
    }

}
