package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.ImageButton
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

open class ImageButtonAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ImageViewAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as ImageButton

}
