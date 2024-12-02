package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.ViewAnimator
import android.widget.ViewSwitcher
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.runtime.ScriptRuntime

open class ViewSwitcherInflater<V : ViewAnimator>(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : ViewAnimatorInflater<V>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<ViewAnimator> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): ViewSwitcher {
            return ViewSwitcher(context)
        }
    }

}
