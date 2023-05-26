package org.autojs.autojs.core.ui.inflater.inflaters

import android.view.TextureView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class TextureViewInflater<V: TextureView>(resourceParser: ResourceParser): BaseViewInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> TextureView(context) }

}
