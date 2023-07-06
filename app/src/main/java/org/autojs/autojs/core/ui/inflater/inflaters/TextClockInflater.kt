package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.TextClock
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class TextClockInflater<V : TextClock>(resourceParser: ResourceParser) : TextViewInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<TextClock> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): TextClock {
            return TextClock(context)
        }
    }

}
