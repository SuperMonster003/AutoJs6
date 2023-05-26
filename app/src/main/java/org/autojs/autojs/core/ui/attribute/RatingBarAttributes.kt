package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.RatingBar
import org.autojs.autojs.core.ui.inflater.ResourceParser

open class RatingBarAttributes<V : RatingBar>(resourceParser: ResourceParser, view: View) : AbsSeekBarAttributes<V>(resourceParser, view) {

    override val view = super.view as RatingBar

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttr("isIndicator") { view.setIsIndicator(it.toBoolean()) }
        registerAttr("numStars") { view.numStars = it.toInt() }
        registerAttr("rating") { view.rating = it.toFloat() }
        registerAttr("stepSize") { view.stepSize = it.toFloat() }
    }

}
