package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.VideoView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Strings
import org.autojs.autojs.runtime.ScriptRuntime

open class VideoViewAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : SurfaceViewAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as VideoView

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttrs(arrayOf("videoPath", "path", "src")) { view.setVideoPath(Strings.parsePath(view, it) ?: return@registerAttrs) }
    }

}
