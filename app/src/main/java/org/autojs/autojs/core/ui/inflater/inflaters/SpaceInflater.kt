package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.Space
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class SpaceInflater(resourceParser: ResourceParser) : BaseViewInflater<Space>(resourceParser) {

    override fun getCreator(): ViewCreator<in Space> = object : ViewCreator<Space> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): Space {
            return Space(context)
        }
    }

}
