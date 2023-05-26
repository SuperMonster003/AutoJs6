package org.autojs.autojs.core.ui.attribute

import android.view.InflateException
import android.view.View
import android.widget.DatePicker
import org.autojs.autojs.core.ui.inflater.ResourceParser
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.text.RegexOption.IGNORE_CASE

open class DatePickerAttributes(resourceParser: ResourceParser, view: View) : FrameLayoutAttributes(resourceParser, view) {

    override val view = super.view as DatePicker

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttr("minDate") { setMinDate(it) }
        registerAttr("maxDate") { setMaxDate(it) }
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

    private fun setMaxDate(value: String) {
        try {
            parseDate(value)?.time?.let { view.maxDate = it }
        } catch (e: ParseException) {
            throw InflateException(e)
        }
    }

    private fun setMinDate(value: String) {
        try {
            parseDate(value)?.time?.let { view.minDate = it }
        } catch (e: ParseException) {
            throw InflateException(e)
        }
    }

    private fun parseDate(value: String) = when {
        value.matches(Regex("^\\d{4}/.+")) -> {
            SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).parse(value)
        }
        else -> {
            SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).parse(value)
        }
    }

    @Suppress("SpellCheckingInspection")
    private fun parseDayOfWeek(value: String) = when {
        value.matches(Regex("MON(DAY)?", IGNORE_CASE)) -> Calendar.MONDAY
        value.matches(Regex("TUE(SDAY)?", IGNORE_CASE)) -> Calendar.TUESDAY
        value.matches(Regex("WED(NESDAY)?", IGNORE_CASE)) -> Calendar.WEDNESDAY
        value.matches(Regex("THU(RSDAY)?", IGNORE_CASE)) -> Calendar.THURSDAY
        value.matches(Regex("FRI(DAY)?", IGNORE_CASE)) -> Calendar.FRIDAY
        value.matches(Regex("SAT(URDAY)?", IGNORE_CASE)) -> Calendar.SATURDAY
        value.matches(Regex("SUN(DAY)?", IGNORE_CASE)) -> Calendar.SUNDAY
        else -> {
            // @Caution by SuperMonster003 on May 19, 2023.
            //  ! Calendar.XXX is not as same as JavaScript Date.
            //  ! Take Tuesday as an example,
            //  ! for Java, Calendar.TUESDAY is 3,
            //  ! for JavaScript, Date#getDay() is 2.

            // Compatibility for 0 is not necessary.
            // (value.toInt() + 6).mod(7) + 1

            value.toInt()
        }
    }

}