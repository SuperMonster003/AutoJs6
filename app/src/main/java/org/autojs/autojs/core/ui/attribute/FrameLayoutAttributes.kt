package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.FrameLayout
import org.autojs.autojs.core.ui.inflater.ResourceParser

open class FrameLayoutAttributes(resourceParser: ResourceParser, view: View) : ViewGroupAttributes(resourceParser, view) {

    override val view = super.view as FrameLayout

}
