package org.autojs.autojs.theme.intelligent.calendar

import android.icu.util.Calendar
import android.icu.util.ChineseCalendar
import android.icu.util.TimeZone
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Date

/**
 * Checks the lunar table against the astronomical Chinese calendar of the platform ICU (roadmap P3.5):
 * every month start of lunar years 1901 to 2099, leap months included.
 * zh-CN: 以平台 ICU 的天文农历核对农历表 (Roadmap P3.5): 覆盖农历 1901 至 2099 年每个月 (含闰月) 的初一.
 */
@RunWith(AndroidJUnit4::class)
class ChineseLunarCalendarIcuTest {

    @Test
    fun everyMonthStartMatchesIcu() {
        val zone = TimeZone.getTimeZone("Asia/Shanghai")
        val calendar = ChineseCalendar(zone)
        // ICU versions differ in how EXTENDED_YEAR counts (cycle based or the related Gregorian year), so the offset
        // is taken from the first month and must then hold for every month.
        // zh-CN: 不同 ICU 版本的 EXTENDED_YEAR 计数方式不同 (按纪年或对应的公历年), 因此偏移取自第一个月, 之后每个月都必须一致.
        calendar.time = noonOf(ChineseLunarCalendar.toGregorian(1901, 1, 1)!!, zone)
        val extendedYearOffset = calendar.get(Calendar.EXTENDED_YEAR) - 1901
        val mismatches = mutableListOf<String>()
        for (year in 1901..2099) {
            val leap = ChineseLunarCalendar.leapMonth(year)
            for (month in 1..12) {
                for (isLeap in if (month == leap) listOf(false, true) else listOf(false)) {
                    val start = ChineseLunarCalendar.toGregorian(year, month, 1, isLeap)!!
                    calendar.time = noonOf(start, zone)
                    val actual = listOf(
                        calendar.get(Calendar.EXTENDED_YEAR) - extendedYearOffset,
                        calendar.get(Calendar.MONTH) + 1,
                        calendar.get(ChineseCalendar.IS_LEAP_MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH),
                    )
                    val expected = listOf(year, month, if (isLeap) 1 else 0, 1)
                    if (actual != expected) mismatches += "$year-$month${if (isLeap) "L" else ""}"
                }
            }
        }
        assertEquals(KNOWN_DIFFERENCES, mismatches.toSet())
    }

    companion object {

        /**
         * Months where the table (the official calendar) and ICU's astronomical approximation disagree. Most are new moons
         * within minutes of midnight Beijing time, which ICU places on the wrong day (e.g. 2018-11-07 16:02 UTC is
         * 2018-11-08 00:02 in Beijing, the official start of the tenth month; Spring Festival 2027 and 2030 are officially
         * February 6 and February 3). In 1917, 1922 and 1987 ICU places the leap month one month later than the official
         * calendar (1987 officially has a leap sixth month). Any other difference means a table error.
         * zh-CN: 农历表 (官方历法) 与 ICU 天文近似结果不一致的月份. 多数是朔日落在北京时间午夜前后数分钟内, ICU 将其归到错误的一天
         * (例如 2018-11-07 16:02 UTC 即北京时间 2018-11-08 00:02, 为官方十月初一; 2027 与 2030 年春节官方为 2 月 6 日与 2 月 3 日).
         * 1917, 1922 与 1987 年 ICU 把闰月放在官方历法的下一个月 (1987 年官方为闰六月). 其他任何差异都意味着表有误.
         */
        private val KNOWN_DIFFERENCES = setOf(
            "1917-2L", "1917-3", "1922-5L", "1922-6", "1954-1", "1955-2", "1987-6L", "1987-7",
            "1998-12", "2012-7", "2018-10", "2027-1", "2030-1", "2070-2",
        )

    }

    private fun noonOf(date: LocalDate, zone: TimeZone): Date {
        val gregorian = android.icu.util.GregorianCalendar(zone).apply {
            clear()
            set(date.year, date.monthValue - 1, date.dayOfMonth, 12, 0, 0)
        }
        return gregorian.time
    }

}
