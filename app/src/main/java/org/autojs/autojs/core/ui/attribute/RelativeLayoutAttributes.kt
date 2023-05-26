package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.RelativeLayout
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Gravities

open class RelativeLayoutAttributes(resourceParser: ResourceParser, view: View) : ViewGroupAttributes(resourceParser, view) {

    override val view = super.view as RelativeLayout

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttr("gravity") { view.gravity = Gravities.parse(it) }
        registerAttr("horizontalGravity") { view.setHorizontalGravity(Gravities.parse(it)) }
        registerAttr("verticalGravity") { view.setVerticalGravity(Gravities.parse(it)) }
    }

}
