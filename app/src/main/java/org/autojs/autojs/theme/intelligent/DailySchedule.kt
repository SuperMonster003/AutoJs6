package org.autojs.autojs.theme.intelligent

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Local times of the day as instants (roadmap P3.4). A time skipped by a daylight saving gap fires right after the
 * gap; a time repeated by an overlap fires once, at its first occurrence.
 * zh-CN: 将一天中的本地时刻换算为时间点 (Roadmap P3.4). 因夏令时跳过的时刻在跳变后立即触发; 因重叠而重复的时刻只在首次出现时触发一次.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
object DailySchedule {

    /**
     * The first of [times] (minutes of the day) strictly after [now], or null when [times] is empty.
     * zh-CN: [times] (一天中的分钟数) 中严格晚于 [now] 的最早时刻; [times] 为空时返回 null.
     */
    fun nextDue(times: List<Int>, now: Long, zone: ZoneId): Long? {
        if (times.isEmpty()) return null
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return (0L..2L).asSequence().flatMap { offset ->
            val date = today.plusDays(offset)
            times.asSequence().map { date.atTime(LocalTime.of(it / 60, it % 60)).atZone(zone).toInstant().toEpochMilli() }
        }.filter { it > now }.minOrNull()
    }

    /** The start of the next local day after [now]. zh-CN: [now] 之后下一个本地日期的开始时刻. */
    fun nextMidnight(now: Long, zone: ZoneId): Long {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

}
