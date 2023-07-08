package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import androidx.appcompat.widget.SwitchCompat
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class SwitchCompatInflater<V : SwitchCompat>(resourceParser: ResourceParser) : CompoundButtonInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<SwitchCompat> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): SwitchCompat {
            return SwitchCompat(context)
        }
    }

}
