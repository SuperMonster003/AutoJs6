package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.Spinner
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.runtime.ScriptRuntime

open class SpinnerInflater<V : Spinner>(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : AbsSpinnerInflater<V>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<Spinner> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): Spinner {
            return Spinner(context)
        }
    }

}
