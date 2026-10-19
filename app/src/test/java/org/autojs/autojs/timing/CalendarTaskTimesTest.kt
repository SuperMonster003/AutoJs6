package org.autojs.autojs.timing

import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.LocalDate
import org.joda.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarTaskTimesTest {
    private val zone=DateTimeZone.forID("Europe/Berlin")
    private fun now(value:String)=DateTime.parse(value).withZone(zone)
    private fun millis(value:String)=LocalTime.parse(value).millisOfDay.toLong()
    @Test fun dailyAndWeeklyKeepWallTimeAcrossShortAndLongDays() {
        for((start,expected) in listOf("2024-03-30T10:00:00+01:00" to "2024-03-31T09:00:00+02:00","2024-10-26T10:00:00+02:00" to "2024-10-27T09:00:00+01:00"))
            for(days in listOf(127L,1L))assertEquals(DateTime.parse(expected).millis,CalendarTaskTimes.nextWeekday(millis("09:00"),days,now(start)))
    }
    @Test fun missingTimesShiftForwardAndRepeatedTimesUseTheEarlierOccurrence() {
        assertEquals(DateTime.parse("2024-03-31T03:30:00+02:00").millis,CalendarTaskTimes.atLocalTime(LocalDate(2024,3,31),millis("02:30"),zone))
        assertEquals(DateTime.parse("2024-10-27T02:30:00+02:00").millis,CalendarTaskTimes.atLocalTime(LocalDate(2024,10,27),millis("02:30"),zone))
        assertEquals(DateTime.parse("2024-10-28T02:30:00+01:00").millis,CalendarTaskTimes.nextWeekday(millis("02:30"),127,now("2024-10-27T02:45:00+02:00")))
    }
    @Test fun emptyWeekdayFlagsDoNotScheduleAndExactCurrentTimeIsValid() {
        val time=now("2024-10-21T09:00:00+02:00")
        assertEquals(-1,CalendarTaskTimes.nextWeekday(millis("09:00"),0,time))
        assertEquals(time.millis,CalendarTaskTimes.nextWeekday(millis("09:00"),2,time))
    }
}
