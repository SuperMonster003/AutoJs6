package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.ScrollView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class ScrollViewInflater<V : ScrollView>(resourceParser: ResourceParser) : FrameLayoutInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<ScrollView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): ScrollView {
            return ScrollView(context)
        }
    }

}
