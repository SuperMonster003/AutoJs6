package org.autojs.autojs.theme.intelligent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

class DailyScheduleAndOverridesTest {

    private val newYork = ZoneId.of("America/New_York")

    private fun local(text: String) = LocalDateTime.parse(text).atZone(newYork).toInstant().toEpochMilli()

    private fun instant(text: String) = OffsetDateTime.parse(text).toInstant().toEpochMilli()

    @Test
    fun nextDailyTimeIsTheFirstAfterNow() {
        val times = listOf(8 * 60, 20 * 60)
        assertEquals(local("2026-10-07T20:00"), DailySchedule.nextDue(times, local("2026-10-07T09:00"), newYork))
        assertEquals(local("2026-10-08T08:00"), DailySchedule.nextDue(times, local("2026-10-07T20:00"), newYork))
        assertNull(DailySchedule.nextDue(emptyList(), local("2026-10-07T09:00"), newYork))
    }

    @Test
    fun daylightSavingGapsAndOverlapsFireOnce() {
        // 2026-03-08 02:30 does not exist in New York: it fires at 03:30 EDT.
        assertEquals(instant("2026-03-08T03:30-04:00"), DailySchedule.nextDue(listOf(2 * 60 + 30), local("2026-03-08T00:00"), newYork))
        // 2026-11-01 01:30 happens twice: it fires at the first one only, then the next day.
        val first = instant("2026-11-01T01:30-04:00")
        assertEquals(first, DailySchedule.nextDue(listOf(90), local("2026-11-01T00:00"), newYork))
        assertEquals(instant("2026-11-02T01:30-05:00"), DailySchedule.nextDue(listOf(90), first, newYork))
        // Midnight after a spring-forward day.
        assertEquals(instant("2026-03-08T00:00-05:00"), DailySchedule.nextMidnight(local("2026-03-07T12:00"), newYork))
        assertEquals(instant("2026-03-09T00:00-04:00"), DailySchedule.nextMidnight(Instant.parse("2026-03-08T12:00:00Z").toEpochMilli(), newYork))
    }

    private fun date(text: String) = LocalDate.parse(text)

    private fun range(from: Pair<Int, Int>, to: Pair<Int, Int>, year: Int? = null) = DateCondition.DateRange(from.first, from.second, to.first, to.second, year)

    @Test
    fun dateRangesRepeatOrApplyOnce() {
        val christmas = range(12 to 24, 12 to 26)
        assertTrue(DateOverrides.matches(christmas, date("2026-12-25")))
        assertFalse(DateOverrides.matches(christmas, date("2026-12-27")))

        val holidays = range(12 to 24, 1 to 2)
        listOf("2026-12-31", "2027-01-02", "2026-12-24").forEach { assertTrue(it, DateOverrides.matches(holidays, date(it))) }
        listOf("2027-01-03", "2026-06-01").forEach { assertFalse(it, DateOverrides.matches(holidays, date(it))) }

        // Only the occurrence starting in 2026 counts.
        val once = range(12 to 24, 1 to 2, year = 2026)
        assertTrue(DateOverrides.matches(once, date("2026-12-30")))
        assertTrue(DateOverrides.matches(once, date("2027-01-01")))
        assertFalse(DateOverrides.matches(once, date("2026-01-01")))
        assertFalse(DateOverrides.matches(once, date("2027-12-30")))
        assertTrue(DateOverrides.matches(range(3 to 1, 3 to 10, year = 2027), date("2027-03-05")))
        assertFalse(DateOverrides.matches(range(3 to 1, 3 to 10, year = 2027), date("2026-03-05")))

        // A range ending on 02-29 ends on 02-28 in common years.
        assertTrue(DateOverrides.matches(range(2 to 1, 2 to 29), date("2026-02-28")))
        assertFalse(DateOverrides.matches(range(2 to 1, 2 to 29), date("2026-03-01")))
    }

    @Test
    fun theFirstMatchingOverrideWins() {
        val overrides = listOf(
            DateOverride(DateCondition.Unsupported("future", "{}"), 9L),
            DateOverride(range(12 to 1, 12 to 31), 1L),
            DateOverride(DateCondition.Holiday("christmas", daysBefore = 1), 2L),
        )
        assertEquals(1L, DateOverrides.firstMatch(overrides, date("2026-12-24"))?.groupId)
        assertEquals(2L, DateOverrides.firstMatch(overrides.drop(2), date("2026-12-24"))?.groupId)
        assertNull(DateOverrides.firstMatch(overrides, date("2026-11-30")))
    }

}
