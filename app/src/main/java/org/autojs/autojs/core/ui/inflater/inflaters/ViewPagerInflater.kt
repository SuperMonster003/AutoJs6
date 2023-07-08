package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import androidx.viewpager.widget.ViewPager
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class ViewPagerInflater<V : ViewPager>(resourceParser: ResourceParser) : ViewGroupInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<ViewPager> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): ViewPager {
            return ViewPager(context)
        }
    }

}
