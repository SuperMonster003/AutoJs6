package org.autojs.autojs.theme.intelligent.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChineseLunarCalendarTest {

    private fun date(text: String) = LocalDate.parse(text)

    @Test
    fun lunarNewYearMatchesKnownDates() {
        val known = mapOf(
            1900 to "1900-01-31", 1949 to "1949-01-29", 1950 to "1950-02-17", 1976 to "1976-01-31", 1990 to "1990-01-27",
            2000 to "2000-02-05", 2008 to "2008-02-07", 2010 to "2010-02-14", 2012 to "2012-01-23", 2015 to "2015-02-19",
            2016 to "2016-02-08", 2017 to "2017-01-28", 2018 to "2018-02-16", 2019 to "2019-02-05", 2020 to "2020-01-25",
            2021 to "2021-02-12", 2022 to "2022-02-01", 2023 to "2023-01-22", 2024 to "2024-02-10", 2025 to "2025-01-29",
            2026 to "2026-02-17", 2027 to "2027-02-06", 2028 to "2028-01-26", 2029 to "2029-02-13", 2030 to "2030-02-03",
        )
        known.forEach { (year, expected) -> assertEquals("lunar new year $year", date(expected), ChineseLunarCalendar.toGregorian(year, 1, 1)) }
    }

    @Test
    fun festivalsMatchKnownDates() {
        assertEquals(date("2026-03-03"), ChineseLunarCalendar.toGregorian(2026, 1, 15))
        assertEquals(date("2024-06-10"), ChineseLunarCalendar.toGregorian(2024, 5, 5))
        assertEquals(date("2025-05-31"), ChineseLunarCalendar.toGregorian(2025, 5, 5))
        assertEquals(date("2026-06-19"), ChineseLunarCalendar.toGregorian(2026, 5, 5))
        assertEquals(date("2024-08-10"), ChineseLunarCalendar.toGregorian(2024, 7, 7))
        assertEquals(date("2025-08-29"), ChineseLunarCalendar.toGregorian(2025, 7, 7))
        assertEquals(date("2023-09-29"), ChineseLunarCalendar.toGregorian(2023, 8, 15))
        assertEquals(date("2024-09-17"), ChineseLunarCalendar.toGregorian(2024, 8, 15))
        assertEquals(date("2025-10-06"), ChineseLunarCalendar.toGregorian(2025, 8, 15))
        assertEquals(date("2026-09-25"), ChineseLunarCalendar.toGregorian(2026, 8, 15))
        assertEquals(date("2024-10-11"), ChineseLunarCalendar.toGregorian(2024, 9, 9))
        assertEquals(date("2025-10-29"), ChineseLunarCalendar.toGregorian(2025, 9, 9))
    }

    @Test
    fun leapMonthsMatchKnownYears() {
        assertEquals(mapOf(2020 to 4, 2023 to 2, 2025 to 6, 2026 to 0, 2028 to 5, 2033 to 11), listOf(2020, 2023, 2025, 2026, 2028, 2033).associateWith { ChineseLunarCalendar.leapMonth(it) })
        // 2025 has a leap sixth month: the regular sixth month starts 2025-06-25, the leap one 2025-07-25.
        assertEquals(date("2025-06-25"), ChineseLunarCalendar.toGregorian(2025, 6, 1))
        assertEquals(date("2025-07-25"), ChineseLunarCalendar.toGregorian(2025, 6, 1, isLeapMonth = true))
        assertEquals(date("2025-08-23"), ChineseLunarCalendar.toGregorian(2025, 7, 1))
    }

    @Test
    fun missingDatesAreNull() {
        assertNull(ChineseLunarCalendar.toGregorian(2026, 6, 1, isLeapMonth = true))
        assertNull(ChineseLunarCalendar.toGregorian(1899, 1, 1))
        assertNull(ChineseLunarCalendar.toGregorian(2101, 1, 1))
        assertNull(ChineseLunarCalendar.toGregorian(2026, 13, 1))
        val shortMonth = (1..12).first { ChineseLunarCalendar.monthDays(2026, it) == 29 }
        assertNull(ChineseLunarCalendar.toGregorian(2026, shortMonth, 30))
    }

    @Test
    fun everyYearIsConsistent() {
        for (year in ChineseLunarCalendar.MIN_YEAR until ChineseLunarCalendar.MAX_YEAR) {
            val days = ChineseLunarCalendar.yearDays(year)
            assertTrue("year $year has $days days", days in 353..385)
            val start = ChineseLunarCalendar.toGregorian(year, 1, 1)!!
            val next = ChineseLunarCalendar.toGregorian(year + 1, 1, 1)!!
            assertEquals("year $year", days.toLong(), next.toEpochDay() - start.toEpochDay())
            // Lunar new year always falls between January 21 and February 20.
            assertTrue("new year $year on $start", start.monthValue == 1 && start.dayOfMonth >= 21 || start.monthValue == 2 && start.dayOfMonth <= 20)
        }
    }

}
