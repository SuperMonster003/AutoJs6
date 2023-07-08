package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.Space
import org.autojs.autojs.core.ui.inflater.ResourceParser

open class SpaceAttributes(resourceParser: ResourceParser, view: View) : ViewAttributes(resourceParser, view) {

    override val view = super.view as Space

}
