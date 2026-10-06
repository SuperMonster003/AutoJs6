package org.autojs.autojs.theme.intelligent

import org.autojs.autojs.theme.intelligent.calendar.HolidayCalendar
import java.time.LocalDate

/**
 * Matches date overrides against a local date (roadmap P3.5, D4): the first matching override in list order wins.
 * zh-CN: 按本地日期匹配日期覆盖层 (Roadmap P3.5, D4): 按列表顺序第一个命中的覆盖层生效.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
object DateOverrides {

    fun firstMatch(overrides: List<DateOverride>, date: LocalDate): DateOverride? = overrides.firstOrNull { matches(it.condition, date) }

    fun matches(condition: DateCondition, date: LocalDate): Boolean = when (condition) {
        is DateCondition.DateRange -> matchesRange(condition, date)
        is DateCondition.Holiday -> HolidayCalendar.contains(condition.id, date, condition.daysBefore, condition.daysAfter)
        is DateCondition.Unsupported -> false
    }

    /**
     * Month-days compare as numbers, so a range ending on 02-29 also ends on 02-28 in common years. A range whose
     * start is after its end wraps across the year end; with a year, only the occurrence starting in that year counts.
     * zh-CN: 月-日按数值比较, 因此以 02-29 结束的区间在平年于 02-28 结束. 起点晚于终点的区间跨年;
     * 指定年份时, 只计算从该年开始的那一次.
     */
    private fun matchesRange(range: DateCondition.DateRange, date: LocalDate): Boolean {
        val from = range.fromMonth * 100 + range.fromDay
        val to = range.toMonth * 100 + range.toDay
        val day = date.monthValue * 100 + date.dayOfMonth
        val year = range.year
        return when {
            from <= to -> day in from..to && (year == null || date.year == year)
            year == null -> day >= from || day <= to
            else -> (date.year == year && day >= from) || (date.year == year + 1 && day <= to)
        }
    }

}
