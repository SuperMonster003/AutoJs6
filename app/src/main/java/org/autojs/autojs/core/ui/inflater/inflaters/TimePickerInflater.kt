package org.autojs.autojs.core.ui.inflater.inflaters

import android.view.View
import android.widget.TimePicker
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs6.R

/**
 * Created by Stardust on 2017/11/29.
 * Transformed by SuperMonster003 on May 23, 2023.
 */
open class TimePickerInflater<V : TimePicker>(resourceParser: ResourceParser) : FrameLayoutInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> {
        return ViewCreator { context, attrs ->
            attrs.remove("android:timePickerMode")?.takeUnless { it == "spinner" }?.let {
                TimePicker(context)
            } ?: View.inflate(context, R.layout.time_picker_spinner, null) as TimePicker
        }
    }

}