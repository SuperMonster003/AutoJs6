package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.TimePicker
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.inflater.util.Res
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs6.R

/**
 * Created by Stardust on Nov 29, 2017.
 * Transformed by SuperMonster003 on May 23, 2023.
 */
open class TimePickerInflater<V : TimePicker>(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : FrameLayoutInflater<V>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in V> {
        return object : ViewCreator<TimePicker> {
            override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): TimePicker {
                val viewContext = themedContext(context, attrs)
                return when (val mode = attrs.remove("android:timePickerMode")) {
                    null -> attrs["style"]
                        ?.let { Res.parseStyle(context, it) }
                        ?.takeIf { it != 0 }
                        ?.let { TimePicker(context, null, 0, it) }
                        ?: TimePicker(context)
                    "clock" -> View.inflate(
                        viewContext,
                        R.layout.time_picker_clock,
                        null,
                    ) as TimePicker
                    "spinner" -> View.inflate(viewContext, R.layout.time_picker_spinner, null) as TimePicker
                    else -> throw IllegalArgumentException("Unknown timePickerMode: $mode")
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
