package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsGridView

/**
 * Created by Stardust on Mar 30, 2018.
 * Transformed by SuperMonster003 on May 20, 2023.
 */
class JsGridViewInflater<V : JsGridView>(resourceParser: ResourceParser) : JsListViewInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<JsGridView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsGridView {
            return JsGridView(context)
        }
    }

}