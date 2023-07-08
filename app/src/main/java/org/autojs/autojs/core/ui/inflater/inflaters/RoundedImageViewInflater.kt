package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import com.makeramen.roundedimageview.RoundedImageView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class RoundedImageViewInflater<V : RoundedImageView>(resourceParser: ResourceParser) : ImageViewInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<RoundedImageView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): RoundedImageView {
            return RoundedImageView(context)
        }
    }

}
