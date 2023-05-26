package org.autojs.autojs.core.ui.inflater.inflaters

import android.widget.SeekBar
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.inflater.util.Res

open class SeekbarInflater<V : SeekBar>(resourceParser: ResourceParser) : AbsSeekbarInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, attrs ->
        fun hasTrueAttr(name: String) = attrs["android:$name"] == "true"

        attrs["style"]?.let { return@ViewCreator SeekBar(context, null, 0, Res.parseStyle(context, it)) }

        if (hasTrueAttr("isMaterial") || hasTrueAttr("materialStyle") || hasTrueAttr("isMaterialStyle")) {
            return@ViewCreator SeekBar(context, null, 0, android.R.style.Widget_Material_SeekBar)
        }
        return@ViewCreator SeekBar(context)
    }
}
