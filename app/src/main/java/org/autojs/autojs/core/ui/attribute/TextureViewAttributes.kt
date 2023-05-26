package org.autojs.autojs.core.ui.attribute

import android.view.TextureView
import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser

open class TextureViewAttributes(resourceParser: ResourceParser, view: View) : ViewAttributes(resourceParser, view) {

    override val view = super.view as TextureView

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttrs(arrayOf("isOpaque", "opaque")) { view.isOpaque = it.toBoolean() }
    }

}
