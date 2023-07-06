package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.SeekBar
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.inflater.util.Res

open class SeekBarInflater<V : SeekBar>(resourceParser: ResourceParser) : AbsSeekBarInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<SeekBar> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): SeekBar {
            fun hasTrueAttr(name: String) = attrs["android:$name"] == "true"

            attrs["style"]?.let { return SeekBar(context, null, 0, Res.parseStyle(context, it)) }

            if (hasTrueAttr("isMaterial") || hasTrueAttr("materialStyle") || hasTrueAttr("isMaterialStyle")) {
                return SeekBar(context, null, 0, android.R.style.Widget_Material_SeekBar)
            }
            return SeekBar(context)
        }
    }
}
