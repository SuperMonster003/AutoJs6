package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs6.R

/**
 * Created by Stardust on Nov 29, 2017.
 * Modified by SuperMonster003 as of Apr 12, 2023.
 * Transformed by SuperMonster003 on Apr 12, 2023.
 */
open class DatePickerInflater<V: DatePicker>(resourceParser: ResourceParser) : FrameLayoutInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> {
        return object : ViewCreator<DatePicker> {
            override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): DatePicker {
                val datePickerMode = attrs.remove("android:datePickerMode")
                return if (datePickerMode == null || datePickerMode != "spinner") {
                    DatePicker(context)
                } else {
                    (View.inflate(context, R.layout.date_picker_spinner, null) as DatePicker).apply {
                        @Suppress("DEPRECATION")
                        calendarViewShown = false
                    }
                }
            }
        }
    }
}