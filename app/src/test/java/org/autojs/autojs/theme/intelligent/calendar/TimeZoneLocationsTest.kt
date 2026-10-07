package org.autojs.autojs.theme.intelligent.calendar

import org.autojs.autojs.theme.intelligent.Hemisphere
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TimeZoneLocationsTest {

    private val january = Instant.parse("2026-01-15T00:00:00Z")

    @Test
    fun knownZonesUseTheirCity() {
        val shanghai = TimeZoneLocations.infer(ZoneId.of("Asia/Shanghai"), january)
        assertFalse(shanghai.isApproximate)
        assertEquals(31.23, shanghai.location.latitude, 0.01)
        assertEquals(121.47, shanghai.location.longitude, 0.01)
        assertEquals(Hemisphere.SOUTH, TimeZoneLocations.infer(ZoneId.of("Australia/Sydney"), january).location.hemisphere)
        assertEquals(Hemisphere.NORTH, TimeZoneLocations.infer(ZoneId.of("Europe/London"), january).location.hemisphere)
    }

    @Test
    fun otherZonesAreEstimatedFromTheStandardOffset() {
        val plusEight = TimeZoneLocations.infer(ZoneId.of("Etc/GMT-8"), january)
        assertTrue(plusEight.isApproximate)
        assertEquals(120.0, plusEight.location.longitude, 0.001)
        assertEquals(Hemisphere.NORTH, plusEight.location.hemisphere)
        // Lord Howe is not in the table but lies in an Australian zone.
        val lordHowe = TimeZoneLocations.infer(ZoneId.of("Australia/Lord_Howe"), january)
        assertTrue(lordHowe.isApproximate)
        assertEquals(Hemisphere.SOUTH, lordHowe.location.hemisphere)
    }

    @Test
    fun everyTableEntryIsAValidZoneAndPlace() {
        val zones = ZoneId.getAvailableZoneIds()
        listOf("Asia/Shanghai", "Asia/Calcutta", "Asia/Saigon", "Europe/Kiev", "America/Buenos_Aires").forEach { id ->
            assertTrue("$id should be known", TimeZoneLocations.locationOf(id) != null)
        }
        zones.mapNotNull { id -> TimeZoneLocations.locationOf(id)?.let { id to it } }.forEach { (id, location) ->
            assertTrue(id, location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0)
        }
    }

}
