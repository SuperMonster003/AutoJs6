package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.SurfaceView
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.runtime.ScriptRuntime

open class SurfaceViewInflater<V : SurfaceView>(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : BaseViewInflater<V>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<SurfaceView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): SurfaceView {
            return SurfaceView(context)
        }
    }

}
