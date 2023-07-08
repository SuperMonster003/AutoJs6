package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.ViewFlipper
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class ViewFlipperInflater<V : ViewFlipper>(resourceParser: ResourceParser) : ViewAnimatorInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<ViewFlipper> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): ViewFlipper {
            return ViewFlipper(context)
        }
    }

}
