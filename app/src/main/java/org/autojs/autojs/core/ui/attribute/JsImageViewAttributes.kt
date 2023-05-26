package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsImageView

open class JsImageViewAttributes(resourceParser: ResourceParser, view: View) : RoundedImageViewAttributes(resourceParser, view) {

    override val view = super.view as JsImageView

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttrs(arrayOf("isCircle", "circle")) { view.isCircle = true }
    }

}