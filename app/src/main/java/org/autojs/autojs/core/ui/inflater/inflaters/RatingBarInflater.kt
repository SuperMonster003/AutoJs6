package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.RatingBar
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class RatingBarInflater<V : RatingBar>(resourceParser: ResourceParser) : AbsSeekBarInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<RatingBar> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): RatingBar {
            return RatingBar(context)
        }
    }

}
