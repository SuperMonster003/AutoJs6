package org.autojs.autojs.core.ui.inflater.inflaters

import android.widget.ImageButton
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class ImageButtonInflater<V : ImageButton>(resourceParser: ResourceParser) : ImageViewInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> ImageButton(context) }

}
