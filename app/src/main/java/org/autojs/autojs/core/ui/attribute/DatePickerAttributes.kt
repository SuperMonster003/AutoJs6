package org.autojs.autojs.core.ui.attribute

import android.view.InflateException
import android.view.View
import android.widget.DatePicker
import org.autojs.autojs.core.ui.inflater.ResourceParser
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

open class DatePickerAttributes(resourceParser: ResourceParser, view: View) : FrameLayoutAttributes(resourceParser, view) {

    override val view = super.view as DatePicker

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

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