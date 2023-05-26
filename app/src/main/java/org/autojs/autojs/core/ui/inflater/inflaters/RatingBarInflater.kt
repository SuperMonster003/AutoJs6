package org.autojs.autojs.core.ui.inflater.inflaters

import android.widget.RatingBar
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class RatingBarInflater<V : RatingBar>(resourceParser: ResourceParser) : AbsSeekbarInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> RatingBar(context) }

}
