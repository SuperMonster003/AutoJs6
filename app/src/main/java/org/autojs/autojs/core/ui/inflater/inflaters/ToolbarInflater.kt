package org.autojs.autojs.core.ui.inflater.inflaters

import androidx.appcompat.widget.Toolbar
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class ToolbarInflater<V: Toolbar>(resourceParser: ResourceParser): ViewGroupInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> Toolbar(context) }

}
