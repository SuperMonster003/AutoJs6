package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.Button
import org.autojs.autojs.core.ui.inflater.ResourceParser

open class ButtonAttributes(resourceParser: ResourceParser, view: View) : TextViewAttributes(resourceParser, view) {

    override val view = super.view as Button

}