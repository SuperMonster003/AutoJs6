package org.autojs.autojs.core.ui.inflater.inflaters

import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsViewPager

class JsViewPagerInflater(resourceParser: ResourceParser) : ViewPagerInflater<JsViewPager>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsViewPager> = ViewCreator { context, _ -> JsViewPager(context) }

}