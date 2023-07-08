package org.autojs.autojs.core.ui.attribute

import android.net.Uri
import android.view.View
import android.widget.ImageSwitcher
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Strings

open class ImageSwitcherAttributes(resourceParser: ResourceParser, view: View) : ViewSwitcherAttributes(resourceParser, view) {

    override val view = super.view as ImageSwitcher

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttrs(arrayOf("imageDrawable", "drawable")) { view.setImageDrawable(drawables.parse(view, it)) }
        registerAttrs(arrayOf("imageResource", "resource")) { view.setImageResource(it.toInt()) }
        registerAttrs(arrayOf("imageURI", "uri")) { view.setImageURI(Uri.parse(Strings.parse(view, it))) }
    }

}
