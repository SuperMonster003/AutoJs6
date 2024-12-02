package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.ImageView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.runtime.ScriptRuntime

/**
 * Created by Stardust on Nov 3, 2017.
 * Modified by SuperMonster003 as of May 26, 2022.
 * Transformed by SuperMonster003 on May 19, 2023.
 */
open class ImageViewInflater<V : ImageView>(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : BaseViewInflater<V>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<ImageView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): ImageView {
            return ImageView(context)
        }
    }

}