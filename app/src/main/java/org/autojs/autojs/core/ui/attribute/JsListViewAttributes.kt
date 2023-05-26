package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsListView
import org.autojs.autojs.groundwork.WrapContentLinearLayoutManager

open class JsListViewAttributes(resourceParser: ResourceParser, view: View) : RecyclerViewAttributes(resourceParser, view) {

    override val view = super.view as JsListView

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttr("orientation") { view.layoutManager = WrapContentLinearLayoutManager(view.context, LinearLayoutAttributes.ORIENTATIONS[it], false) }
    }

}