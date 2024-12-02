package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsImageButton
import org.autojs.autojs.runtime.ScriptRuntime

class JsImageButtonAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ImageButtonAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsImageButton

}