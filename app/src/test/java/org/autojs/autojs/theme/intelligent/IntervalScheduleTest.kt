package org.autojs.autojs.theme.intelligent

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class IntervalScheduleTest {

    private val minute = 60_000L
    private val hour = 60 * minute

    private fun at(zone: ZoneId, year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0) =
        ZonedDateTime.of(year, month, day, hour, minute, second, 0, zone).toInstant().toEpochMilli()

    @Test
    fun nowAnchorFirstFiresOnePeriodLater() {
        val now = 1_790_000_000_123L
        val anchor = IntervalSchedule.anchorAt(now, IntervalAnchor.NOW, ZoneId.of("UTC"))
        assertEquals(now, anchor)
        assertEquals(now + 10 * minute, IntervalSchedule.nextDue(anchor, 10 * minute, now))
    }

    @Test
    fun wholeMinuteAndHourAnchorsUseLocalTime() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        val now = at(kolkata, 2026, 10, 6, 15, 51, 20)
        assertEquals(at(kolkata, 2026, 10, 6, 15, 52), IntervalSchedule.anchorAt(now, IntervalAnchor.NEXT_MINUTE, kolkata))
        assertEquals(at(kolkata, 2026, 10, 6, 16, 0), IntervalSchedule.anchorAt(now, IntervalAnchor.NEXT_HOUR, kolkata))

        val onBoundary = at(kolkata, 2026, 10, 6, 15, 52)
        assertEquals(at(kolkata, 2026, 10, 6, 15, 53), IntervalSchedule.anchorAt(onBoundary, IntervalAnchor.NEXT_MINUTE, kolkata))
    }

    @Test
    fun nextHourAcrossTheSpringForwardGap() {
        val newYork = ZoneId.of("America/New_York")
        // 02:00 does not exist on 2026-03-08; the next whole hour after 01:30 EST is 03:00 EDT (07:00 UTC).
        // zh-CN: 2026-03-08 不存在 02:00; 01:30 (EST) 之后的下一个整点是 03:00 (EDT), 即 UTC 07:00.
        val now = at(newYork, 2026, 3, 8, 1, 30)
        assertEquals(at(ZoneId.of("UTC"), 2026, 3, 8, 7, 0), IntervalSchedule.anchorAt(now, IntervalAnchor.NEXT_HOUR, newYork))
    }

    @Test
    fun nextDueSkipsMissedGridPoints() {
        val anchor = 1_000_000L
        val period = 10 * minute
        assertEquals(anchor, IntervalSchedule.nextDue(anchor, period, anchor - 1))
        assertEquals(anchor + period, IntervalSchedule.nextDue(anchor, period, anchor))
        assertEquals(anchor + 11 * period, IntervalSchedule.nextDue(anchor, period, anchor + 10 * period + period / 2))
        assertEquals(anchor + 12 * period, IntervalSchedule.nextDue(anchor, period, anchor + 11 * period))
        assertEquals(anchor + 2 * hour, IntervalSchedule.nextDue(anchor, hour, anchor + hour))
    }

    @Test
    fun periodsShorterThanTheMinimumAreClamped() {
        assertEquals(IntervalSchedule.MIN_PERIOD_MILLIS, IntervalSchedule.effectivePeriod(1_000L))
        assertEquals(IntervalSchedule.MIN_PERIOD_MILLIS, IntervalSchedule.nextDue(0L, 1_000L, 0L))
    }

}
