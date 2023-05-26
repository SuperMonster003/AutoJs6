package org.autojs.autojs.core.ui.inflater.inflaters

import com.makeramen.roundedimageview.RoundedImageView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class RoundedImageViewInflater<V : RoundedImageView>(resourceParser: ResourceParser) : ImageViewInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> RoundedImageView(context) }

}
