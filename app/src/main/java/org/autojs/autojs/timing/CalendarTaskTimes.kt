package org.autojs.autojs.timing

import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.LocalDate
import org.joda.time.LocalTime

/** Calendar recurrence follows local dates, including 23/25-hour days. */
internal object CalendarTaskTimes {
    @JvmStatic fun atLocalTime(date:LocalDate,millisOfDay:Long,zone:DateTimeZone):Long {
        val local=date.toLocalDateTime(LocalTime.fromMillisOfDay(millisOfDay)).toDateTime(DateTimeZone.UTC).millis
        // Shift a nonexistent wall time across the gap; use the earlier occurrence in an overlap.
        return DateTime(zone.convertLocalToUTC(local,false),zone).withEarlierOffsetAtOverlap().millis
    }
    @JvmStatic fun nextWeekday(millisOfDay:Long,days:Long,now:DateTime):Long {
        for(offset in 0..7) {
            val date=now.toLocalDate().plusDays(offset)
            if(days and (1L shl (date.dayOfWeek%7))==0L)continue
            val candidate=atLocalTime(date,millisOfDay,now.zone)
            if(candidate>=now.millis)return candidate
        }
        return -1
    }
}
