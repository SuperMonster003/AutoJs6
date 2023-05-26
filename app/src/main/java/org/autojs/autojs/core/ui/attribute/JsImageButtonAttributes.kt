package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsImageButton

class JsImageButtonAttributes(resourceParser: ResourceParser, view: View) : ImageButtonAttributes(resourceParser, view) {

    override val view = super.view as JsImageButton

}