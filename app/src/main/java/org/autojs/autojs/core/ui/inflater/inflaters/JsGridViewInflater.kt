package org.autojs.autojs.core.ui.inflater.inflaters

import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsGridView

/**
 * Created by Stardust on 2018/3/30.
 * Transformed by SuperMonster003 on May 20, 2023.
 */
class JsGridViewInflater<V : JsGridView>(resourceParser: ResourceParser) : JsListViewInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> JsGridView(context) }

}