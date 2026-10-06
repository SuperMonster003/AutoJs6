package org.autojs.autojs.theme.intelligent.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Holidays available to date overrides (roadmap P3.5, D21): common Gregorian holidays, Chinese lunar festivals
 * and a few regional ones. Ids are stable and stored in configurations and exchange files.
 * zh-CN: 可用于日期覆盖层的节日 (Roadmap P3.5, D21): 通用公历节日, 中国农历节日以及少量地区节日.
 * id 稳定不变, 保存在配置与交换文件中.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
object HolidayCalendar {

    sealed interface Rule {

        /** The date in Gregorian [year], or null when the rule cannot compute it. zh-CN: 公历 [year] 年中的日期; 无法计算时返回 null. */
        fun dateIn(year: Int): LocalDate?

        data class Fixed(val month: Int, val day: Int) : Rule {
            override fun dateIn(year: Int): LocalDate = LocalDate.of(year, month, day)
        }

        /** The [nth] [dayOfWeek] of [month]. zh-CN: [month] 月的第 [nth] 个 [dayOfWeek]. */
        data class NthWeekday(val month: Int, val nth: Int, val dayOfWeek: DayOfWeek) : Rule {
            override fun dateIn(year: Int): LocalDate = LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(nth, dayOfWeek))
        }

        /** A Chinese lunar date; the festivals used here always fall in the Gregorian year of the same number. zh-CN: 农历日期; 这里使用的节日总在同一数字的公历年内. */
        data class Lunar(val month: Int, val day: Int) : Rule {
            override fun dateIn(year: Int): LocalDate? = ChineseLunarCalendar.toGregorian(year, month, day)
        }

        /** Western (Gregorian) Easter Sunday by the anonymous Gregorian algorithm. zh-CN: 按匿名公历算法计算的西方复活节 (星期日). */
        data object Easter : Rule {
            override fun dateIn(year: Int): LocalDate {
                val a = year % 19
                val b = year / 100
                val c = year % 100
                val d = b / 4
                val e = b % 4
                val f = (b + 8) / 25
                val g = (b - f + 1) / 3
                val h = (19 * a + b - d - g + 15) % 30
                val i = c / 4
                val k = c % 4
                val l = (32 + 2 * e + 2 * i - h - k) % 7
                val m = (a + 11 * h + 22 * l) / 451
                val month = (h + l - 7 * m + 114) / 31
                val day = (h + l - 7 * m + 114) % 31 + 1
                return LocalDate.of(year, month, day)
            }
        }

    }

    /**
     * @property regions ISO country codes or language codes where the holiday is suggested first; empty means everywhere.
     * zh-CN: 优先推荐该节日的 ISO 国家或语言代码; 为空表示全部地区.
     */
    data class Holiday(val id: String, val rule: Rule, val regions: Set<String> = emptySet())

    private val CHINESE_REGIONS = setOf("zh", "CN", "TW", "HK", "MO", "SG")

    val ALL: List<Holiday> = listOf(
        Holiday("newYear", Rule.Fixed(1, 1)),
        Holiday("valentinesDay", Rule.Fixed(2, 14)),
        Holiday("halloween", Rule.Fixed(10, 31)),
        Holiday("christmas", Rule.Fixed(12, 25)),
        Holiday("springFestival", Rule.Lunar(1, 1), CHINESE_REGIONS),
        Holiday("lanternFestival", Rule.Lunar(1, 15), CHINESE_REGIONS),
        Holiday("dragonBoatFestival", Rule.Lunar(5, 5), CHINESE_REGIONS),
        Holiday("qixiFestival", Rule.Lunar(7, 7), CHINESE_REGIONS),
        Holiday("midAutumnFestival", Rule.Lunar(8, 15), CHINESE_REGIONS),
        Holiday("doubleNinthFestival", Rule.Lunar(9, 9), CHINESE_REGIONS),
        Holiday("easter", Rule.Easter),
        Holiday("mothersDay", Rule.NthWeekday(5, 2, DayOfWeek.SUNDAY)),
        Holiday("fathersDay", Rule.NthWeekday(6, 3, DayOfWeek.SUNDAY)),
        Holiday("thanksgiving", Rule.NthWeekday(11, 4, DayOfWeek.THURSDAY), setOf("US")),
    )

    fun find(id: String): Holiday? = ALL.firstOrNull { it.id == id }

    fun dateOf(id: String, year: Int): LocalDate? = runCatching { find(id)?.rule?.dateIn(year) }.getOrNull()

    /**
     * Whether [date] falls in the holiday widened by [daysBefore] and [daysAfter]; widened ranges may cross a year end.
     * zh-CN: [date] 是否落在向前扩展 [daysBefore] 天, 向后扩展 [daysAfter] 天的节日范围内; 扩展后的范围可以跨年.
     */
    fun contains(id: String, date: LocalDate, daysBefore: Int = 0, daysAfter: Int = 0): Boolean {
        return (date.year - 1..date.year + 1).any { year ->
            val day = dateOf(id, year) ?: return@any false
            !date.isBefore(day.minusDays(daysBefore.toLong())) && !date.isAfter(day.plusDays(daysAfter.toLong()))
        }
    }

    /**
     * Holidays in suggestion order for [locale]: the regional ones of its country or language first, then the rest.
     * zh-CN: 按 [locale] 的推荐顺序排列的节日: 先列出其国家或语言所在地区的节日, 再列出其余节日.
     */
    fun suggestedOrder(locale: Locale): List<Holiday> {
        val (regional, others) = ALL.partition { holiday ->
            holiday.regions.isNotEmpty() && (locale.country in holiday.regions || locale.language in holiday.regions)
        }
        return regional + others
    }

}
