package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.ToggleButton
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Strings

open class ToggleButtonAttributes(resourceParser: ResourceParser, view: View) : CompoundButtonAttributes(resourceParser, view) {

    override val view = super.view as ToggleButton

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttr("textOff") { view.textOff = Strings.parse(view, it) }
        registerAttr("textOn") { view.textOn = Strings.parse(view, it) }
    }

}
