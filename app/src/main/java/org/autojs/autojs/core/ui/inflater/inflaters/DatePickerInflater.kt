package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.inflater.util.Res
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs6.R

/**
 * Created by Stardust on Nov 29, 2017.
 * Modified by SuperMonster003 as of Apr 12, 2023.
 * Transformed by SuperMonster003 on Apr 12, 2023.
 */
open class DatePickerInflater<V: DatePicker>(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : FrameLayoutInflater<V>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in V> {
        return object : ViewCreator<DatePicker> {
            override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): DatePicker {
                val viewContext = themedContext(context, attrs)
                return when (val mode = attrs.remove("android:datePickerMode")) {
                    null -> attrs["style"]
                        ?.let { Res.parseStyle(context, it) }
                        ?.takeIf { it != 0 }
                        ?.let { DatePicker(context, null, 0, it) }
                        ?: DatePicker(context)
                    "calendar" -> View.inflate(
                        viewContext,
                        R.layout.date_picker_calendar,
                        null,
                    ) as DatePicker
                    "spinner" -> (View.inflate(
                        viewContext,
                        R.layout.date_picker_spinner,
                        null,
                    ) as DatePicker).apply {
                        @Suppress("DEPRECATION")
                        calendarViewShown = false
                    }
                    else -> throw IllegalArgumentException("Unknown datePickerMode: $mode")
                }
            }
        }
    }

    private fun themedContext(context: Context, attrs: Map<String, String>): Context {
        return listOfNotNull(attrs["style"]).fold(context) { themed, reference ->
            Res.parseStyle(themed, reference)
                .takeIf { it != 0 }
                ?.let { ContextThemeWrapper(themed, it) }
                ?: themed
        }
    }
}
