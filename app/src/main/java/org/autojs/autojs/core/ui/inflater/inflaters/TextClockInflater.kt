package org.autojs.autojs.core.ui.inflater.inflaters

import android.widget.TextClock
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class TextClockInflater<V : TextClock>(resourceParser: ResourceParser) : TextViewInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> TextClock(context) }

}
