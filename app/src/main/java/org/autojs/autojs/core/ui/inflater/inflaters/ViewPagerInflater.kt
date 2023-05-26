package org.autojs.autojs.core.ui.inflater.inflaters

import androidx.viewpager.widget.ViewPager
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class ViewPagerInflater<V : ViewPager>(resourceParser: ResourceParser) : ViewGroupInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> ViewPager(context) }

}
