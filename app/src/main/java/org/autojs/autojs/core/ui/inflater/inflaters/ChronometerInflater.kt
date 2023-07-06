package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.Chronometer
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class ChronometerInflater<V : Chronometer>(resourceParser: ResourceParser) : TextViewInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<Chronometer> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): Chronometer {
            return Chronometer(context)
        }
    }

}
