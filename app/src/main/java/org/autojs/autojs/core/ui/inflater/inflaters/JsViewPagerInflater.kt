package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsViewPager

class JsViewPagerInflater(resourceParser: ResourceParser) : ViewPagerInflater<JsViewPager>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsViewPager> = object : ViewCreator<JsViewPager> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsViewPager {
            return JsViewPager(context)
        }
    }

}