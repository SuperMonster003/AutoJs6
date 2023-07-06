package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.ImageButton
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class ImageButtonInflater<V : ImageButton>(resourceParser: ResourceParser) : ImageViewInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<ImageButton> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): ImageButton {
            return ImageButton(context)
        }
    }

}
