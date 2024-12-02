package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.ScrollView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.runtime.ScriptRuntime

open class ScrollViewInflater<V : ScrollView>(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : FrameLayoutInflater<V>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<ScrollView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): ScrollView {
            return ScrollView(context)
        }
    }

}
