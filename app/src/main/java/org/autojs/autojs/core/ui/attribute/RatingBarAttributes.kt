package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.RatingBar
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

open class RatingBarAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : AbsSeekBarAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as RatingBar

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttr("isIndicator") { view.setIsIndicator(it.toBoolean()) }
        registerAttr("numStars") { view.numStars = it.toInt() }
        registerAttr("rating") { view.rating = it.toFloat() }
        registerAttr("stepSize") { view.stepSize = it.toFloat() }
    }

}
