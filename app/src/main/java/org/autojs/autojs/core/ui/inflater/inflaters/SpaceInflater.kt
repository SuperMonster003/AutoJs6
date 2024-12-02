package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.Space
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.runtime.ScriptRuntime

open class SpaceInflater(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : BaseViewInflater<Space>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in Space> = object : ViewCreator<Space> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): Space {
            return Space(context)
        }
    }

}
