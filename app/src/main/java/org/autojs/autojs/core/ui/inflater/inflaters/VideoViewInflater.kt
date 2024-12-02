package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.VideoView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.runtime.ScriptRuntime

open class VideoViewInflater<V : VideoView>(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : SurfaceViewInflater<V>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<VideoView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): VideoView {
            return VideoView(context)
        }
    }

}
