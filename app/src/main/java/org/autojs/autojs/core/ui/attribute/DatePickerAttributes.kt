package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.DatePicker
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

open class DatePickerAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : FrameLayoutAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as DatePicker

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttr("minDate") { setMinDate(view, it) }
        registerAttr("maxDate") { setMaxDate(view, it) }
        registerAttr("firstDayOfWeek") { view.firstDayOfWeek = parseDayOfWeek(it) }

        @Suppress("DEPRECATION")
        registerAttr("spinnersShown") { view.spinnersShown = it.toBoolean() }

        @Suppress("DEPRECATION")
        registerAttr("calendarViewShown") { view.calendarViewShown = it.toBoolean() }

        registerAttrUnsupported(
            arrayOf(
                "startYear",
                "endYear",
                "calendarTextColor",
                "dayOfWeekBackground",
                "dayOfWeekTextAppearance",
                "headerBackground",
                "headerDayOfMonthTextAppearance",
                "headerMonthTextAppearance",
                "headerYearTextAppearance",
                "yearListItemTextAppearance",
                "yearListSelectorColor",
            )
        )
    }

}