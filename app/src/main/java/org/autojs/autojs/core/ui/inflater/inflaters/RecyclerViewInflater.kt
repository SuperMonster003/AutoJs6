package org.autojs.autojs.core.ui.inflater.inflaters

import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class RecyclerViewInflater<V : RecyclerView>(resourceParser: ResourceParser) : ViewGroupInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> RecyclerView(context) }

}
