package org.autojs.autojs.theme.intelligent.calendar

import org.autojs.autojs.theme.intelligent.calendar.SolarCalculator.Day
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

class SolarCalculatorTest {

    private fun normal(date: String, latitude: Double, longitude: Double): Day.Normal {
        return SolarCalculator.dayOf(LocalDate.parse(date), latitude, longitude) as Day.Normal
    }

    private fun assertLocalTime(expected: String, actual: java.time.Instant, zone: String) {
        val local = actual.atZone(ZoneId.of(zone)).toLocalTime()
        val expectedMinutes = expected.substringBefore(':').toInt() * 60 + expected.substringAfter(':').toInt()
        val actualMinutes = local.hour * 60 + local.minute + local.second / 60.0
        assertTrue("expected about $expected in $zone but was $local", abs(actualMinutes - expectedMinutes) <= 2.0)
    }

    @Test
    fun julianDayMatchesTheJ2000Epoch() {
        assertEquals(2451544.5, SolarCalculator.julianDay(LocalDate.of(2000, 1, 1)), 0.0)
    }

    @Test
    fun publishedTimesAreMetWithinTwoMinutes() {
        // Equator at the March equinox: solar noon about 12:07.6 UTC, half a day of 90.833 degrees.
        normal("2026-03-20", 0.0, 0.0).let {
            assertLocalTime("06:04", it.sunrise, "UTC")
            assertLocalTime("18:11", it.sunset, "UTC")
        }
        normal("2026-06-21", 39.904, 116.407).let {
            assertLocalTime("04:46", it.sunrise, "Asia/Shanghai")
            assertLocalTime("19:46", it.sunset, "Asia/Shanghai")
        }
        normal("2026-06-21", 51.507, -0.128).let {
            assertLocalTime("04:43", it.sunrise, "Europe/London")
            assertLocalTime("21:21", it.sunset, "Europe/London")
        }
        normal("2026-12-21", 40.713, -74.006).let {
            assertLocalTime("07:16", it.sunrise, "America/New_York")
            assertLocalTime("16:32", it.sunset, "America/New_York")
        }
        normal("2026-12-21", -33.869, 151.209).let {
            assertLocalTime("05:41", it.sunrise, "Australia/Sydney")
            assertLocalTime("20:05", it.sunset, "Australia/Sydney")
        }
    }

    @Test
    fun resultsBelongToTheLocalDay() {
        // In Beijing the sunrise is the previous evening in UTC; it must still precede the local sunset of the same day.
        val day = normal("2026-01-15", 39.904, 116.407)
        assertTrue(day.sunrise.isBefore(day.sunset))
        assertEquals(LocalDate.parse("2026-01-15"), day.sunrise.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate())
        assertEquals(LocalDate.parse("2026-01-15"), day.sunset.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate())
    }

    @Test
    fun polarDaysAndNightsAreReported() {
        assertEquals(Day.PolarNight, SolarCalculator.dayOf(LocalDate.parse("2026-12-21"), 69.65, 18.96))
        assertEquals(Day.PolarDay, SolarCalculator.dayOf(LocalDate.parse("2026-06-21"), 69.65, 18.96))
        assertEquals(Day.PolarDay, SolarCalculator.dayOf(LocalDate.parse("2026-12-21"), -75.0, 0.0))
        assertEquals(Day.PolarNight, SolarCalculator.dayOf(LocalDate.parse("2026-06-21"), -75.0, 0.0))
    }

    @Test
    fun agreesWithTheAlmanacAlgorithm() {
        val places = listOf(
            0.0 to 0.0, 1.29 to 103.85, 22.28 to 114.15, 31.23 to 121.47, 39.90 to 116.41, 35.69 to 139.69, 51.51 to -0.13,
            55.76 to 37.62, 40.71 to -74.01, 34.05 to -118.24, -33.87 to 151.21, -23.55 to -46.63, -34.60 to -58.38, 59.91 to 10.75,
        )
        var worst = 0.0
        for ((latitude, longitude) in places) {
            for (month in 1..12) {
                val date = LocalDate.of(2026, month, 15)
                val day = SolarCalculator.dayOf(date, latitude, longitude) as Day.Normal
                val midnight = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                for ((rise, instant) in listOf(true to day.sunrise, false to day.sunset)) {
                    val minutes = (instant.toEpochMilli() - midnight) / 60_000.0
                    val reference = almanacMinutes(date, latitude, longitude, rise)
                    val difference = ((minutes - reference) % 1440 + 1440 + 720) % 1440 - 720
                    worst = maxOf(worst, abs(difference))
                }
            }
        }
        assertTrue("worst difference $worst minutes", worst <= 3.0)
    }

    /** "Almanac for Computers" (1990) sunrise / sunset, an independent approximation, in UTC minutes of [date]. */
    private fun almanacMinutes(date: LocalDate, latitude: Double, longitude: Double, rise: Boolean): Double {
        fun sinD(x: Double) = sin(Math.toRadians(x))
        fun cosD(x: Double) = cos(Math.toRadians(x))
        fun norm(x: Double, range: Double) = ((x % range) + range) % range
        val lngHour = longitude / 15
        val t = date.dayOfYear + ((if (rise) 6.0 else 18.0) - lngHour) / 24
        val m = 0.9856 * t - 3.289
        val l = norm(m + 1.916 * sinD(m) + 0.020 * sinD(2 * m) + 282.634, 360.0)
        var ra = norm(Math.toDegrees(atan(0.91764 * tan(Math.toRadians(l)))), 360.0)
        ra = (ra + floor(l / 90) * 90 - floor(ra / 90) * 90) / 15
        val sinDec = 0.39782 * sinD(l)
        val cosDec = cos(asin(sinDec))
        val cosH = (cosD(90.833) - sinDec * sinD(latitude)) / (cosDec * cosD(latitude))
        val h = (if (rise) 360 - Math.toDegrees(acos(cosH)) else Math.toDegrees(acos(cosH))) / 15
        val localMeanTime = h + ra - 0.06571 * t - 6.622
        return norm(localMeanTime - lngHour, 24.0) * 60
    }

}
