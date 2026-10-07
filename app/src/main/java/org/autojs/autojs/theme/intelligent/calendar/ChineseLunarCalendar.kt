package org.autojs.autojs.theme.intelligent.calendar

import java.time.LocalDate

/**
 * The Chinese lunisolar calendar for lunar years 1900 to 2100, by table (roadmap P3.5).
 * zh-CN: 以查表实现的中国农历, 覆盖农历 1900 至 2100 年 (Roadmap P3.5).
 *
 * Each table entry describes one lunar year: bits 15..4 tell whether months 1..12 have 30 days (else 29),
 * bits 3..0 give the leap month (0 for none), and bit 16 tells whether the leap month has 30 days.
 * Lunar 1900-01-01 is 1900-01-31 in the Gregorian calendar.
 * zh-CN: 每个表项描述一个农历年: 第 15..4 位表示 1..12 月是否为大月 (30 天, 否则 29 天),
 * 第 3..0 位为闰月 (0 表示无闰月), 第 16 位表示闰月是否为大月. 农历 1900 年正月初一即公历 1900-01-31.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
object ChineseLunarCalendar {

    const val MIN_YEAR = 1900
    const val MAX_YEAR = 2100

    private val EPOCH: LocalDate = LocalDate.of(1900, 1, 31)

    private val INFO = intArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2, // 1900-1909
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977, // 1910-1919
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970, // 1920-1929
        0x06566, 0x0d4a0, 0x0ea50, 0x16a95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950, // 1930-1939
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557, // 1940-1949
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0, // 1950-1959
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0, // 1960-1969
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6, // 1970-1979
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570, // 1980-1989
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x05ac0, 0x0ab60, 0x096d5, 0x092e0, // 1990-1999
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, // 2000-2009
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930, // 2010-2019
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530, // 2020-2029
        0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45, // 2030-2039
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0, // 2040-2049
        0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0, // 2050-2059
        0x092e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4, // 2060-2069
        0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0, // 2070-2079
        0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160, // 2080-2089
        0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252, // 2090-2099
        0x0d520, // 2100
    )

    /** Days from the epoch to lunar new year of each year. zh-CN: 自纪元起至各年正月初一的天数. */
    private val NEW_YEAR_OFFSETS: IntArray by lazy {
        IntArray(INFO.size + 1).also { offsets ->
            for (index in INFO.indices) offsets[index + 1] = offsets[index] + yearDays(MIN_YEAR + index)
        }
    }

    fun isSupported(year: Int) = year in MIN_YEAR..MAX_YEAR

    /** The leap month of [year], or 0 when it has none. zh-CN: [year] 的闰月, 无闰月时为 0. */
    fun leapMonth(year: Int): Int = info(year) and 0xF

    fun monthDays(year: Int, month: Int): Int {
        require(month in 1..12) { "Month out of range: $month" }
        return if (info(year) and (0x10000 shr month) != 0) 30 else 29
    }

    fun leapMonthDays(year: Int): Int = when {
        leapMonth(year) == 0 -> 0
        info(year) and 0x10000 != 0 -> 30
        else -> 29
    }

    fun yearDays(year: Int): Int = (1..12).sumOf { monthDays(year, it) } + leapMonthDays(year)

    /**
     * The Gregorian date of a lunar date, or null when it does not exist (out of range, a missing leap month,
     * or day 30 of a short month).
     * zh-CN: 农历日期对应的公历日期; 日期不存在时 (超出范围, 该年无此闰月, 或小月的三十) 返回 null.
     */
    fun toGregorian(year: Int, month: Int, day: Int, isLeapMonth: Boolean = false): LocalDate? {
        if (!isSupported(year) || month !in 1..12) return null
        val leap = leapMonth(year)
        if (isLeapMonth && leap != month) return null
        val length = if (isLeapMonth) leapMonthDays(year) else monthDays(year, month)
        if (day !in 1..length) return null
        var offset = NEW_YEAR_OFFSETS[year - MIN_YEAR]
        for (m in 1 until month) {
            offset += monthDays(year, m)
            if (m == leap) offset += leapMonthDays(year)
        }
        if (isLeapMonth) offset += monthDays(year, month)
        return EPOCH.plusDays((offset + day - 1).toLong())
    }

    private fun info(year: Int): Int {
        require(isSupported(year)) { "Lunar year out of range: $year" }
        return INFO[year - MIN_YEAR]
    }

}
