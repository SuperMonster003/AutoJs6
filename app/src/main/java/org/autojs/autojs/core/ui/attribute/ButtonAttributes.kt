package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.Button
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

open class ButtonAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : TextViewAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as Button

}