package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.Space
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

open class SpaceAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ViewAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as Space

}
