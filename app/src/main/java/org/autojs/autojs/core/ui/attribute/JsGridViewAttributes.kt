package org.autojs.autojs.core.ui.attribute

import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsGridView

class JsGridViewAttributes(resourceParser: ResourceParser, view: View) : JsListViewAttributes(resourceParser, view) {

    override val view = super.view as JsGridView

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttr("orientation") { (view.layoutManager as? GridLayoutManager)?.orientation = LinearLayoutAttributes.ORIENTATIONS[it] }
        registerAttr("spanCount") { (view.layoutManager as? GridLayoutManager)?.spanCount = it.toInt() }
        registerAttrs(arrayOf("isUsingSpansToEstimateScrollbarDimensions", "usingSpansToEstimateScrollbarDimensions")) { (view.layoutManager as? GridLayoutManager)?.isUsingSpansToEstimateScrollbarDimensions = it.toBoolean() }
    }
}