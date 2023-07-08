package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class RecyclerViewInflater<V : RecyclerView>(resourceParser: ResourceParser) : ViewGroupInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<RecyclerView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): RecyclerView {
            return RecyclerView(context)
        }
    }

}
