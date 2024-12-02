package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.CheckBox
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.runtime.ScriptRuntime

open class CheckBoxInflater<V: CheckBox>(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser): CompoundButtonInflater<V>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<CheckBox> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): CheckBox {
            return CheckBox(context)
        }
    }

}
