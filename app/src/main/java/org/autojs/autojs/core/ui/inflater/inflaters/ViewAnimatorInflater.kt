package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.ViewAnimator
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class ViewAnimatorInflater<V : ViewAnimator>(resourceParser: ResourceParser) : FrameLayoutInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<ViewAnimator> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): ViewAnimator {
            return ViewAnimator(context)
        }
    }

}
