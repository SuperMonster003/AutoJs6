package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.VideoView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Strings

open class VideoViewAttributes(resourceParser: ResourceParser, view: View) : SurfaceViewAttributes(resourceParser, view) {

    override val view = super.view as VideoView

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttrs(arrayOf("videoPath", "path", "src")) { view.setVideoPath(Strings.parsePath(view, it) ?: return@registerAttrs) }
    }

}
