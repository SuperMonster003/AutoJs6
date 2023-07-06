package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.ViewFlipper
import org.autojs.autojs.core.ui.inflater.ResourceParser

open class ViewFlipperAttributes(resourceParser: ResourceParser, view: View) : ViewAnimatorAttributes(resourceParser, view) {

    override val view = super.view as ViewFlipper

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttrs(arrayOf("isAutoStart", "autoStart")) { view.isAutoStart = it.toBoolean() }
        registerAttr("flipInterval") { view.flipInterval = it.toInt() }
    }

}
