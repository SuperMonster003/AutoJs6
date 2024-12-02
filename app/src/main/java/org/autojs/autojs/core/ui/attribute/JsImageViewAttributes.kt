package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsImageView
import org.autojs.autojs.runtime.ScriptRuntime

open class JsImageViewAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : RoundedImageViewAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsImageView

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttrs(arrayOf("isCircle", "circle")) { view.isCircle = true }
    }

}