package org.autojs.autojs.theme.intelligent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId

class StateResolverTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val sydney = ZoneId.of("Australia/Sydney")

    private fun at(text: String, zone: ZoneId = shanghai) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun resolve(source: StateSource, now: Long, zone: ZoneId = shanghai, facts: DeviceFacts = DeviceFacts(), previous: String? = null) =
        StateResolver.resolve(source, now, zone, facts, previous)

    @Test
    fun timePeriodsWrapPastMidnight() {
        val source = StateSource.TimePeriods(listOf(6 * 60, 12 * 60, 18 * 60))
        assertEquals(StateResolution("period:2", at("2026-10-07T06:00")), resolve(source, at("2026-10-07T05:00")))
        assertEquals(StateResolution("period:0", at("2026-10-07T12:00")), resolve(source, at("2026-10-07T06:00")))
        assertEquals(StateResolution("period:1", at("2026-10-07T18:00")), resolve(source, at("2026-10-07T12:00")))
        assertEquals(StateResolution("period:2", at("2026-10-08T06:00")), resolve(source, at("2026-10-07T23:00")))
    }

    @Test
    fun sunriseAndSunsetDecideDayAndNight() {
        val source = StateSource.SunriseSunset(GeoLocation(39.904, 116.407))
        val noon = resolve(source, at("2026-06-21T12:00"))
        assertEquals(StateKeys.DAY, noon.stateKey)
        // The next change is the evening sunset, about 19:46 in Beijing.
        assertEquals(19, LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(noon.nextChangeAt!!), shanghai).hour)
        val early = resolve(source, at("2026-06-21T03:00"))
        assertEquals(StateKeys.NIGHT, early.stateKey)
        assertEquals(4, LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(early.nextChangeAt!!), shanghai).hour)
        // In the polar night it stays night and checks again at midnight.
        val polar = resolve(StateSource.SunriseSunset(GeoLocation(69.65, 18.96)), at("2026-12-21T12:00"))
        assertEquals(StateResolution(StateKeys.NIGHT, at("2026-12-22T00:00")), polar)
        // Without a location, the time zone decides (Shanghai at noon is daytime).
        assertEquals(StateKeys.DAY, resolve(StateSource.SunriseSunset(), at("2026-10-07T12:00")).stateKey)
    }

    @Test
    fun weekdaysChangeAtMidnight() {
        // 2026-10-07 is a Wednesday, 2026-10-10 a Saturday.
        assertEquals(StateResolution("weekday:3", at("2026-10-08T00:00")), resolve(StateSource.Weekday(), at("2026-10-07T09:00")))
        assertEquals(StateKeys.WORKDAY, resolve(StateSource.Weekday(workdaysOnly = true), at("2026-10-07T09:00")).stateKey)
        assertEquals(StateKeys.WEEKEND, resolve(StateSource.Weekday(workdaysOnly = true), at("2026-10-10T09:00")).stateKey)
    }

    @Test
    fun seasonsFollowTheHemisphere() {
        assertEquals(StateResolution("season:autumn", at("2026-11-01T00:00")), resolve(StateSource.Seasons(), at("2026-10-07T09:00")))
        assertEquals("season:spring", resolve(StateSource.Seasons(), at("2026-10-07T09:00", sydney), sydney).stateKey)
        assertEquals("season:winter", resolve(StateSource.Seasons(Hemisphere.NORTH), at("2026-01-15T09:00", sydney), sydney).stateKey)
        assertEquals(Season.WINTER, StateResolver.seasonOf(Month.DECEMBER, Hemisphere.NORTH))
        assertEquals(Season.SUMMER, StateResolver.seasonOf(Month.DECEMBER, Hemisphere.SOUTH))
        assertEquals(Season.SPRING, StateResolver.seasonOf(Month.MARCH, Hemisphere.NORTH))
        assertEquals(Season.AUTUMN, StateResolver.seasonOf(Month.MARCH, Hemisphere.SOUTH))
    }

    @Test
    fun batteryRangesHaveHysteresis() {
        val thresholds = listOf(20, 80)
        assertEquals(1, StateResolver.batteryIndex(thresholds, 50, null))
        assertEquals(0, StateResolver.batteryIndex(thresholds, 19, null))
        assertEquals(2, StateResolver.batteryIndex(thresholds, 80, null))
        // Rising out of range 0 needs 22; falling out of range 1 needs 17.
        assertEquals(0, StateResolver.batteryIndex(thresholds, 21, "battery:0"))
        assertEquals(1, StateResolver.batteryIndex(thresholds, 22, "battery:0"))
        assertEquals(1, StateResolver.batteryIndex(thresholds, 18, "battery:1"))
        assertEquals(0, StateResolver.batteryIndex(thresholds, 17, "battery:1"))
        // A big jump goes straight to the new range.
        assertEquals(2, StateResolver.batteryIndex(thresholds, 95, "battery:0"))
        // Unknown levels keep the previous range, or assume a full battery; foreign keys are ignored.
        assertEquals(1, StateResolver.batteryIndex(thresholds, null, "battery:1"))
        assertEquals(2, StateResolver.batteryIndex(thresholds, null, null))
        assertEquals(1, StateResolver.batteryIndex(thresholds, 50, "battery:9"))
        assertEquals(StateResolution("battery:0", null), resolve(StateSource.Battery(thresholds), at("2026-10-07T09:00"), facts = DeviceFacts(batteryLevel = 5)))
    }

    @Test
    fun deviceSourcesAreOnOrOff() {
        val facts = DeviceFacts(isNight = true, charging = true, powerSave = false, accessibilityServiceRunning = true, scriptRunning = false)
        val now = at("2026-10-07T09:00")
        assertEquals(StateKeys.NIGHT, resolve(StateSource.NightMode, now, facts = facts).stateKey)
        assertEquals(StateKeys.ON, resolve(StateSource.Charging, now, facts = facts).stateKey)
        assertEquals(StateKeys.OFF, resolve(StateSource.PowerSave, now, facts = facts).stateKey)
        assertEquals(StateKeys.ON, resolve(StateSource.AccessibilityService, now, facts = facts).stateKey)
        assertEquals(StateKeys.OFF, resolve(StateSource.ScriptRunning, now, facts = facts).stateKey)
        assertNull(resolve(StateSource.Charging, now, facts = facts).nextChangeAt)
        assertEquals(StateResolution(StateKeys.DEFAULT, null), resolve(StateSource.None, now))
    }

}
