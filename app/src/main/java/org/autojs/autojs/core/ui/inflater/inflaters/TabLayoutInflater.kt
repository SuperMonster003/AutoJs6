package org.autojs.autojs.core.ui.inflater.inflaters

import com.google.android.material.tabs.TabLayout
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class TabLayoutInflater<V: TabLayout>(resourceParser: ResourceParser): HorizontalScrollViewInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> TabLayout(context) }

}
