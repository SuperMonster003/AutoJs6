package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class FrameLayoutInflater<V : FrameLayout>(resourceParser: ResourceParser) : ViewGroupInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<FrameLayout> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): FrameLayout {
            return FrameLayout(context)
        }
    }

}
