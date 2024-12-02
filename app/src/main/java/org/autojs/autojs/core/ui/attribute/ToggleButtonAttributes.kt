package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.ToggleButton
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Strings
import org.autojs.autojs.runtime.ScriptRuntime

open class ToggleButtonAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : CompoundButtonAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as ToggleButton

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttr("textOff") { view.textOff = Strings.parse(view, it) }
        registerAttr("textOn") { view.textOn = Strings.parse(view, it) }
    }

}
