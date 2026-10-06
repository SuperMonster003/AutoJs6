package org.autojs.autojs.theme.intelligent

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Trigger grid of an interval rotation: `anchor + k * period` for `k >= 0`. Anchors to the next whole
 * minute / hour use local time of the given zone, so offsets such as +05:30 still land on local whole hours.
 * zh-CN: 时间间隔换组的触发网格: `anchor + k * period` (k >= 0). 对齐到下一个整分 / 整时按给定时区的本地时间计算,
 * 因此 +05:30 等时区也落在本地整点.
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
object IntervalSchedule {

    /**
     * Shortest effective period (provisional until roadmap Q3 is decided).
     * zh-CN: 最短有效周期 (Roadmap Q3 拍板前的暂定值).
     */
    const val MIN_PERIOD_MILLIS = 5_000L

    fun effectivePeriod(periodMillis: Long): Long = periodMillis.coerceAtLeast(MIN_PERIOD_MILLIS)

    /**
     * Grid anchor when the trigger is armed at [now]. For [IntervalAnchor.NOW] the anchor is [now] itself,
     * which the activation already covers, so the first trigger comes one period later.
     * zh-CN: 在 [now] 启用时的网格起点. [IntervalAnchor.NOW] 的起点就是 [now], 由激活本身覆盖, 因此第一次触发在一个周期之后.
     */
    fun anchorAt(now: Long, anchor: IntervalAnchor, zone: ZoneId): Long = when (anchor) {
        IntervalAnchor.NOW -> now
        IntervalAnchor.NEXT_MINUTE -> nextBoundary(now, ChronoUnit.MINUTES, zone)
        IntervalAnchor.NEXT_HOUR -> nextBoundary(now, ChronoUnit.HOURS, zone)
    }

    /**
     * The first grid time strictly after [after]. Missed grid points are skipped rather than replayed.
     * zh-CN: 严格晚于 [after] 的第一个网格时间. 错过的网格点直接跳过, 不会补放.
     */
    fun nextDue(anchorAt: Long, periodMillis: Long, after: Long): Long {
        if (after < anchorAt) return anchorAt
        val period = effectivePeriod(periodMillis)
        return anchorAt + ((after - anchorAt) / period + 1) * period
    }

    private fun nextBoundary(now: Long, unit: ChronoUnit, zone: ZoneId): Long {
        return Instant.ofEpochMilli(now).atZone(zone).truncatedTo(unit).plus(1, unit).toInstant().toEpochMilli()
    }

}
