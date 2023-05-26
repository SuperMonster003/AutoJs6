package org.autojs.autojs.core.ui.inflater.inflaters

import android.widget.HorizontalScrollView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class HorizontalScrollViewInflater<V : HorizontalScrollView>(resourceParser: ResourceParser) : FrameLayoutInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> HorizontalScrollView(context) }

}
