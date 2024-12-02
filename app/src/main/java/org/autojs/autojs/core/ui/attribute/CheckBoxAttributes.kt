package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.CheckBox
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

open class CheckBoxAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : CompoundButtonAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as CheckBox

}
