package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.VideoView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class VideoViewInflater<V : VideoView>(resourceParser: ResourceParser) : SurfaceViewInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<VideoView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): VideoView {
            return VideoView(context)
        }
    }

}
