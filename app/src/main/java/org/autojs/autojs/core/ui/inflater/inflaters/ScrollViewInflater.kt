package org.autojs.autojs.core.ui.inflater.inflaters

import android.widget.ScrollView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class ScrollViewInflater<V : ScrollView>(resourceParser: ResourceParser) : FrameLayoutInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> ScrollView(context) }

}
