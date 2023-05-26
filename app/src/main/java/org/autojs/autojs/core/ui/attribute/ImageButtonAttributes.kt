package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.ImageButton
import org.autojs.autojs.core.ui.inflater.ResourceParser

open class ImageButtonAttributes(resourceParser: ResourceParser, view: View) : ImageViewAttributes(resourceParser, view) {

    override val view = super.view as ImageButton

}
