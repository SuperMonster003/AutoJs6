package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.TimePicker
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

/**
 * Created by SuperMonster003 on May 20, 2023.
 */
open class TimePickerAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : FrameLayoutAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as TimePicker

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttrs(arrayOf("hour", "hh")) { view.hour = it.toInt() }
        registerAttrs(arrayOf("minute", "mm")) { view.minute = it.toInt() }
        registerAttrs(arrayOf("is24HourView", "is24Hour", "is24H", "is24")) { view.setIs24HourView(it.toBoolean()) }
    }

}