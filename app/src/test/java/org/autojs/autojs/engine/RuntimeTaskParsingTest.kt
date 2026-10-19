package org.autojs.autojs.engine

import org.joda.time.DateTime
import org.junit.Assert.*
import org.junit.Test

class RuntimeTaskParsingTest {
    @Test fun weekdaysUseSundayZeroAndDeduplicateAliases() {
        assertEquals(3L,RuntimeTasksBridge.weekdays(listOf("Sunday",7,"mon",1)))
        assertThrows(IllegalArgumentException::class.java){RuntimeTasksBridge.weekdays(emptyList())}
        assertThrows(IllegalArgumentException::class.java){RuntimeTasksBridge.weekdays(listOf(1.5))}
        assertThrows(IllegalArgumentException::class.java){RuntimeTasksBridge.weekdays(listOf("sunflower"))}
    }
    @Test fun timestampsWithExplicitOffsetsPreserveTheirInstant() {
        val input="2026-10-19T12:34:56+03:00"
        assertEquals(DateTime.parse(input).millis,RuntimeTasksBridge.dateTime(input).toDateTime().millis)
        assertThrows(IllegalArgumentException::class.java){RuntimeTasksBridge.time("25:00")}
    }
}
