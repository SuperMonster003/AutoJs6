package org.autojs.autojs.theme.intelligent.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class HolidayCalendarTest {

    private fun date(text: String) = LocalDate.parse(text)

    @Test
    fun rulesGiveKnownDates() {
        mapOf(2000 to "2000-04-23", 2019 to "2019-04-21", 2024 to "2024-03-31", 2025 to "2025-04-20", 2026 to "2026-04-05", 2027 to "2027-03-28")
            .forEach { (year, expected) -> assertEquals("easter $year", date(expected), HolidayCalendar.dateOf("easter", year)) }
        assertEquals(date("2026-05-10"), HolidayCalendar.dateOf("mothersDay", 2026))
        assertEquals(date("2026-06-21"), HolidayCalendar.dateOf("fathersDay", 2026))
        assertEquals(date("2026-11-26"), HolidayCalendar.dateOf("thanksgiving", 2026))
        assertEquals(date("2026-12-25"), HolidayCalendar.dateOf("christmas", 2026))
        assertEquals(date("2026-02-17"), HolidayCalendar.dateOf("springFestival", 2026))
        assertEquals(date("2026-09-25"), HolidayCalendar.dateOf("midAutumnFestival", 2026))
        assertNull(HolidayCalendar.dateOf("springFestival", 2200))
        assertNull(HolidayCalendar.dateOf("unknown", 2026))
    }

    @Test
    fun idsAreUnique() {
        assertEquals(HolidayCalendar.ALL.size, HolidayCalendar.ALL.map { it.id }.toSet().size)
    }

    @Test
    fun widenedHolidaysMayCrossTheYearEnd() {
        assertTrue(HolidayCalendar.contains("christmas", date("2026-12-24"), daysBefore = 1))
        assertFalse(HolidayCalendar.contains("christmas", date("2026-12-24")))
        assertTrue(HolidayCalendar.contains("newYear", date("2025-12-31"), daysBefore = 1))
        assertTrue(HolidayCalendar.contains("christmas", date("2027-01-01"), daysAfter = 7))
        assertFalse(HolidayCalendar.contains("christmas", date("2027-01-02"), daysAfter = 7))
        // Spring Festival 2026 with a week after it.
        assertTrue(HolidayCalendar.contains("springFestival", date("2026-02-24"), daysAfter = 7))
        assertFalse(HolidayCalendar.contains("springFestival", date("2026-02-25"), daysAfter = 7))
    }

    @Test
    fun regionalHolidaysComeFirst() {
        val chinese = HolidayCalendar.suggestedOrder(Locale.SIMPLIFIED_CHINESE).map { it.id }
        assertEquals("springFestival", chinese.first())
        assertEquals(HolidayCalendar.ALL.size, chinese.size)
        val american = HolidayCalendar.suggestedOrder(Locale.US).map { it.id }
        assertEquals("thanksgiving", american.first())
        assertEquals("newYear", HolidayCalendar.suggestedOrder(Locale.FRANCE).first().id)
    }

}
