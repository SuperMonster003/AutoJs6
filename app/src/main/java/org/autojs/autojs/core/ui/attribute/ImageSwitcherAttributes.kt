package org.autojs.autojs.core.ui.attribute

import android.net.Uri
import android.view.View
import android.widget.ImageSwitcher
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Strings
import org.autojs.autojs.runtime.ScriptRuntime

open class ImageSwitcherAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ViewSwitcherAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as ImageSwitcher

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttrs(arrayOf("imageDrawable", "drawable")) { view.setImageDrawable(drawables.parse(view, it)) }
        registerAttrs(arrayOf("imageResource", "resource")) { view.setImageResource(it.toInt()) }
        registerAttrs(arrayOf("imageURI", "uri")) { view.setImageURI(Uri.parse(Strings.parse(view, it))) }
    }

}
