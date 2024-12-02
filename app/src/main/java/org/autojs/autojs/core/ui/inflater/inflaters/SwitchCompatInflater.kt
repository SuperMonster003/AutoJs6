package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import androidx.appcompat.widget.SwitchCompat
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.runtime.ScriptRuntime

open class SwitchCompatInflater<V : SwitchCompat>(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : CompoundButtonInflater<V>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<SwitchCompat> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): SwitchCompat {
            return SwitchCompat(context)
        }
    }

}
