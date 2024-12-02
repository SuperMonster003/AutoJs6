package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.ViewFlipper
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

open class ViewFlipperAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ViewAnimatorAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as ViewFlipper

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttrs(arrayOf("isAutoStart", "autoStart")) { view.isAutoStart = it.toBoolean() }
        registerAttr("flipInterval") { view.flipInterval = it.toInt() }
    }

}
